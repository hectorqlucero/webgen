(ns {{sanitized}}.engine.query
  "Query execution engine for parameter-driven entities.
   Handles query resolution, parameter substitution, and execution."
  (:require
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.models.crud :as crud]))

;; =============================================================================
;; Query Resolution
;; =============================================================================

(defn- resolve-query
  "Resolves a query definition to an executable form.
   Queries can be:
   - String: SQL query
   - Function: Called with params
   - Keyword: Reference to a function"
  [query-def]
  (cond
    (string? query-def) query-def
    (fn? query-def) query-def
    (keyword? query-def) (config/resolve-fn-ref query-def)
    :else (throw (ex-info "Invalid query definition" {:query query-def}))))

(defn get-query
  "Gets a query definition from an entity configuration."
  [entity query-key]
  (let [config (config/get-entity-config entity)
        query (get-in config [:queries query-key])]
    (when-not query
      (throw (ex-info "Query not defined for entity"
                      {:entity entity :query-key query-key})))
    (resolve-query query)))

;; =============================================================================
;; Query Execution
;; =============================================================================

(defn execute-query
  "Executes a query for an entity with given parameters.
   
   Options:
   - :conn - Database connection key (defaults to entity's connection or :default)
   - :params - Query parameters
   - :query-key - Which query to execute (:list, :get, :custom/name, etc.)"
  [entity {:keys [query-key params conn] :or {params [] conn nil}}]
  (let [config (config/get-entity-config entity)
        connection (or conn (:connection config) :default)
        query-def (get-in config [:queries query-key])
        query (resolve-query query-def)]
    
    (cond
      ;; SQL string query
      (string? query)
      (if (empty? params)
        (crud/Query query :conn connection)
        (crud/Query (into [query] params) :conn connection))
      
      ;; Function query
      (fn? query)
      (query params connection)
      
      :else
      (throw (ex-info "Unsupported query type"
                      {:entity entity :query-key query-key :query query})))))

;; =============================================================================
;; Common Query Shortcuts
;; =============================================================================

(defn list-records
  "Executes the :list query for an entity.
   Returns all records according to the entity's list query."
  [entity & [opts]]
  (execute-query entity (merge {:query-key :list} opts)))

(defn get-record
  "Executes the :get query for an entity with an ID.
   Returns a single record."
  [entity id & [opts]]
  (first
   (execute-query entity
                  (merge {:query-key :get :params [id]} opts))))

(defn custom-query
  "Executes a custom named query from the entity configuration.
   Example: (custom-query :users :active-users)"
  [entity query-name & [params opts]]
  (execute-query entity
                 (merge {:query-key query-name
                         :params (or params [])}
                        opts)))

;; =============================================================================
;; Auto-Generated Queries
;; =============================================================================

(defn generate-list-query
  "Generates a default list query for an entity based on its table."
  [entity]
  (let [config (config/get-entity-config entity)
        table (:table config)
        fields (config/get-display-fields entity)
        field-names (map (comp name :id) fields)
        field-list (clojure.string/join ", " field-names)]
    (str "SELECT " field-list " FROM " table " ORDER BY id DESC")))

(defn generate-get-query
  "Generates a default get query for an entity based on its table."
  [entity]
  (let [config (config/get-entity-config entity)
        table (:table config)]
    (str "SELECT * FROM " table " WHERE id = ?")))

(defn ensure-default-queries
  "Ensures an entity has default queries if not explicitly defined."
  [entity]
  (let [config (config/get-entity-config entity)
        queries (:queries config {})
        queries (if (:list queries)
                  queries
                  (assoc queries :list (generate-list-query entity)))
        queries (if (:get queries)
                  queries
                  (assoc queries :get (generate-get-query entity)))]
    (swap! config/config-cache assoc-in [entity :queries] queries)
    queries))

;; =============================================================================
;; Query with Hooks
;; =============================================================================

(defn execute-with-hooks
  "Executes a query with before/after hooks.
   
   Options:
   - :before-hook - Hook function to call before query
   - :after-hook - Hook function to call after query
   - :query-key - Which query to execute
   - :params - Query parameters
   - :conn - Connection key"
  [entity {:keys [before-hook after-hook] :as opts}]
  (let [config (config/get-entity-config entity)
        params (:params opts [])
        
        ;; Execute before hook
        params (if before-hook
                 (before-hook params)
                 params)
        
        ;; Execute query
        result (execute-query entity (assoc opts :params params))
        
        ;; Execute after hook
        result (if after-hook
                 (after-hook result params)
                 result)]
    
    result))

(defn list-with-hooks
  "Lists records with before-load and after-load hooks."
  [entity & [opts]]
  (let [config (config/get-entity-config entity)
        hooks (:hooks config)
        before-load (:before-load hooks)
        after-load (:after-load hooks)]
    (execute-with-hooks entity
                        (merge {:query-key :list
                                :before-hook before-load
                                :after-hook after-load}
                               opts))))

(defn get-with-hooks
  "Gets a record with before-load and after-load hooks."
  [entity id & [opts]]
  (let [config (config/get-entity-config entity)
        hooks (:hooks config)
        before-load (:before-load hooks)
        after-load (:after-load hooks)
        result (execute-with-hooks entity
                                   (merge {:query-key :get
                                           :params [id]
                                           :before-hook before-load
                                           :after-hook after-load}
                                          opts))]
    (first result)))

(comment
  ;; Usage examples
  (list-records :users)
  (get-record :users 1)
  (custom-query :users :active-users)
  (list-with-hooks :users)
  (get-with-hooks :users 1)
  (generate-list-query :users)
  (ensure-default-queries :users))
