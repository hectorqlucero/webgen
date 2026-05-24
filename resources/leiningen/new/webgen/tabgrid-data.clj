(ns {{sanitized}}.tabgrid.data
  "Data fetching and transformation for TabGrid.
   Each relationship type has its own fetch strategy:
     :one-to-many  - lazy AJAX load (existing behaviour)
     :one-to-one   - server-side single record
     :many-to-many - server-side linked records + available picker list"
  (:require
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.query :as query]))

(defn build-fields-map
  "Builds a field map for rendering from entity config"
  [entity]
  (let [display-fields (config/get-display-fields entity)]
    (apply array-map
           (mapcat (fn [field]
                     [(:id field) (:label field)])
                   display-fields))))

(defn fetch-parent-record
  "Fetches a single parent record by ID"
  [entity parent-id]
  (when parent-id
    (query/get-with-hooks entity parent-id)))

(defn fetch-all-parent-records
  "Fetches all parent records for selection modal"
  [entity]
  (query/list-with-hooks entity))

(defn fetch-subgrid-records
  "Fetches subgrid records filtered by parent foreign key"
  [subgrid-entity parent-id foreign-key]
  (when (and subgrid-entity parent-id foreign-key)
    (let [all-records (query/list-with-hooks subgrid-entity)
          fk-keyword  (keyword foreign-key)
          filtered    (filter #(= (str (get % fk-keyword)) (str parent-id)) all-records)]
      filtered)))

(defn fetch-one-to-one-record
  "Fetches the single associated record for a one-to-one relationship, or nil."
  [sg-entity parent-id foreign-key]
  (when (and sg-entity parent-id foreign-key)
    (let [fk-kw (keyword foreign-key)]
      (first (filter #(= (str (get % fk-kw)) (str parent-id))
                     (query/list-with-hooks sg-entity))))))

(defn fetch-many-to-many-records
  "Returns the related-entity rows linked to parent-id via a junction table,
   enriched with junction-table attributes (e.g. proficiency, role).
   Returns {:records [...] :linked-ids #{...}}."
  [junction-entity related-entity parent-id parent-fk related-fk]
  (when (and junction-entity parent-id parent-fk)
    (let [parent-fk-kw  (keyword parent-fk)
          related-fk-kw (keyword related-fk)
          junctions     (filter #(= (str (get % parent-fk-kw)) (str parent-id))
                                (query/list-with-hooks junction-entity))
          related-by-id (when related-entity
                          (into {} (map (fn [r] [(:id r) r])
                                        (query/list-with-hooks related-entity))))
          linked-ids    (into #{} (map #(get % related-fk-kw) junctions))]
      {:records   (mapv (fn [j]
                          (merge (get related-by-id (get j related-fk-kw) {})
                                 j))
                        junctions)
       :linked-ids linked-ids})))

(defn fetch-available-for-linking
  "Returns related-entity records that are NOT yet linked via the junction table."
  [related-entity linked-ids]
  (when related-entity
    (remove #(contains? linked-ids (:id %)) (query/list-with-hooks related-entity))))

(defn count-subgrid-records
  "Counts subgrid records for the current parent record."
  [subgrid-entity parent-id foreign-key]
  (when (and subgrid-entity parent-id foreign-key)
    (try
      (let [all-records (query/list-with-hooks subgrid-entity)
            fk-keyword (keyword foreign-key)]
        (count (filter #(= (str (get % fk-keyword)) (str parent-id)) all-records)))
      (catch Exception _ nil))))

(defn prepare-subgrid-config
  "Prepares a single subgrid configuration for rendering.
   Pre-fetches data for :one-to-one and :many-to-many; :one-to-many stays lazy (AJAX)."
  [_parent-entity subgrid-spec parent-id]
  (let [sg-entity  (:entity subgrid-spec)
        sg-config  (config/get-entity-config sg-entity)
        sg-fields  (build-fields-map sg-entity)
        fk         (:foreign-key subgrid-spec)
        rel-type   (or (:relationship-type subgrid-spec) :one-to-many)
        base       {:entity            sg-entity
                    :title             (or (:title subgrid-spec) (:title sg-config) (name sg-entity))
                    :foreign-key       fk
                    :icon              (or (:icon subgrid-spec) "bi bi-list-ul")
                    :label             (or (:label subgrid-spec) (:title sg-config))
                    :relationship-type rel-type
                    :through-table     (:through-table subgrid-spec)
                    :related-entity    (:related-entity subgrid-spec)
                    :related-fk        (:related-fk subgrid-spec)
                    :fields            sg-fields
                    :actions           (or (:actions sg-config) {:new true :edit true :delete true})}]
    (case rel-type
      :one-to-one
      (let [record (when parent-id (fetch-one-to-one-record sg-entity parent-id fk))]
        (assoc base
               :record record
               :count  (if record 1 0)))

      :many-to-many
      (let [junction       (or (:through-table subgrid-spec) sg-entity)
            related        (:related-entity subgrid-spec)
            related-fk     (:related-fk subgrid-spec)
            related-fields (when related (build-fields-map related))
            m2m-data       (when parent-id
                             (fetch-many-to-many-records junction related parent-id fk related-fk))
            records        (or (:records m2m-data) [])
            linked-ids     (or (:linked-ids m2m-data) #{})
            available      (fetch-available-for-linking related linked-ids)]
        (assoc base
               ;; Use related-entity fields for display in the M2M pane
               :fields         (or related-fields sg-fields)
               :records        records
               :available      available
               :related-fields (or related-fields sg-fields)
               :count          (count records)))

      ;; :one-to-many — lazy AJAX; just count for the badge
      (let [cnt (when parent-id (count-subgrid-records sg-entity parent-id fk))]
        (assoc base :count cnt)))))

(defn prepare-all-subgrids
  "Prepares all subgrid configurations from parent entity config, with record counts."
  [parent-entity parent-id]
  (let [parent-config (config/get-entity-config parent-entity)
        subgrid-specs (:subgrids parent-config)]
    (when (seq subgrid-specs)
      (mapv #(prepare-subgrid-config parent-entity % parent-id) subgrid-specs))))

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
        subgrids (prepare-all-subgrids entity (when parent-record
                                                (or (:id parent-record)
                                                    parent-id)))
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
