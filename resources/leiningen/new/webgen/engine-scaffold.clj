(ns {{sanitized}}.engine.scaffold
  "Scaffolding engine - generates entity configurations from database tables.
   
   Usage:
     lein scaffold products
     lein scaffold products --rights [A S]
     lein scaffold --all
     lein scaffold products --interactive"
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.java.io :as io]s
   [clojure.java.jdbc :as jdbc]
   [{{sanitized}}.models.crud :as crud]))

(defn get-base-ns
  "Gets the base namespace (project name) from the current namespace"
  []
  (-> (namespace ::_)
      (str/split #"\.")
      first))

(defn hooks-path
  "Returns the path to hooks directory for this project"
  []
  (str "src/" (get-base-ns) "/hooks/"))

(defn hook-namespace-segment
  "Converts entity/table names to valid Clojure namespace segments.
   Example: employee_profiles -> employee-profiles"
  [entity-name]
  (-> (name entity-name)
      (str/replace #"_" "-")))

(def sql-type-map
  "Maps SQL types to entity field types"
  {;; Text types
   "VARCHAR" :text
   "CHAR" :text
   "TEXT" :textarea
   "LONGTEXT" :textarea
   "MEDIUMTEXT" :textarea
   "TINYTEXT" :text

   ;; Numeric types
   "INT" :number
   "INTEGER" :number
   "BIGINT" :number
   "SMALLINT" :number
   "TINYINT" :number
   "DECIMAL" :decimal
   "NUMERIC" :decimal
   "FLOAT" :decimal
   "DOUBLE" :decimal
   "REAL" :decimal

   ;; Date/Time types
   "DATE" :date
   "DATETIME" :datetime
   "TIMESTAMP" :datetime
   "TIME" :text

   ;; Boolean types
   "BOOLEAN" :checkbox
   "BOOL" :checkbox
   "BIT" :checkbox

   ;; Binary types
   "BLOB" :file
   "BYTEA" :file
   "BINARY" :file})

(defn detect-field-type
  "Detects field type from SQL column info"
  [column-info]
  (let [sql-type (str/upper-case (or (:type_name column-info)
                                     (:column_type column-info)
                                     "TEXT"))
        column-name (str/lower-case (:column_name column-info))
        size (:column_size column-info 0)]

    (cond
      ;; Convention-based detection
      (re-find #"email|mail|e_mail" column-name) :email
      (re-find #"password|passwd|pwd" column-name) :password
      (re-find #"phone|tel|mobile|cell" column-name) :text
      (re-find #"url|website|link|uri" column-name) :text
      (re-find #"image|photo|picture|img|avatar|logo|thumbnail|thumb|banner|cover" column-name) :file
      (re-find #"description|comment|note|memo|text" column-name) :textarea
      (and (re-find #"VARCHAR|CHAR" sql-type) (> size 255)) :textarea

      ;; Type-based detection
      :else (or (get sql-type-map (first (str/split sql-type #"[(]")))
                :text))))

(defn humanize-label
  "Converts field name to human-readable label"
  [field-name]
  (->> (str/split (name field-name) #"[_-]")
       (map str/capitalize)
       (str/join " ")))

(defn get-catalog-from-connection
  "Extracts the database/catalog name from a JDBC connection.
   For MySQL, this is critical for metadata queries."
  [connection]
  (try
    (.getCatalog connection)
    (catch Exception e
      (println "[WARN] Could not get catalog from connection:" (.getMessage e))
      nil)))

(defn normalize-table-name
  "Normalizes table name for metadata queries.
   MySQL is case-sensitive depending on OS, so we try the original name first."
  [table-name]
  (name table-name))

(defn get-table-columns
  "Gets column information for a table"
  [table-name conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              table (normalize-table-name table-name)
              ;; Try with catalog first (important for MySQL)
              rs (.getColumns metadata catalog nil table nil)
              columns (jdbc/metadata-result rs)]
          (if (seq columns)
            (vec columns)
            ;; Fallback: try uppercase table name
            (let [table-upper (str/upper-case table)
                  rs-upper (.getColumns metadata catalog nil table-upper nil)
                  columns-upper (jdbc/metadata-result rs-upper)]
              (vec columns-upper)))))
      (catch Exception e
        (println "[ERROR] Failed to introspect table:" table-name)
        (.printStackTrace e)
        []))))

(defn get-primary-key
  "Gets primary key column for a table"
  [table-name conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              table (normalize-table-name table-name)
              rs (.getPrimaryKeys metadata catalog nil table)
              pks (jdbc/metadata-result rs)]
          (if (seq pks)
            (:column_name (first pks))
            ;; Fallback: try uppercase
            (let [table-upper (str/upper-case table)
                  rs-upper (.getPrimaryKeys metadata catalog nil table-upper)
                  pks-upper (jdbc/metadata-result rs-upper)]
              (or (:column_name (first pks-upper)) "id")))))
      (catch Exception _
        (println "[WARN] Failed to get primary key for" table-name "- using 'id'")
        "id"))))

(defn get-foreign-keys
  "Gets foreign key relationships for a table"
  [table-name conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              table (normalize-table-name table-name)
              rs (.getImportedKeys metadata catalog nil table)
              fks (jdbc/metadata-result rs)]
          (if (seq fks)
            (mapv (fn [fk]
                    {:column (keyword (str/lower-case (:fkcolumn_name fk)))
                     :references-table (keyword (str/lower-case (:pktable_name fk)))
                     :references-column (keyword (str/lower-case (:pkcolumn_name fk)))})
                  fks)
            ;; Fallback: try uppercase
            (let [table-upper (str/upper-case table)
                  rs-upper (.getImportedKeys metadata catalog nil table-upper)
                  fks-upper (jdbc/metadata-result rs-upper)]
              (mapv (fn [fk]
                      {:column (keyword (str/lower-case (:fkcolumn_name fk)))
                       :references-table (keyword (str/lower-case (:pktable_name fk)))
                       :references-column (keyword (str/lower-case (:pkcolumn_name fk)))})
                    fks-upper)))))
      (catch Exception _
        (println "[WARN] Failed to get foreign keys for" table-name)
        []))))

(defn get-all-tables
  "Gets list of all tables in database (excluding system tables)"
  [conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              rs (.getTables metadata catalog nil "%" (into-array String ["TABLE"]))
              tables (jdbc/metadata-result rs)
              ;; Filter out system tables (including MySQL system tables)
              filtered (remove #(let [name (str/lower-case (:table_name %))]
                                  (or (str/starts-with? name "sqlite_")
                                      (str/starts-with? name "pg_")
                                      (str/starts-with? name "mysql_")
                                      (str/starts-with? name "sys_")
                                      (str/starts-with? name "information_schema")
                                      (str/starts-with? name "performance_schema")
                                      (= name "schema_migrations")
                                      (= name "ragtime_migrations")))
                               tables)]
          (mapv #(keyword (str/lower-case (:table_name %))) filtered)))
      (catch Exception e
        (println "[ERROR] Failed to get table list")
        (.printStackTrace e)
        []))))

(defn get-referencing-tables
  "Gets tables that reference the given table (reverse foreign keys for subgrids).
  Returns [{:table :child_table :foreign-key :parent_id} ...]"
  [table-name conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              table (normalize-table-name table-name)
              ;; Get exported keys = tables that reference this table
              rs (.getExportedKeys metadata catalog nil table)
              refs (jdbc/metadata-result rs)]
          (if (seq refs)
            (mapv (fn [ref]
                    {:table (keyword (str/lower-case (:fktable_name ref)))
                     :foreign-key (keyword (str/lower-case (:fkcolumn_name ref)))
                     :column (keyword (str/lower-case (:pkcolumn_name ref)))})
                  refs)
            ;; Fallback: try uppercase
            (let [table-upper (str/upper-case table)
                  rs-upper (.getExportedKeys metadata catalog nil table-upper)
                  refs-upper (jdbc/metadata-result rs-upper)]
              (mapv (fn [ref]
                      {:table (keyword (str/lower-case (:fktable_name ref)))
                       :foreign-key (keyword (str/lower-case (:fkcolumn_name ref)))
                       :column (keyword (str/lower-case (:pkcolumn_name ref)))})
                    refs-upper)))))
      (catch Exception _
        (println "[WARN] Failed to get referencing tables for" table-name)
        []))))

