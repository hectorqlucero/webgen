(ns {{sanitize}}.routes.fk-api
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [{{sanitize}}.engine.config :as config]
   [{{sanitize}}.engine.crud :as crud]
   [{{sanitize}}.models.crud :as model-crud]
   [{{sanitize}}.models.util :refer [json-response]]
   [clojure.string :as str]
   [clojure.data.json :as json]
   [{{sanitize}}.engine.render :as render]
   [hiccup2.core :refer [html]]))

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
  [entity parent-field fk-fields fk-config]
  (let [sort-by (or (:fk-sort fk-config) (first fk-fields))
        parent-field-kw (keyword parent-field)
        fields-str (str/join ", " (map name fk-fields))
        order-str (str/join ", " (map name (if (sequential? sort-by) sort-by [sort-by])))
        sql (str "SELECT id, " fields-str
                 " FROM " (name entity)
                 " WHERE " (name parent-field-kw) " = ?"
                 " ORDER BY " order-str)]
    sql))

(defn- format-fk-options
  "Format FK options with labels."
  [rows fk-fields separator]
  (let [fk-fields (and (seq fk-fields) fk-fields)
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
(defn get-fk-options
  "Returns FK options, optionally filtered by parent field value."
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)
        [parent-field parent-value] (parse-parent-param params)
        fk-fields-param (or (get params "fk-fields") (get params :fk-fields))]
    (if entity
      (try
        (let [fk-config (config/get-entity-config entity)
              fk-fields (if fk-fields-param
                          (map keyword (str/split fk-fields-param #","))
                          (:fk-field fk-config))
              separator (or (:fk-separator fk-config) " — ")
              has-parent? (and parent-field parent-value)
              sql (if has-parent?
                    (build-fk-sql entity parent-field fk-fields fk-config)
                    (let [fields-str (str/join ", " (map name fk-fields))
                          sort-by (or (:fk-sort fk-config) (first fk-fields))
                          order-str (str/join ", " (map name (if (sequential? sort-by) sort-by [sort-by])))]
                      (str "SELECT id, " fields-str " FROM " (name entity) " ORDER BY " order-str)))
              rows (if has-parent?
                     (model-crud/Query model-crud/db [sql (Integer/parseInt parent-value)])
                     (model-crud/Query model-crud/db [sql]))]
          (if (seq rows)
            (json-response {:ok true :options (format-fk-options rows fk-fields separator)})
            (json-response {:ok true :options []})))
        (catch Exception e
          (json-response {:ok false :error (.getMessage e) :options []})))
      (json-response {:ok false :error "Missing required params" :options []}))))

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
    (json-response {:ok false :errors (:errors result)})

    (and (map? result) (:success result))
    ;; success map; determine new-id from known places
    (let [new-id (or (when (number? (:success result)) (:success result))
                     (get-in result [:data :id]))
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
        data (parse-data-param params)]

    (if (and entity data)
      (try
        (let [data-kw (into {} (map (fn [[k v]] [k v]) data))
              entity-config (config/get-entity-config entity)
              errors (validate-fk-data data-kw entity-config)]

          (if (seq errors)
            (json-response {:ok false :errors errors})
            (let [result (crud/save-record entity data-kw {})]
              (handle-fk-save-result result entity-config data-kw))))
        (catch Exception e
          (json-response {:ok false :error (.getMessage e)})))
      (json-response {:ok false :error "Missing required params"}))))

(defn get-fk-modal-config
  "Returns entity configuration for modal form.
   Includes both a lightweight `form-fields` vector (id,label,type,required?,placeholder)
   and a rendered HTML string (`form-html`) so the client can choose how to build the
   modal.  Using server-side rendering keeps input types, options, and FK selects
   in sync with the normal form logic."
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)]

    (if entity
      (try
        (let [entity-config (config/get-entity-config entity)
              fields (config/get-form-fields entity)
              form-fields (map #(select-keys % [:id :label :type :required? :placeholder
                                                :options :fk :fk-field :fk-parent])
                               fields)
              rendered (let [render-fn #'{{sanitize}}.engine.render/render-field]
                         ;; Render each field separately and concatenate HTML to avoid fragment artifacts.
                         (->> fields
                              (map #(str (html (render-fn % {}))))
                              (apply str)))]
          (json-response {:ok true
                          :entity entity
                          :title (:title entity-config)
                          :form-fields form-fields
                          :form-html rendered}))
        (catch Exception e
          (json-response {:ok false :error (.getMessage e)})))
      (json-response {:ok false :error "Missing entity parameter"}))))

(defroutes fk-api-routes
  (GET "/api/fk-options" request (get-fk-options request))
  (POST "/api/fk-create" request (create-fk-record request))
  (GET "/api/fk-modal-config" request (get-fk-modal-config request)))
