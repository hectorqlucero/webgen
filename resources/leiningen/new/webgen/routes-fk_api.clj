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

(defn- parse-fk-fields-param
  "Parse fk-fields parameter - can be comma/space separated list or array.
   Returns a vector of keywords."
  [params]
  (when-let [fk-fields (or (get params "fk-fields") (get params :fk-fields))]
    (cond
      (string? fk-fields)
      ;; Parse comma/space-separated string into keywords
      (let [trimmed (.trim fk-fields)]
        (if (empty? trimmed)
          nil
          (mapv keyword (clojure.string/split trimmed #"[,\s]+"))))
      (vector? fk-fields)
      ;; Already a vector, ensure keywords
      (mapv (fn [f] (if (keyword? f) f (keyword f))) fk-fields)
      (coll? fk-fields)
      ;; Other collection type, convert to vector of keywords
      (mapv (fn [f] (if (keyword? f) f (keyword f))) fk-fields)
      :else nil)))

(defn- build-fk-sql
  "Build SQL query for FK options.
   Handles both parent-field filtering (dependent selects) and :fk-filter (config-based filtering).
   fk-fields-to-select parameter allows overriding the fields from request params."
  [entity parent-field fk-fields-to-select fk-config]
  (let [fk-fields (or fk-fields-to-select
                      (:fk-field fk-config)
                      [:nombre])
        sort-by (or (:fk-sort fk-config) [:nombre])
        separator (or (:fk-separator fk-config) " — ")
        fields-str (str/join ", " (map name fk-fields))
        order-str (str/join ", " (map name (if (sequential? sort-by) sort-by [sort-by])))

        ;; Build WHERE clauses for both parent-field and :fk-filter
        ;; Order matters: parent-field first, then fk-filter
        parent-where (when parent-field
                       (str (name (keyword parent-field)) " = ?"))
        fk-filter (:fk-filter fk-config)
        filter-where (when fk-filter
                       (str (name (first fk-filter)) " = ?"))

        ;; Combine WHERE clauses with AND; preserve order
        where-parts (cond-> []
                      parent-where (conj parent-where)
                      filter-where (conj filter-where))
        where-clause (when (seq where-parts)
                       (str " WHERE " (str/join " AND " where-parts)))]

    (str "SELECT id, " fields-str
         " FROM " (name entity)
         where-clause
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
(defn get-fk-options
  "Returns FK options.
   - If parent-field and parent-value provided: returns filtered options (dependent select)
   - If parent-field/value missing: returns all records from FK entity (direct FK field)
   - Always applies :fk-filter if configured in the entity
   - Uses fk-fields from request param if provided, otherwise falls back to entity config"
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)
        [parent-field parent-value] (parse-parent-param params)]

    (if entity
      (try
        (let [fk-config (config/get-entity-config entity)
              ;; Priority: request param > entity config > default
              fk-fields (or (parse-fk-fields-param params)
                            (:fk-field fk-config)
                            [:nombre])
              separator (or (:fk-separator fk-config) " — ")
              sql (build-fk-sql entity parent-field fk-fields fk-config)

              ;; Build query params in same order as WHERE clause: parent-value first, then fk-filter value
              fk-filter (:fk-filter fk-config)
              param-values (cond-> []
                             (and parent-field parent-value)
                             (conj (Integer/parseInt parent-value))
                             fk-filter
                             (conj (second fk-filter)))

              query-params (if (seq param-values)
                             (into [sql] param-values)
                             [sql])
              rows (model-crud/Query model-crud/db query-params)]
          (if rows
            (json-response {:ok true :options (format-fk-options rows fk-fields separator)})
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
          (println "[ERROR] create-fk-record:" (.getMessage e))
          (.printStackTrace e)
          (json-response {:ok false :error (.getMessage e)})))
      (json-response {:ok false :error "Missing required params"}))))

(defn get-fk-modal-config
  "Returns entity configuration for modal form.
   Includes both a lightweight `form-fields` vector (id,label,type,required?,placeholder)
   and a rendered HTML string (`form-html`) so the client can choose how to build the
   modal.  Using server‑side rendering keeps input types, options, and FK selects
   in sync with the normal form logic.
   Excludes fields marked as :grid-only? or :hidden-in-form?"
  [request]
  (let [params (:params request)
        entity (parse-entity-param params)]

    (if entity
      (try
        (let [entity-config (config/get-entity-config entity)
              ;; Exclude grid-only and hidden-in-form fields
              ;; Include all FK fields (dependent selects will filter by parent on client)
              fields (remove (fn [f]
                               (or
                                ;; Exclude grid-only fields
                                (:grid-only? f)
                                ;; Exclude hidden-in-form fields
                                (:hidden-in-form? f)))
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
