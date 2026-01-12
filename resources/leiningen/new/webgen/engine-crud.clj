(ns {{sanitized}}.engine.crud
  "Generic CRUD operations for parameter-driven entities.
   Handles create, read, update, delete with hooks and validation."
  (:require
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.query :as query]
   [{{sanitized}}.models.crud :as crud]))

;; =============================================================================
;; CRUD Operations
;; =============================================================================

(defn- execute-hook
  "Executes a hook function if it exists."
  [hook-fn data & args]
  (if (fn? hook-fn)
    (apply hook-fn data args)
    data))

(defn- validate-fields
  "Validates form data against entity field definitions."
  [entity data]
  (let [config (config/get-entity-config entity)
        fields (:fields config)
        errors (atom [])]
    
    ;; Check required fields
    (doseq [field fields]
      (when (:required? field)
        (let [field-id (:id field)
              value (get data field-id)]
          (when (or (nil? value) (and (string? value) (empty? value)))
            (swap! errors conj
                   {:field field-id
                    :message (str (:label field) " is required")})))))
    
    ;; Execute custom validators
    (doseq [field fields]
      (when-let [validator (:validation field)]
        (let [field-id (:id field)
              value (get data field-id)]
          (try
            (when-not (validator value data)
              (swap! errors conj
                     {:field field-id
                      :message (str (:label field) " is invalid")}))
            (catch Exception e
              (swap! errors conj
                     {:field field-id
                      :message (.getMessage e)}))))))
    
    (if (empty? @errors)
      {:valid? true :data data}
      {:valid? false :errors @errors})))

(defn- compute-fields
  "Computes values for computed fields."
  [entity data]
  (let [config (config/get-entity-config entity)
        fields (:fields config)]
    (reduce
     (fn [acc field]
       (if-let [compute-fn (:compute-fn field)]
         (let [field-id (:id field)
               computed-value (compute-fn acc)]
           (assoc acc field-id computed-value))
         acc))
     data
     fields)))

(defn- prepare-data
  "Prepares data for save - computes fields, runs validation."
  [entity data]
  (let [data (compute-fields entity data)
        validation (validate-fields entity data)]
    (if (:valid? validation)
      {:success true :data (:data validation)}
      {:success false :errors (:errors validation)})))

;; =============================================================================
;; Save Operation (Create/Update)
;; =============================================================================

(defn save-record
  "Saves a record (create or update based on presence of :id).
   
   Options:
   - :conn - Database connection key
   - :skip-hooks? - Skip before/after save hooks
   - :skip-validation? - Skip field validation"
  [entity data & [opts]]
  (let [config (config/get-entity-config entity)
        hooks (:hooks config)
        connection (or (:conn opts) (:connection config) :default)
        table (:table config)
        
        ;; Execute before-save hook
        data (if (and (not (:skip-hooks? opts))
                      (:before-save hooks))
               (execute-hook (:before-save hooks) data)
               data)
        
        ;; Validate and prepare data
        prepared (if (:skip-validation? opts)
                   {:success true :data data}
                   (prepare-data entity data))]
    
    (if (:success prepared)
      (let [clean-data (:data prepared)
            
            ;; Save to database
            result (crud/build-form-save clean-data table :conn connection)
            
            ;; Execute after-save hook
            _ (when (and result
                         (not (:skip-hooks? opts))
                         (:after-save hooks))
                (execute-hook (:after-save hooks) clean-data result))]
        
        {:success result
         :data clean-data})
      
      ;; Validation failed
      prepared)))

;; =============================================================================
;; Delete Operation
;; =============================================================================

(defn delete-record
  "Deletes a record by ID.
   
   Options:
   - :conn - Database connection key
   - :skip-hooks? - Skip before/after delete hooks"
  [entity id & [opts]]
  (let [config (config/get-entity-config entity)
        hooks (:hooks config)
        connection (or (:conn opts) (:connection config) :default)
        table (:table config)
        
        ;; Execute before-delete hook
        _ (when (and (not (:skip-hooks? opts))
                     (:before-delete hooks))
            (execute-hook (:before-delete hooks) {:id id}))
        
        ;; Delete from database
        result (crud/build-form-delete table id :conn connection)
        
        ;; Execute after-delete hook
        _ (when (and result
                     (not (:skip-hooks? opts))
                     (:after-delete hooks))
            (execute-hook (:after-delete hooks) {:id id} result))]
    
    {:success result}))

