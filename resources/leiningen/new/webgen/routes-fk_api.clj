(ns {{sanitized}}.routes.fk-api
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.crud :as crud]
   [{{sanitized}}.models.crud :as model-crud]
   [{{sanitized}}.models.util :refer [json-response]]
   [clojure.string :as str]
   [clojure.data.json :as json]
   [{{sanitized}}.engine.render :as render]
   [hiccup.core :refer [html]]))

;; === Helper Functions ===
(defn- parse-entity-param
  "Parse entity parameter from request"
  [params]
  (when-let [entity-str (or (get params "entity") (get params :entity))]
    (keyword entity-str)))

(defn- parse-parent-param
  "Parse parent field and value parameters; support string or keyword keys."
  [params]
  (let [pf (or (get params "parent-field") (get params :parent-field))
        pv (or (get params "parent-value") (get params :parent-value))]
    (when (and pf pv)
      [pf pv])))

(defn- parse-data-param
  "Parse data parameter from request"
  [params]
  (when-let [data-json (or (get params "data") (get params :data))]
    (if (string? data-json)
      (json/read-str data-json :key-fn keyword)
      data-json)))

(defn- build-fk-sql
  "Build SQL query for FK options"
  [entity parent-field fk-config]
  (let [fk-fields (or (:fk-field fk-config) [:nombre])
        sort-by (or (:fk-sort fk-config) [:nombre])
        separator (or (:fk-separator fk-config) " — ")
        parent-field-kw (keyword parent-field)
        fields-str (str/join ", " (map name fk-fields))
        order-str (str/join ", " (map name (if (sequential? sort-by) sort-by [sort-by])))]
    (str "SELECT id, " fields-str
         " FROM " (name entity)
         " WHERE " (name parent-field-kw) " = ?"
         " ORDER BY " order-str)))

(defn- format-fk-options
  "Format FK options with labels. If fk-fields is nil or empty, default to [:nombre]."
  [rows fk-fields separator]
  (let [fk-fields (or (and (seq fk-fields) fk-fields) [:nombre])
        label-fn (fn [row]
                   (->> fk-fields
                        (map #(str (get row % "")))
                        (str/join separator)))]
    (cons {:value "" :label "-- Seleccionar --"}
          (map (fn [row]
                 {:value (str (:id row))
                  :label (label-fn row)})
               rows))))

;; === Main Functions ===
(defn- parse-fk-fields-param
  "Parse fk-fields parameter from request"
  [params]
  (when-let [fields-str (or (get params "fk-fields") (get params :fk-fields))]
    (if (string? fields-str)
      (map keyword (str/split fields-str #","))
      fields-str)))

(defn get-fk-options
  "Returns filtered FK options based on parent field value.
   If parent-field and parent-value are provided, filters by parent.
   Otherwise returns all options for the entity.
   Accepts fk-fields parameter to specify which fields to display (from calling field config)."
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)
        [parent-field parent-value] (parse-parent-param params)
        fk-fields-param (parse-fk-fields-param params)]

    (if entity
      (try
        (let [fk-config (config/get-entity-config entity)
              ;; Use provided fk-fields if available, otherwise fall back to entity config
              fk-fields (or fk-fields-param
                            (:fk-field fk-config)
                            [:nombre])
              separator (:fk-separator fk-config)]
          (if (and parent-field parent-value)
            ;; Filtered by parent (dependent select)
            (let [sql (build-fk-sql entity parent-field fk-config)]
              (if-let [rows (model-crud/Query model-crud/db [sql (Integer/parseInt parent-value)])]
                (json-response {:ok true :options (format-fk-options rows fk-fields separator)})
                (json-response {:ok false :error "Database query failed" :options []})))
            ;; No parent - return all options
            (let [order-str (str/join ", " (map name fk-fields))
                  sql (str "SELECT id, " (str/join ", " (map name fk-fields)) " FROM " (name entity) " ORDER BY " order-str)
                  rows (model-crud/Query model-crud/db [sql])]
              (json-response {:ok true :options (format-fk-options rows fk-fields (or separator " — "))}))))
        (catch Exception e
          (println "[ERROR] get-fk-options:" (.getMessage e))
          (json-response {:ok false :error (.getMessage e) :options []})))
      (json-response {:ok false :error "Missing entity parameter" :options []}))))