(defn get-parent-tables
  "Gets tables that this table is a subgrid of (tables that reference back to us).
   Returns set of parent table keywords that have this table as a subgrid."
  [table-name conn-key]
  (let [all-tables (get-all-tables conn-key)]
    (into #{}
          (for [parent-table all-tables
                :let [refs (get-referencing-tables parent-table conn-key)
                      has-this-as-subgrid? (some #(= (:table %) (keyword table-name)) refs)]
                :when has-this-as-subgrid?]
            parent-table))))

(defn generate-field
  "Generates field configuration from column info"
  [column-info foreign-keys parent-tables]
  (let [column-name (keyword (str/lower-case (:column_name column-info)))
        field-type (detect-field-type column-info)
        is-nullable (not= 0 (:nullable column-info))
        is-pk (= "YES" (:is_autoincrement column-info))
        fk (first (filter #(= (:column %) column-name) foreign-keys))]

    (cond
      ;; Primary key
      is-pk
      {:id column-name
       :label "ID"
       :type :hidden}

      ;; Foreign key
      fk
      (let [is-parent-ref? (contains? parent-tables (:references-table fk))]
        (if is-parent-ref?
          ;; This FK references a parent table (subgrid relationship) - hide it
          {:id column-name
           :label (str (humanize-label (:references-table fk)) " ID")
           :type :hidden}
          ;; This FK is a lookup/reference - show as select
          {:id column-name
           :label (humanize-label (:references-table fk))
           :type :select
           :required? (not is-nullable)
           :options []
           :hidden-in-grid? true  ;; Hide FK ID in grid, show display name instead
           :foreign-key {:table (:references-table fk)
                         :column (:references-column fk)}}))

      ;; Regular field
      :else
      (let [base {:id column-name
                  :label (humanize-label column-name)
                  :type field-type}]
        (cond-> base
          (not is-nullable) (assoc :required? true)
          (= field-type :text) (assoc :placeholder (str (humanize-label column-name) "..."))
          (= field-type :textarea) (assoc :placeholder (str (humanize-label column-name) "..."))
          (= field-type :email) (assoc :placeholder "user@example.com"))))))

(defn get-display-field-name
  "Gets the display field name for a foreign key reference.
  Prefers common human-readable fields (title/name/description)."
  [fk-table conn-key]
  (let [columns (get-table-columns fk-table conn-key)
        column-names (map #(keyword (str/lower-case (:column_name %))) columns)]
    (cond
      ;; Check for title-like field
      (some #{:titulo :title} column-names)
      (if (some #{:titulo} column-names) :titulo :title)

      ;; Check for name-like field
      (some #{:nombre :name} column-names)
      (if (some #{:nombre} column-names) :nombre :name)

      ;; Check for descripcion
      (some #{:descripcion :description} column-names)
      (if (some #{:descripcion} column-names) :descripcion :description)

      ;; Fallback to second column (skip id)
      :else (second column-names))))

(defn needs-name-concatenation?
  "Checks if a table needs CONCAT for full names (has nombre + apellido_paterno)"
  [fk-table conn-key]
  (let [columns (get-table-columns fk-table conn-key)
        column-names (map #(keyword (str/lower-case (:column_name %))) columns)]
    (and (some #{:nombre} column-names)
         (some #{:apellido_paterno} column-names))))

(defn generate-display-field
  "Generates a display field for a foreign key"
  [fk-info]
  (let [fk-table (:table fk-info)
        base-name (name fk-table)
        ;; Remove plural 's' for singular form
        singular (if (str/ends-with? base-name "s")
                   (subs base-name 0 (dec (count base-name)))
                   base-name)
        display-field-id (keyword (str singular "_nombre"))]
    {:id display-field-id
     :label (humanize-label fk-table)
     :type :text
     :grid-only? true}))

(defn generate-queries
  "Generates queries with JOINs for FK display names"
  [table-name foreign-keys conn-key]
  (let [pk-col (or (some-> (get-primary-key table-name conn-key)
                           str/lower-case)
                   "id")]
    (if (empty? foreign-keys)
      ;; No FKs - simple query
      {:list (str "SELECT * FROM " (name table-name) " ORDER BY " pk-col " DESC")
       :get (str "SELECT * FROM " (name table-name) " WHERE " pk-col " = ?")}

      ;; Has FKs - build JOIN query
      (let [;; Use first 3 letters for main table to reduce conflicts
            table-alias (let [n (name table-name)]
                          (if (> (count n) 2)
                            (subs n 0 3)
                            n))
            ;; Track used aliases to avoid duplicates
            used-aliases (atom #{table-alias})
            joins (for [fk foreign-keys]
                    (let [fk-table (:references-table fk)
                          fk-col (:column fk)
                          ;; Generate unique alias (3 letters)
                          base-alias (let [n (name fk-table)]
                                       (if (> (count n) 2)
                                         (subs n 0 3)
                                         n))
                          fk-alias (loop [alias base-alias
                                          suffix 1]
                                     (if (contains? @used-aliases alias)
                                       (recur (str base-alias suffix) (inc suffix))
                                       (do
                                         (swap! used-aliases conj alias)
                                         alias)))
                          display-field (get-display-field-name fk-table conn-key)
                          singular (if (str/ends-with? (name fk-table) "s")
                                     (subs (name fk-table) 0 (dec (count (name fk-table))))
                                     (name fk-table))
                          display-col (str singular "_nombre")]
                      {:join (str " LEFT JOIN " (name fk-table) " " fk-alias
                                  " ON " table-alias "." (name fk-col) " = " fk-alias ".id")
                       :select (if (needs-name-concatenation? fk-table conn-key)
                                 (str "CONCAT(" fk-alias ".nombre, ' ', " fk-alias ".apellido_paterno) as " display-col)
                                 (str fk-alias "." (name display-field) " as " display-col))}))
            select-clause (str "SELECT " table-alias ".*"
                               (when (seq joins)
                                 (str ", " (str/join ", " (map :select joins)))))
            from-clause (str " FROM " (name table-name) " " table-alias)
            join-clause (str/join "" (map :join joins))
            order-clause (str " ORDER BY " table-alias "." pk-col " DESC")]

        {:list (str select-clause from-clause join-clause order-clause)
         :get (str "SELECT * FROM " (name table-name) " WHERE " pk-col " = ?")}))))

(defn get-subgrid-icon
  "Returns a generic Bootstrap icon for a table based on broad name patterns."
  [table-name]
  (let [name-str (str/lower-case (name table-name))]
    (cond
      (re-find #"log|audit|history" name-str) "bi bi-journal-text"
      (re-find #"doc|file|attachment" name-str) "bi bi-file-earmark-text"
      (re-find #"user|person|employee|profile" name-str) "bi bi-person"
      (re-find #"project|task|work" name-str) "bi bi-kanban"
      :else "bi bi-list-ul")))

(defn get-menu-icon
  "Returns a smart generic Bootstrap icon for an entity/table."
  [table-name]
  (let [name-str (str/lower-case (name table-name))]
    (cond
      (re-find #"report|print|export" name-str) "bi bi-print"
      (re-find #"invoice|payment|billing|price|cost|finance|money" name-str) "bi bi-cash-stack"
      (re-find #"doc|file|attachment" name-str) "bi bi-file-earmark-text"
      (re-find #"image|photo|media|gallery" name-str) "bi bi-image"
      (re-find #"user|person|employee|profile|contact|customer|client" name-str) "bi bi-person"
      (re-find #"team|group|department|organization|company" name-str) "bi bi-people"
      (re-find #"project|task|ticket|board|sprint" name-str) "bi bi-kanban"
      (re-find #"product|item|catalog|inventory|stock" name-str) "bi bi-box-seam"
      (re-find #"order|purchase|sale|checkout" name-str) "bi bi-cart"
      (re-find #"log|audit|history|event" name-str) "bi bi-journal-text"
      (re-find #"setting|config|preference|option" name-str) "bi bi-gear"
      :else "bi bi-table")))

(defn- unique-index-on-columns?
  "Returns true if the given columns are covered by a unique index on the table.
   Useful for identifying composite unique FK constraints on junction tables."
  [table-name columns conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              table (normalize-table-name table-name)
              rs (.getIndexInfo metadata catalog nil table false true)
              indexes (jdbc/metadata-result rs)
              indexes-by-name (group-by :index_name indexes)
              target-set (set columns)]
          (boolean
           (some (fn [[index-name rows]]
                   (let [non-unique (:non_unique (first rows))
                         unique? (or (false? non-unique)
                                     (= 0 non-unique))
                         cols (->> rows
                                   (map :column_name)
                                   (remove nil?)
                                   (map #(keyword (str/lower-case %))))]
                     (and index-name
                          unique?
                          (= target-set (set cols))
                          (= (count cols) (count target-set)))))
                 indexes-by-name))))
      (catch Exception _ false))))

(defn- junction-table?
  "Best-effort detection for many-to-many junction tables.
   A table is considered a junction when it has at least 2 FKs and either few
   non-FK business columns or a composite unique FK constraint."
  [table-name conn-key]
  (let [fks (get-foreign-keys table-name conn-key)
        fk-cols (set (map :column fks))
        columns (->> (get-table-columns table-name conn-key)
                     (map (comp keyword str/lower-case :column_name))
                     (remove nil?))
        non-meta-cols (remove #{:id :created_at :updated_at :deleted_at} columns)
        business-cols (remove fk-cols non-meta-cols)]
    (and (>= (count fks) 2)
         (or (<= (count business-cols) 2)
             (unique-index-on-columns? table-name fk-cols conn-key)))))

(defn- fk-column-unique?
  "Returns true if the given foreign key column has a unique index on the
   child table, which implies a 1:1 relationship rather than 1:N."
  [table-name fk-column conn-key]
  (let [conn (get crud/dbs conn-key :default)
        db-spec (if (map? conn) conn (crud/build-db-spec conn))]
    (try
      (with-open [connection (jdbc/get-connection db-spec)]
        (let [metadata (.getMetaData connection)
              catalog (get-catalog-from-connection connection)
              table (normalize-table-name table-name)
              rs (.getIndexInfo metadata catalog nil table false true)
              indexes (jdbc/metadata-result rs)
              indexes-by-name (group-by :index_name indexes)]
          (boolean
           (some (fn [[index-name rows]]
                   (let [non-unique (:non_unique (first rows))
                         unique? (or (false? non-unique)
                                     (= 0 non-unique))
                         cols (->> rows
                                   (map :column_name)
                                   (remove nil?)
                                   (map #(keyword (str/lower-case %))))]
                     (and index-name
                          unique?
                          (= [fk-column] cols))))
                 indexes-by-name))))
      (catch Exception _ false))))

(defn- classify-subgrid-relationship
  "Classifies child relationship type for parent -> child subgrid.
   Returns one of :one-to-one, :one-to-many, :many-to-many."
  [_parent-table child-table fk-column conn-key]
  (let [child-pk (-> (get-primary-key child-table conn-key)
                     str/lower-case
                     keyword)]
    (cond
      (junction-table? child-table conn-key) :many-to-many
      (= child-pk fk-column) :one-to-one
      (fk-column-unique? child-table fk-column conn-key) :one-to-one
      :else :one-to-many)))

(defn- get-many-to-many-side
  "When child-table is a junction table, returns {:related-entity :skills :related-fk :skill_id}"
  [_parent-table child-table fk-column conn-key]
  (let [child-fks (get-foreign-keys child-table conn-key)
        other-fk  (first (remove #(= (:column %) fk-column) child-fks))]
    {:related-entity (:references-table other-fk)
     :related-fk     (:column other-fk)}))

(defn generate-subgrids
  "Generates relationship-aware subgrid configurations for tables that reference this table.
   Includes :relationship-type, :related-fk etc. for the rendering layer."
  [table-name conn-key]
  (let [refs (->> (get-referencing-tables table-name conn-key)
                  (remove #(= (:table %) table-name)))]
    (when (seq refs)
      (mapv (fn [ref]
              (let [child-table (:table ref)
                    fk-column   (:foreign-key ref)
                    rel-type    (classify-subgrid-relationship table-name child-table fk-column conn-key)
                    m2m-side    (when (= rel-type :many-to-many)
                                  (get-many-to-many-side table-name child-table fk-column conn-key))
                    related-entity (:related-entity m2m-side)
                    related-fk     (:related-fk m2m-side)
                    title (if (and (= rel-type :many-to-many) related-entity)
                            (str (humanize-label related-entity) " (via " (humanize-label child-table) ")")
                            (humanize-label child-table))]
                (cond-> {:entity            child-table
                         :title             title
                         :foreign-key       fk-column
                         :icon              (get-subgrid-icon child-table)
                         :label             title
                         :relationship-type rel-type}
                  (= rel-type :many-to-many)
                  (assoc :through-table  child-table
                         :related-entity related-entity
                         :related-fk     related-fk))))
            refs))))

(defn generate-entity-config
  "Generates complete entity configuration from table schema"
  [table-name & {:keys [conn rights title]
                 :or {conn :default
                      rights ["U" "A" "S"]
                      title nil}}]
  (let [columns (get-table-columns table-name conn)
        fks (get-foreign-keys table-name conn)
        parent-tables (get-parent-tables table-name conn)
        base-fields (mapv #(generate-field % fks parent-tables) columns)

        ;; Generate display fields for each FK (insert after the FK field)
        fields-with-display (reduce
                             (fn [acc field]
                               (if (:foreign-key field)
                                 ;; FK field - add it and its display field
                                 (conj acc
                                       field
                                       (generate-display-field (:foreign-key field)))
                                 ;; Regular field - just add it
                                 (conj acc field)))
                             []
                             base-fields)

        table-title (or title (humanize-label table-name))
        file-fields (filter #(= :file (:type %)) fields-with-display)
        hook-ns-segment (hook-namespace-segment table-name)
        auto-hooks (when (seq file-fields)
                     {:after-load (symbol (str (get-base-ns) ".hooks." hook-ns-segment "/after-load"))
                      :before-save (symbol (str (get-base-ns) ".hooks." hook-ns-segment "/before-save"))})
        subgrids (generate-subgrids table-name conn)
        has-parent? (seq fks)
        has-children? (seq subgrids)
        menu-hidden? (and has-parent? (not has-children?))
        queries (generate-queries table-name fks conn)]

    (cond-> {:entity (keyword table-name)
             :title table-title
             :table (name table-name)
             :connection conn
             :rights rights
             :mode :parameter-driven
             :menu-order 999
             :menu-icon (get-menu-icon table-name)

             :fields fields-with-display

             :queries queries

             :actions {:new true :edit true :delete true}}

      ;; Hide leaf child entities from top-level menu.
      menu-hidden? (assoc :menu-hidden true)

      ;; Auto-enable required hooks for file/image fields.
      (seq file-fields) (assoc :hooks auto-hooks)

      ;; Add subgrids if any tables reference this one
      (seq subgrids) (assoc :subgrids subgrids))))

(defn generate-hook-stub
  "Generates a minimal hook stub file for an entity."
  [entity-name & [fields]]
  (let [file-fields (filter #(= :file (:type %)) fields)
        has-file-fields? (seq file-fields)
        file-field-names (map :id file-fields)
        base-ns (get-base-ns)
        hook-ns-segment (hook-namespace-segment entity-name)]
    (str "(ns " base-ns ".hooks." hook-ns-segment
         (when has-file-fields?
           (str "\n  (:require [" base-ns ".models.util :refer [image-link]])"))
         ")\n\n"
         "(defn before-load [params]\n"
         "  params)\n\n"
         "(defn after-load [rows _params]\n"
         (if has-file-fields?
           (str "  (map #(-> %\n"
                (apply str (map (fn [field-id]
                                  (str "            (assoc :" (name field-id) " (image-link (:" (name field-id) " %)))\n"))
                                file-field-names))
                "        ) rows))\n\n")
           "  rows)\n\n")
         "(defn before-save [params]\n"
         (if has-file-fields?
           (let [first-file-field (first file-field-names)]
             (str "  (if-let [file-data (:" (name first-file-field) " params)]\n"
                  "    (if (and (map? file-data) (:tempfile file-data))\n"
                  "      (-> params\n"
                  "          (assoc :file file-data :file-column :" (name first-file-field) ")\n"
                  "          (dissoc :" (name first-file-field) "))\n"
                  "      params)\n"
                  "    params))\n\n"))
           "  params)\n\n")
         "(defn after-save [_entity-id _params]\n"
         "  {:success true})\n\n"
         "(defn before-delete [_entity-id]\n"
         "  {:success true})\n\n"
         "(defn after-delete [_entity-id]\n"
         "  {:success true})\n")))

(defn write-hook-stub
  "Writes hook stub file. Overwrites when force? is true."
  [entity-name fields & {:keys [force?] :or {force? false}}]
  (let [filename (str (hooks-path) entity-name ".clj")
        file (io/file filename)
        existed? (.exists file)]
    (if (and (.exists file) (not force?))
      (println (str "   ⚠️  Hook file already exists: " filename " (use --force to regenerate)"))
      (do
        (io/make-parents file)
        (spit file (generate-hook-stub entity-name fields))
        (println (str (if existed? "   Regenerated" "   Generated") " hook stub: " filename))))))

(defn format-field
  "Formats a field configuration for EDN output"
  [field]
  (let [base (str "\n          {:id " (:id field)
                  " :label \"" (:label field) "\""
                  " :type " (:type field))]
    (str base
         (when (:required? field) " :required? true")
         (when (:placeholder field) (str " :placeholder \"" (:placeholder field) "\""))
         (when (:options field) " :options []")
         (when (:hidden-in-grid? field) " :hidden-in-grid? true")
         (when (:grid-only? field) " :grid-only? true")
         "}")))

(defn format-subgrid
  "Formats a subgrid configuration for EDN output"
  [subgrid]
  (str "\n            {:entity " (:entity subgrid)
       "\n             :title \"" (:title subgrid) "\""
       "\n             :foreign-key " (:foreign-key subgrid)
       "\n             :icon \"" (:icon subgrid) "\""
       "\n             :label \"" (:label subgrid) "\""
       (when-let [rt (:relationship-type subgrid)]
         (str "\n             :relationship-type " rt))
       (when-let [through (:through-table subgrid)]
         (str "\n             :through-table " through))
       (when-let [related (:related-entity subgrid)]
         (str "\n             :related-entity " related))
       (when-let [rfk (:related-fk subgrid)]
         (str "\n             :related-fk " rfk))
       "}"))

(defn generate-edn-content
  "Generates EDN file content."
  [config]
  (let [hooks-map (:hooks config)
        has-subgrids? (seq (:subgrids config))]
    (str "{:entity " (:entity config) "\n"
         " :title \"" (:title config) "\"\n"
         " :table \"" (:table config) "\"\n"
         " :connection " (:connection config) "\n"
         " :rights " (vec (:rights config)) "\n"
         " :mode " (:mode config) "\n"
         " :menu-order " (or (:menu-order config) 999) "\n"
         (when (:menu-hidden config)
           " :menu-hidden true\n")
         (when-let [menu-category (:menu-category config)]
           (str " :menu-category " menu-category "\n"))
         (when-let [menu-category-label (:menu-category-label config)]
           (str " :menu-category-label \"" menu-category-label "\"\n"))
         (when-let [menu-category-order (:menu-category-order config)]
           (str " :menu-category-order " menu-category-order "\n"))
         (when-let [menu-category-icon (:menu-category-icon config)]
           (str " :menu-category-icon \"" menu-category-icon "\"\n"))
         (when-let [menu-icon (:menu-icon config)]
           (str " :menu-icon \"" menu-icon "\"\n"))
         (when-let [dropdown-icon (:dropdown-icon config)]
           (str " :dropdown-icon \"" dropdown-icon "\"\n"))
         " \n"
         " :fields ["
         (str/join "" (map format-field (:fields config)))
         "]\n"
         " \n"
         " :queries {:list \"" (get-in config [:queries :list]) "\"\n"
         "           :get \"" (get-in config [:queries :get]) "\"}\n"
         " \n"
         " :actions {:new " (get-in config [:actions :new])
         " :edit " (get-in config [:actions :edit])
         " :delete " (get-in config [:actions :delete]) "}\n"
         (when hooks-map
           (str " \n"
                " :hooks {:after-load :" (str (:after-load hooks-map)) "\n"
                "         :before-save :" (str (:before-save hooks-map)) "}\n"))

         ;; Add subgrids if they exist
         (if has-subgrids?
           (str " \n"
                " :subgrids ["
                (str/join "" (map format-subgrid (:subgrids config)))
                "]}\n")
           "}\n"))))

(defn read-existing-config
  "Reads an existing entity EDN config. Returns nil if file missing or unparseable."
  [entity-name]
  (let [filename (str "resources/entities/" (name entity-name) ".edn")]
    (when (.exists (io/file filename))
      (try
        (edn/read-string (slurp filename))
        (catch Exception e
          (println "[WARN] Could not parse existing config for" entity-name ":" (.getMessage e))
          nil)))))

(defn merge-subgrid-configs
  "Merges newly-scaffolded subgrids into existing ones without losing user changes.
   - New relationships not yet in the file are appended.
   - Existing subgrids gain :relationship-type / :related-fk if they are still missing."
  [existing-subgrids generated-subgrids]
  (let [existing-entities (into #{} (map :entity existing-subgrids))
        new-sgs           (remove #(contains? existing-entities (:entity %)) generated-subgrids)
        ;; Enrich existing entries with freshly detected metadata (only fills nil keys)
        updated-existing  (mapv (fn [sg]
                                  (let [gen (first (filter #(= (:entity %) (:entity sg))
                                                           generated-subgrids))]
                                    (if gen
                                      (merge (select-keys gen [:relationship-type
                                                               :through-table
                                                               :related-entity
                                                               :related-fk])
                                             sg)  ; existing wins for user-set keys
                                      sg)))
                                existing-subgrids)]
    (vec (concat updated-existing new-sgs))))

(defn merge-entity-configs
  "Merges a freshly generated config into an existing one.
   Preserves user customisations (:fields, :title, :queries, :hooks, menu metadata).
   Updates :subgrids by adding new detected relationships."
  [existing generated]
  (let [merged-sgs (merge-subgrid-configs
                    (or (:subgrids existing) [])
                    (or (:subgrids generated) []))
        merged-hooks (or (:hooks existing) (:hooks generated))
        merged-menu (reduce (fn [acc k]
                              (if (contains? acc k)
                                acc
                                (if-let [v (get generated k)]
                                  (assoc acc k v)
                                  acc)))
                            existing
                            [:menu-order
                             :menu-hidden
                             :menu-category
                             :menu-category-label
                             :menu-category-order
                             :menu-category-icon
                             :menu-icon
                             :dropdown-icon])]
    (cond-> merged-menu
      merged-hooks (assoc :hooks merged-hooks)
      (seq merged-sgs) (assoc :subgrids merged-sgs)
      (empty? merged-sgs) (dissoc :subgrids))))

(defn write-entity-config
  "Writes entity configuration to file.
   Default (no --force): merges with existing to preserve customisations.
   --force: overwrites completely.
   Returns [filename op] where op is :created/:merged/:overwritten/:skipped."
  [config & {:keys [force?] :or {force? false}}]
  (let [entity-name (name (:entity config))
        filename    (str "resources/entities/" entity-name ".edn")
        file        (io/file filename)
        exists?     (.exists file)
        existing    (when exists? (read-existing-config (:entity config)))]
    (cond
      (not exists?)
      (do (io/make-parents file)
          (spit file (generate-edn-content config))
          [filename :created])

      force?
      (do (spit file (generate-edn-content config))
          [filename :overwritten])

      (some? existing)
      (let [merged (merge-entity-configs existing config)]
        (spit file (generate-edn-content merged))
        [filename :merged])

      :else
      (do (println "[WARN] Could not read existing config, skipping" filename)
          [filename :skipped]))))

(defn scaffold-table
  "Scaffolds a single table. Idempotent: merges with existing config by default."
  [table-name & {:keys [conn rights title force? with-hooks?]
                 :or {conn       :default
                      rights     ["U" "A" "S"]
                      title      nil
                      force?     false
                      with-hooks? true}}]
  (try
    (println (str "Scaffolding " table-name "..."))

    (let [config        (generate-entity-config table-name
                                                :conn   conn
                                                :rights rights
                                                :title  title)
          [filename op] (write-entity-config config :force? force?)
          field-count   (count (:fields config))
          subgrid-count (count (:subgrids config))
          op-label      (case op
                          :created     "Created"
                          :merged      "Updated (merged)"
                          :overwritten "Overwritten"
                          :skipped     "Skipped"
                          "Done")]

      (println (str op-label " " filename))
      (when (= op :merged)
        (println "   ↳ Preserved your customizations, added new relationships only"))
      (when (not= op :skipped)
        (println (str "   - " field-count " fields detected"))
        (when (pos? subgrid-count)
          (println (str "   - " subgrid-count " subgrid(s) detected")))
        (println "   - Default queries included"))

      (when with-hooks?
        (write-hook-stub (name table-name) (:fields config) :force? force?))

      (println)
      (println (str "Done: " filename " -> /admin/" (name table-name)
                    (if (:hooks config)
                      " (hooks auto-enabled for file/image fields)"
                      " (add :hooks if needed)")))
      (println))

    (catch Exception e
      (println (str "Failed to scaffold " table-name ": " (.getMessage e)))
      (.printStackTrace e))))

(defn scaffold-all
  "Scaffolds all tables in database"
  [& {:keys [conn exclude force? with-hooks?]
      :or {conn :default
           exclude []
           force? false
           with-hooks? true}}]
  (let [tables (get-all-tables conn)
        excluded-set (set (map keyword exclude))
        tables-to-scaffold (remove excluded-set tables)]

    (println (str "Found " (count tables) " tables"))
    (println (str "Scaffolding " (count tables-to-scaffold) " tables..."))
    (println)

    (doseq [table tables-to-scaffold]
      (scaffold-table table :conn conn :force? force? :with-hooks? with-hooks?))

    (println (str "Scaffolded " (count tables-to-scaffold) " entities"))
    (println)
    (println "All entity configurations created!")
    (println "Review and customize them in resources/entities/")
    (when with-hooks?
      (println (str "Hook stubs created in " (hooks-path))))))

(defn print-usage
  "Prints usage information"
  []
  (println "Scaffold - Generate entity configurations from database tables")
  (println)
  (println "Usage:")
  (println "  lein scaffold <table>              # Scaffold single table")
  (println "  lein scaffold <table> --force      # Overwrite existing config")
  (println "  lein scaffold <table> --no-hooks   # Skip hook stub generation")
  (println "  lein scaffold --all                # Scaffold all tables")
  (println "  lein scaffold --all --exclude sessions,migrations")
  (println)
  (println "Options:")
  (println "  --conn <key>        Database connection (:default, :pg, :localdb)")
  (println "  --rights [U A S]    User rights")
  (println "  --title \"Title\"     Custom title")
  (println "  --force             Overwrite existing config")
  (println "  --no-hooks          Skip hook stub generation")
  (println "  --all               Scaffold all tables")
  (println "  --exclude a,b,c     Tables to exclude (with --all)")
  (println)
  (println "Examples:")
  (println "  lein scaffold employees")
  (println "  lein scaffold employees --rights [A S] --title \"Employees\"")
  (println "  lein scaffold employee_profiles --no-hooks")
  (println "  lein scaffold --all --exclude sessions,schema_migrations")
  (println)
  (println "What gets generated:")
  (println "  Entity EDN config in resources/entities/")
  (println (str "  Hook stub file in " (hooks-path) " (unless --no-hooks)"))
  (println "  Auto-detected fields, foreign keys, subgrids")
  (println))

(defn -main
  "Main entry point for scaffold command"
  [& args]
  (if (empty? args)
    (print-usage)
    (let [args-vec (vec args)
          all? (some #{"--all"} args)
          force? (some #{"--force"} args)
          with-hooks? (not (some #{"--no-hooks"} args))
          conn-idx (.indexOf args-vec "--conn")
          conn (if (>= conn-idx 0)
                 (keyword (get args-vec (inc conn-idx)))
                 :default)
          rights-idx (.indexOf args-vec "--rights")
          rights (if (>= rights-idx 0)
                   (read-string (get args-vec (inc rights-idx)))
                   ["U" "A" "S"])
          title-idx (.indexOf args-vec "--title")
          title (if (>= title-idx 0)
                  (get args-vec (inc title-idx))
                  nil)
          exclude-idx (.indexOf args-vec "--exclude")
          exclude (if (>= exclude-idx 0)
                    (str/split (get args-vec (inc exclude-idx)) #",")
                    [])
          table-name (when-not all?
                       (first (remove #(str/starts-with? % "--") args)))]

      (if all?
        (scaffold-all :conn conn :exclude exclude :force? force? :with-hooks? with-hooks?)
        (if table-name
          (scaffold-table (keyword table-name)
                          :conn conn
                          :rights rights
                          :title title
                          :force? force?
                          :with-hooks? with-hooks?)
          (print-usage))))))

(comment
  ;; Usage examples
  (scaffold-table :products)
  (scaffold-table :users :rights ["A" "S"] :title "User Management")
  (scaffold-all)
  (scaffold-all :exclude [:sessions :schema_migrations])

  ;; Test introspection
  (get-all-tables :default)
  (get-table-columns :users :default)
  (get-foreign-keys :orders :default))