;; =============================================================================
;; Batch Operations
;; =============================================================================

(defn save-batch
  "Saves multiple records in a batch.
   Returns {:success count :errors [...]}"
  [entity records & [opts]]
  (let [results (map #(save-record entity % opts) records)
        successes (filter :success results)
        failures (remove :success results)]
    {:success (count successes)
     :failed (count failures)
     :errors (map :errors failures)}))

(defn delete-batch
  "Deletes multiple records by IDs.
   Returns {:success count :errors [...]}"
  [entity ids & [opts]]
  (let [results (map #(delete-record entity % opts) ids)
        successes (filter :success results)
        failures (remove :success results)]
    {:success (count successes)
     :failed (count failures)}))

;; =============================================================================
;; Audit Trail Support
;; =============================================================================

(defn- create-audit-entry
  "Creates an audit log entry for a CRUD operation."
  [entity operation data user-id]
  (when (:audit? (config/get-entity-config entity))
    (try
      (let [audit-data {:entity (name entity)
                        :operation (name operation)
                        :data (pr-str data)
                        :user_id user-id
                        :timestamp (java.time.Instant/now)}]
        ;; Insert into audit table (if exists)
        (crud/Insert :audit_log audit-data :conn :default))
      (catch Exception e
        (println "[WARN] Failed to create audit entry:" (.getMessage e))))))

(defn save-with-audit
  "Saves a record and creates an audit trail entry."
  [entity data user-id & [opts]]
  (let [result (save-record entity data opts)
        operation (if (:id data) :update :create)]
    (when (:success result)
      (create-audit-entry entity operation data user-id))
    result))

(defn delete-with-audit
  "Deletes a record and creates an audit trail entry."
  [entity id user-id & [opts]]
  (let [result (delete-record entity id opts)]
    (when (:success result)
      (create-audit-entry entity :delete {:id id} user-id))
    result))

;; =============================================================================
;; Soft Delete Support
;; =============================================================================

(defn soft-delete
  "Soft deletes a record by setting a deleted flag instead of removing it.
   Requires entity to have a :deleted_at or :deleted field."
  [entity id & [opts]]
  (let [config (config/get-entity-config entity)
        table (:table config)
        connection (or (:conn opts) (:connection config) :default)
        
        ;; Try to update deleted_at or deleted field
        sql (str "UPDATE " table " SET deleted_at = NOW() WHERE id = ?")
        result (try
                 (crud/Query [sql id] :conn connection)
                 true
                 (catch Exception _
                   ;; Try boolean deleted field
                   (try
                     (let [sql2 (str "UPDATE " table " SET deleted = TRUE WHERE id = ?")]
                       (crud/Query [sql2 id] :conn connection)
                       true)
                     (catch Exception e
                       (println "[ERROR] Soft delete failed:" (.getMessage e))
                       false))))]
    {:success result}))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn record-exists?
  "Checks if a record exists by ID."
  [entity id]
  (try
    (let [record (query/get-record entity id)]
      (boolean record))
    (catch Exception _
      false)))

(defn count-records
  "Counts total records for an entity."
  [entity & [opts]]
  (let [config (config/get-entity-config entity)
        table (:table config)
        connection (or (:conn opts) (:connection config) :default)
        sql (str "SELECT COUNT(*) as count FROM " table)
        result (crud/Query sql :conn connection)]
    (:count (first result))))

(comment
  ;; Usage examples
  (save-record :users {:lastname "Doe" :firstname "John" :username "john@example.com"})
  (save-record :users {:id 1 :lastname "Smith"})
  (delete-record :users 1)
  (save-with-audit :users {:lastname "Test"} 1)
  (delete-with-audit :users 1 1)
  (soft-delete :users 1)
  (record-exists? :users 1)
  (count-records :users)
  (save-batch :users [{:lastname "A"} {:lastname "B"}])
  (delete-batch :users [1 2 3]))
