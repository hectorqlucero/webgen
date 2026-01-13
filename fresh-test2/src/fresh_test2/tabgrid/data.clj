(ns fresh_test2.tabgrid.data
  "Data fetching and transformation for TabGrid"
  (:require
   [fresh_test2.engine.config :as config]
   [fresh_test2.engine.query :as query]))

;; =============================================================================
;; Field Mapping
;; =============================================================================

(defn build-fields-map
  "Builds a field map for rendering from entity config"
  [entity]
  (let [display-fields (config/get-display-fields entity)]
    (apply array-map
           (mapcat (fn [field]
                     [(:id field) (:label field)])
                   display-fields))))

;; =============================================================================
;; Parent Data
;; =============================================================================

(defn fetch-parent-record
  "Fetches a single parent record by ID"
  [entity parent-id]
  (when parent-id
    (query/get-with-hooks entity parent-id)))

(defn fetch-all-parent-records
  "Fetches all parent records for selection modal"
  [entity]
  (query/list-with-hooks entity))

;; =============================================================================
;; Subgrid Data
;; =============================================================================

(defn fetch-subgrid-records
  "Fetches subgrid records filtered by parent foreign key"
  [subgrid-entity parent-id foreign-key]
  (when (and subgrid-entity parent-id foreign-key)
    (let [all-records (query/list-with-hooks subgrid-entity)
          fk-keyword (keyword foreign-key)
          filtered (filter #(= (str (get % fk-keyword)) (str parent-id)) all-records)]
      filtered)))

;; =============================================================================
;; Subgrid Configuration
;; =============================================================================

(defn prepare-subgrid-config
  "Prepares a single subgrid configuration for rendering"
  [parent-entity subgrid-spec]
  (let [sg-entity (:entity subgrid-spec)
        sg-config (config/get-entity-config sg-entity)
        sg-fields (build-fields-map sg-entity)]
    {:entity sg-entity
     :title (or (:title subgrid-spec) (:title sg-config) (name sg-entity))
     :foreign-key (:foreign-key subgrid-spec)
     :icon (or (:icon subgrid-spec) "bi bi-list-ul")
     :label (or (:label subgrid-spec) (:title sg-config))
     :fields sg-fields
     :actions (or (:actions sg-config) {:new true :edit true :delete true})}))

(defn prepare-all-subgrids
  "Prepares all subgrid configurations from parent entity config"
  [parent-entity]
  (let [parent-config (config/get-entity-config parent-entity)
        subgrid-specs (:subgrids parent-config)]
    (when (seq subgrid-specs)
      (mapv #(prepare-subgrid-config parent-entity %) subgrid-specs))))

;; =============================================================================
;; Complete TabGrid Data
;; =============================================================================

(defn prepare-tabgrid-data
  "Prepares all data needed for tabgrid rendering"
  [entity parent-id]
  (let [entity-config (config/get-entity-config entity)
        ;; Get ALL records for the selection modal
        all-records (fetch-all-parent-records entity)
        ;; Get the specific parent record to display
        parent-record (if parent-id
                        (fetch-parent-record entity parent-id)
                        (first all-records))
        ;; Parent display shows ONLY the selected record (or first if none selected)
        parent-display-rows (if parent-record [parent-record] [])
        fields (build-fields-map entity)
        subgrids (prepare-all-subgrids entity)
        actions (or (:actions entity-config) {:new true :edit true :delete true})]
    {:entity entity
     :entity-name (name entity)
     :title (:title entity-config)
     :parent-record parent-record
     :parent-rows parent-display-rows  ; Single record for display
     :all-records all-records          ; All records for modal
     :fields fields
     :subgrids subgrids
     :actions actions}))
