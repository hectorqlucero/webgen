(ns {{sanitized}}.routes.fk-api
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.crud :as crud]
   [{{sanitized}}.models.crud :as model-crud]
   [{{sanitized}}.models.util :refer [json-response]]
   [{{sanitized}}.i18n.core :as i18n]
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

(defn- parse-lang-param
  "Parse language parameter from request, default to :es"
  [params]
  (let [lang-str (or (get params "lang") (get params :lang) "es")]
    (keyword lang-str)))

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

(defn- parse-fk-fields-param
  "Parse fk-fields parameter from request. Returns a vector of keywords."
  [params]
  (when-let [fk-fields-str (or (get params "fk-fields") (get params :fk-fields))]
    (if (string? fk-fields-str)
      (mapv keyword (str/split fk-fields-str #","))
      (if (sequential? fk-fields-str)
        (mapv keyword fk-fields-str)
        [(keyword fk-fields-str)]))))

(defn- build-fk-sql
  "Build SQL query for FK options. If parent-field is nil, returns all records.
   If sort-by is not provided, defaults to sorting by the first fk-field."
  [entity parent-field fk-fields sort-by]
  (when-not fk-fields
    (throw (Exception. (str "fk-fields is required for entity " (name entity)))))
  (let [fk-fields fk-fields
        sort-by (or sort-by (when (seq fk-fields) [(first fk-fields)]))
        fields-str (str/join ", " (map name fk-fields))
        order-str (when sort-by (str/join ", " (map name (if (sequential? sort-by) sort-by [sort-by]))))]
    (if parent-field
      (let [parent-field-kw (keyword parent-field)]
        (str "SELECT id, " fields-str
             " FROM " (name entity)
             " WHERE " (name parent-field-kw) " = ?"
             (when order-str (str " ORDER BY " order-str))))
      ;; No parent field - return all records
      (str "SELECT id, " fields-str
           " FROM " (name entity)
           (when order-str (str " ORDER BY " order-str))))))

(defn- format-fk-options
  "Format FK options with labels."
  [rows fk-fields separator locale]
  (when-not fk-fields
    (throw (Exception. "fk-fields is required for formatting options")))
  (let [fk-fields fk-fields
        label-fn (fn [row]
                   (->> fk-fields
                        (map #(str (get row % "")))
                        (str/join separator)))
        select-label (i18n/tr locale :common/select)]
    (cons {:value "" :label (str "-- " select-label " --")}
          (map (fn [row]
                 {:value (str (:id row))
                  :label (label-fn row)})
               rows))))

;; === Main Functions ===
(defn get-fk-options
  "Returns FK options based on parent field value (if present) or all options.
   Respects fk-fields parameter from client if provided, otherwise uses config."
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)
        [parent-field parent-value] (parse-parent-param params)
        request-fk-fields (parse-fk-fields-param params)
        lang (parse-lang-param params)]

    (if entity
      (try
        (let [fk-config (config/get-entity-config entity)
              fk-fields (or request-fk-fields (:fk-field fk-config))
              separator (or (:fk-separator fk-config) " — ")
              sort-by (:fk-sort fk-config)
              sql (build-fk-sql entity parent-field fk-fields sort-by)
              query-params (if (and parent-field parent-value)
                             [(Integer/parseInt parent-value)]
                             [])]
          (if-let [rows (if (seq query-params)
                          (model-crud/Query model-crud/db (into [sql] query-params))
                          (model-crud/Query model-crud/db [sql]))]
            (json-response {:ok true :options (format-fk-options rows fk-fields separator lang)})
            (json-response {:ok false :error "Database query failed" :options []})))
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
    (json-response {:ok false :errors (:errors result)})

    (and (map? result) (:success result))
    ;; success map; determine new-id from known places
    (let [new-id (or (when (number? (:success result)) (:success result))
                     (get-in result [:data :id]))
          new-label (get data-kw (first (:fk-field entity-config)))]
      (json-response {:ok true :new-id new-id :new-label new-label}))

    (map? result)
    ;; unknown map form
    (json-response {:ok false :error (str result)})

    :else
    ;; result is not a map; fall back to previous logic
    (let [new-id (if (number? result) result (first result))
          new-label (get data-kw (first (:fk-field entity-config)))]
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
              ;; exclude grid-only fields and computed fk? fields (except parent fields)
              fields (remove (fn [f] (or (and (:fk? f) (not= (:id f) (:fk-parent params)))
                                         (:grid-only? f)))
                             (:fields entity-config))
              form-fields (map #(select-keys % [:id :label :type :required? :placeholder
                                                :options :fk :fk-field :fk-parent])
                               fields)
              ;; render the fields using the same server-side helper; pass empty row
              ;; we reference the private var via var literal to avoid visibility errors
              rendered (let [render-fn #'{{sanitized}}.engine.render/render-field]
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
          (json-response {:ok false :error (.getMessage e)}))
        (finally
          (println "[DEBUG] get-fk-modal-config completed")))
      (json-response {:ok false :error "Missing entity parameter"}))))

(defroutes fk-api-routes
  (GET "/api/fk-options" request (get-fk-options request))
  (POST "/api/fk-create" request (create-fk-record request))
  (GET "/api/fk-modal-config" request (get-fk-modal-config request)))