(defn validate-fk-data
  "Validates FK data against entity configuration."
  [data-kw entity-config]
  (reduce
   (fn [errs field]
     (let [field-id (:id field)
           field-label (:label field)]
       (if (and (:required? field) (not (get data-kw field-id)))
         (assoc errs field-id (str field-label " es requerido"))
         errs)))
   {}
   (:fields entity-config)))

(defn handle-fk-save-result
  "Handles the result of saving FK record.
   The `crud/save-record` helper may return a number (new id), a sequence
   (e.g. [id ...]), or a map with :success/:data.  We need to interpret all
   of these forms and convert them to the JSON payload that the client
   expects (#:ok true with :new-id and :new-label).

   Maps containing :errors are treated as validation failures; maps with a
   truthy :success value are considered successful saves.  Anything else is
   reported as an error so the client can display it for debugging."
  [result entity-config data-kw]
  (cond
    (and (map? result) (:errors result))
    (let [errors (if (map? (:errors result))
                   (:errors result)
                   (into {} (map (fn [e] [(:field e) (:message e)]) (:errors result))))]
      (json-response {:ok false :errors errors}))

    (and (map? result) (:success result))
    ;; success map; determine new-id from known places
    (let [success-val (:success result)
          data-id (get-in result [:data :id])
          new-id (cond
                   (number? success-val) success-val
                   (and data-id (not (empty? data-id))) data-id
                   :else nil)
          new-label (get data-kw
                         (first (or (:fk-field entity-config) [:nombre])))]
      (json-response {:ok true :new-id new-id :new-label new-label}))

    (map? result)
    ;; unknown map form
    (json-response {:ok false :error (str result)})

    :else
    ;; result is not a map; fall back to previous logic
    (let [new-id (if (number? result) result (first result))
          new-label (get data-kw (first (or (:fk-field entity-config) [:nombre])))]
      (json-response {:ok true :new-id new-id :new-label new-label}))))

(defn create-fk-record
  "Creates a new FK record via entity hooks."
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)
        data (parse-data-param params)
        user-id (get-in request [:session :user_id] :anonymous)]

    (if (and entity data)
      (try
        (let [data-kw (into {} (map (fn [[k v]] [k v]) data))
              entity-config (config/get-entity-config entity)
              result (if (:audit? entity-config)
                       (crud/save-with-audit entity data-kw user-id)
                       (crud/save-record entity data-kw {:user-id user-id}))]
          (handle-fk-save-result result entity-config data-kw))
        (catch Exception e
          (println "[ERROR] create-fk-record:" (.getMessage e))
          (.printStackTrace e)
          (json-response {:ok false :error (.getMessage e)})))
      (json-response {:ok false :error "Missing required params"}))))

(defn get-fk-modal-config
  "Returns entity configuration for modal form.
   Includes both a lightweight `form-fields` vector (id,label,type,required?,placeholder)
   and a rendered HTML string (`form-html`) so the client can choose how to build the
   modal.  Using server‑side rendering keeps input types, options, and FK selects
   in sync with the normal form logic."
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)]

    (if entity
      (try
        (let [entity-config (config/get-entity-config entity)
              ;; exclude fk? fields? we still include them so modal can render parent select
              ;; also exclude fields that should not appear in forms (same as get-form-fields)
              fields (remove (fn [f] (or (and (:fk? f) (not= (:id f) (:fk-parent params)))
                                         (:grid-only? f)
                                         (:hidden-in-form? f)
                                         (= (:type f) :computed)))
                             (:fields entity-config))
              form-fields (map #(select-keys % [:id :label :type :required? :placeholder
                                                :options :fk :fk-field :fk-parent])
                               fields)
              ;; render the fields using the same server-side helper; pass empty row
              ;; we reference the private var via var literal to avoid visibility errors
              rendered (let [render-fn #'{{sanitize}}.engine.render/render-field]
                         (->> fields
                              (map #(render-fn % {}))
                              (html)))]
          (json-response {:ok true
                          :entity entity
                          :title (:title entity-config)
                          :form-fields form-fields
                          :form-html rendered}))
        (catch Exception e
          (println "[ERROR] get-fk-modal-config:" (.getMessage e))
          (json-response {:ok false :error (.getMessage e)})))
      (json-response {:ok false :error "Missing entity parameter"}))))

(defroutes fk-api-routes
  (GET "/api/fk-options" request (get-fk-options request))
  (POST "/api/fk-create" request (create-fk-record request))
  (GET "/api/fk-modal-config" request (get-fk-modal-config request)))
