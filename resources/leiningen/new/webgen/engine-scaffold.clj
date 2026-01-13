(ns {{sanitized}}.engine.scaffold
  "Scaffolding engine - generates entity configurations from database tables.
   
   Usage:
     lein scaffold products
     lein scaffold products --rights [A S]
     lein scaffold --all
     lein scaffold products --interactive"
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [{{sanitized}}.models.crud :as crud]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn get-base-ns
  "Gets the base namespace (project name) from the current namespace"
  []
  (-> (str *ns*)
      (str/split #"\.")
      first))

(defn hooks-path
  "Returns the path to hooks directory for this project"
  []
  (str "src/" (get-base-ns) "/hooks/"))

;; =============================================================================
;; SQL Type Mapping
;; =============================================================================

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
      (re-find #"image|photo|picture|img" column-name) :file
      (re-find #"description|comment|note|memo|text" column-name) :textarea
      (and (re-find #"VARCHAR|CHAR" sql-type) (> size 255)) :textarea
      
      ;; Type-based detection
      :else (or (get sql-type-map (first (str/split sql-type #"[(]")))
                :text))))

;; =============================================================================
;; Label Humanization
;; =============================================================================

(defn humanize-label
  "Converts field name to human-readable label"
  [field-name]
  (->> (str/split (name field-name) #"[_-]")
       (map str/capitalize)
       (str/join " ")))

;; =============================================================================
;; Schema Introspection
;; =============================================================================

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
      (catch Exception e
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
      (catch Exception e
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
   Returns [{:table :alquileres :foreign-key :id_propiedad} ...]"
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
                refs)))
      (catch Exception e
        (println "[WARN] Failed to get referencing tables for" table-name)
        []))))

;; =============================================================================
;; Field Generation
;; =============================================================================

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

;; =============================================================================
;; Entity Config Generation
;; =============================================================================

(defn get-display-field-name
  "Gets the display field name for a foreign key reference.
   Examples: :propiedades → nombre or titulo, :clientes → nombre + apellido, :agentes → nombre"
  [fk-table conn-key]
  (let [columns (get-table-columns fk-table conn-key)
        column-names (map #(keyword (str/lower-case (:column_name %))) columns)]
    (cond
      ;; Check for titulo (properties, etc)
      (some #{:titulo :title} column-names) 
      (if (some #{:titulo} column-names) :titulo :title)
      
      ;; Check for nombre/name (agents, clients, etc)
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
  (if (empty? foreign-keys)
    ;; No FKs - simple query
    {:list (str "SELECT * FROM " (name table-name) " ORDER BY id DESC")
     :get (str "SELECT * FROM " (name table-name) " WHERE id = ?")}
    
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
          order-clause (str " ORDER BY " table-alias ".id DESC")]
      
      {:list (str select-clause from-clause join-clause order-clause)
       :get (str "SELECT * FROM " (name table-name) " WHERE id = ?")})))

(defn get-subgrid-icon
  "Returns appropriate Bootstrap icon for a table based on its name"
  [table-name]
  (let [name-str (str/lower-case (name table-name))]
    (cond
      (re-find #"alquiler|rent" name-str) "bi bi-house-door"
      (re-find #"venta|sale|compra|purchase" name-str) "bi bi-currency-dollar"
      (re-find #"pago|payment" name-str) "bi bi-cash-stack"
      (re-find #"comision|commission" name-str) "bi bi-cash-coin"
      (re-find #"avaluo|appraisal|valuacion" name-str) "bi bi-calculator"
      (re-find #"contrato|contract" name-str) "bi bi-file-earmark-text"
      (re-find #"documento|document" name-str) "bi bi-file-earmark-pdf"
      (re-find #"tramite|procedure" name-str) "bi bi-clipboard-check"
      (re-find #"bitacora|log|audit" name-str) "bi bi-journal-text"
      (re-find #"cliente|client|customer" name-str) "bi bi-person"
      (re-find #"agente|agent" name-str) "bi bi-briefcase"
      (re-find #"propiedad|property" name-str) "bi bi-building"
      (re-find #"fiador|guarantor" name-str) "bi bi-shield-check"
      :else "bi bi-list-ul")))

(defn generate-subgrids
  "Generates subgrid configurations for tables that reference this table"
  [table-name conn-key]
  (let [refs (get-referencing-tables table-name conn-key)]
    (when (seq refs)
      (mapv (fn [ref]
              {:entity (:table ref)
               :title (humanize-label (:table ref))
               :foreign-key (:foreign-key ref)
               :icon (get-subgrid-icon (:table ref))
               :label (humanize-label (:table ref))})
            refs))))

(defn generate-entity-config
  "Generates complete entity configuration from table schema"
  [table-name & {:keys [conn rights title]
                 :or {conn :default
                      rights ["U" "A" "S"]
                      title nil}}]
  (let [columns (get-table-columns table-name conn)
        pk (get-primary-key table-name conn)
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
        subgrids (generate-subgrids table-name conn)
        queries (generate-queries table-name fks conn)]
    
    (cond-> {:entity (keyword table-name)
             :title table-title
             :table (name table-name)
             :connection conn
             :rights rights
             :mode :parameter-driven
             
             :fields fields-with-display
             
             :queries queries
             
             :actions {:new true :edit true :delete true}}
      
      ;; Add subgrids if any tables reference this one
      (seq subgrids) (assoc :subgrids subgrids))))

;; =============================================================================
;; Hook Stub Generation
;; =============================================================================

(defn generate-hook-stub
  "Generates a hook stub file for an entity"
  [entity-name & [fields]]
  (let [file-fields (filter #(= :file (:type %)) fields)
        has-file-fields? (seq file-fields)
        file-field-names (map :id file-fields)]
    (str "(ns {{sanitized}}.hooks." entity-name "\n"
       "  \"Business logic hooks for " entity-name " entity.\n"
       "   \n"
       "   SENIOR DEVELOPER: Implement custom business logic here.\n"
       "   \n"
       "   See: HOOKS_GUIDE.md for detailed documentation and examples.\n"
       "   Example: " (hooks-path) "alquileres.clj\n"
       "   \n"
       "   Uncomment the hooks you need and implement the logic.\"" 
       (when has-file-fields? "\n  (:require [{{sanitized}}.models.util :refer [image-link]])") ")\n"
       "\n"
       ";; =============================================================================\n"
       ";; Validators\n"
       ";; =============================================================================\n"
       "\n"
       ";; Example validator function:\n"
       ";; (defn validate-dates\n"
       ";;   \"Validates that end date is after start date\"\n"
       ";;   [params]\n"
       ";;   (let [start (:start_date params)\n"
       ";;         end (:end_date params)]\n"
       ";;     (when (and start end)\n"
       ";;       ;; Add your validation logic here\n"
       ";;       nil)))  ; Return nil if valid, or {:field \"error message\"}\n"
       "\n"
       ";; =============================================================================\n"
       ";; Computed Fields\n"
       ";; =============================================================================\n"
       "\n"
       ";; Example computed field:\n"
       ";; (defn compute-total\n"
       ";;   \"Computes total from quantity and price\"\n"
       ";;   [row]\n"
       ";;   (* (or (:quantity row) 0)\n"
       ";;      (or (:price row) 0)))\n"
       "\n"
       ";; =============================================================================\n"
       ";; Lifecycle Hooks\n"
       ";; =============================================================================\n"
       "\n"
       "(defn before-load\n"
       "  \"Hook executed before loading records.\n"
       "   \n"
       "   Use cases:\n"
       "   - Filter by user permissions\n"
       "   - Add default filters\n"
       "   - Log access\n"
       "   \n"
       "   Args: [params] - Query parameters\n"
       "   Returns: Modified params map\"\n"
       "  [params]\n"
       "  ;; TODO: Add your logic here\n"
       "  (println \"[INFO] Loading " entity-name " with params:\" params)\n"
       "  params)\n"
       "\n"
       "(defn after-load\n"
       "  \"Hook executed after loading records.\n"
       "   \n"
       "   Use cases:\n"
       "   - Add computed fields\n"
       "   - Format data\n"
       "   - Enrich with lookups\n"
       "   \n"
       "   Args: [rows params] - Loaded rows and query params\n"
       "   Returns: Modified rows vector\"\n"
       "  [rows params]\n"
       "  (println \"[INFO] Loaded\" (count rows) \"" entity-name " record(s)\")\n"
       (if has-file-fields?
         (str "  ;; Transform file fields to image links\n"
              "  (map #(-> %\n"
              (apply str (map (fn [field-id]
                                (str "            (assoc :" (name field-id) " (image-link (:" (name field-id) " %)))\n"))
                              file-field-names))
              "        ) rows))\n")
         (str "  ;; TODO: Add your transformations here, then return the result\n"
              "  ;; Example: (map #(assoc % :full-name (str (:first-name %) \" \" (:last-name %))) rows)\n"
              "  rows)\n"))
       "\n"
       "(defn before-save\n"
       "  \"Hook executed before saving a record.\n"
       "   \n"
       "   Use cases:\n"
       "   - Validate data\n"
       "   - Set defaults\n"
       "   - Transform values\n"
       "   - Check permissions\n"
       "   \n"
       "   Args: [params] - Form data to be saved\n"
       "   Returns: Modified params map OR {:errors {...}} if validation fails\"\n"
       "  [params]\n"
       "  (println \"[INFO] Saving " entity-name "...\")\n"
       (if has-file-fields?
         (let [first-file-field (first file-field-names)]
           (str "\n"
                "  ;; Handle file upload for " (name first-file-field) " field\n"
                "  ;; The system expects :file key, but our field is named :" (name first-file-field) "\n"
                "  (if-let [file-data (:" (name first-file-field) " params)]\n"
                "    (if (and (map? file-data) (:tempfile file-data))\n"
                "      ;; It's a file upload - move it to :file key so build-form-save finds it\n"
                "      (-> params\n"
                "          (assoc :file file-data)\n"
                "          (dissoc :" (name first-file-field) "))\n"
                "      ;; It's already a string (existing filename) - keep as is\n"
                "      params)\n"
                "    params))\n"))
         "  ;; TODO: Add validation and transformation logic\n  params)\n")
       "\n"
       "(defn after-save\n"
       "  \"Hook executed after successfully saving a record.\n"
       "   \n"
       "   Use cases:\n"
       "   - Send notifications\n"
       "   - Update related records\n"
       "   - Create audit logs\n"
       "   - Trigger workflows\n"
       "   \n"
       "   Args: [entity-id params] - Saved record ID and data\n"
       "   Returns: {:success true}\"\n"
       "  [entity-id params]\n"
       "  ;; TODO: Add post-save logic\n"
       "  (println \"[INFO] " (str/capitalize entity-name) " saved successfully. ID:\" entity-id)\n"
       "  {:success true})\n"
       "\n"
       "(defn before-delete\n"
       "  \"Hook executed before deleting a record.\n"
       "   \n"
       "   Use cases:\n"
       "   - Check for related records\n"
       "   - Verify permissions\n"
       "   - Prevent deletion if constraints\n"
       "   \n"
       "   Args: [entity-id] - ID of record to delete\n"
       "   Returns: {:success true} to allow, or {:errors {...}} to prevent\"\n"
       "  [entity-id]\n"
       "  ;; TODO: Add pre-delete checks\n"
       "  (println \"[INFO] Checking if " entity-name " can be deleted. ID:\" entity-id)\n"
       "  {:success true})\n"
       "\n"
       "(defn after-delete\n"
       "  \"Hook executed after successfully deleting a record.\n"
       "   \n"
       "   Use cases:\n"
       "   - Delete related files\n"
       "   - Update related records\n"
       "   - Send notifications\n"
       "   - Archive data\n"
       "   \n"
       "   Args: [entity-id] - ID of deleted record\n"
       "   Returns: {:success true}\"\n"
       "  [entity-id]\n"
       "  ;; TODO: Add post-delete logic\n"
       "  (println \"[INFO] " (str/capitalize entity-name) " deleted successfully. ID:\" entity-id)\n"
       "  {:success true})\n")))

(defn write-hook-stub
  "Writes hook stub file if it doesn't exist"
  [entity-name fields]
  (let [filename (str (hooks-path) entity-name ".clj")
        file (io/file filename)]
    (if (.exists file)
      (println (str "   ⚠️  Hook file already exists: " filename))
      (do
        (io/make-parents file)
        (spit file (generate-hook-stub entity-name fields))
        (println (str "   ✅ Generated hook stub: " filename))
        (println (str "      → Senior developer: Implement business logic here"))))))

;; =============================================================================
;; Config File Writing
;; =============================================================================

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
         "}"
         (when (:foreign-key field)
           (str " ;; FK: " (get-in field [:foreign-key :table]))))))

(defn format-subgrid
  "Formats a subgrid configuration for EDN output"
  [subgrid]
  (str "\n            {:entity " (:entity subgrid)
       "\n             :title \"" (:title subgrid) "\""
       "\n             :foreign-key " (:foreign-key subgrid)
       "\n             :icon \"" (:icon subgrid) "\""
       "\n             :label \"" (:label subgrid) "\"}"))

(defn generate-edn-content
  "Generates EDN file content with nice formatting and comments"
  [config]
  (let [entity-name (name (:entity config))
        has-subgrids? (seq (:subgrids config))]
    (str ";; AUTO-GENERATED entity configuration for " (:table config) "\n"
         ";; Generated: " (java.time.LocalDateTime/now) "\n"
         ";; \n"
         ";; This file was scaffolded from the database schema.\n"
         ";; Feel free to edit and customize as needed.\n"
         ";; \n"
         ";; JUNIOR DEVELOPER CHECKLIST:\n"
         ";;   ✅ Review field types and labels\n"
         ";;   ✅ Mark required fields\n"
         ";;   ✅ Set field visibility (hide in grid/form)\n"
         ";;   ✅ Add placeholders for text fields\n"
         ";;   ✅ Configure select options\n"
         ";;   ✅ Test CRUD operations\n"
         ";; \n"
         ";; SENIOR DEVELOPER HANDOFF:\n"
         ";;   ⚠️  Implement hooks in: " (hooks-path) entity-name ".clj\n"
         ";;   ⚠️  Add custom validators if needed\n"
         ";;   ⚠️  Add computed fields\n"
         ";;   ⚠️  Configure audit trail if needed\n"
         ";; \n"
         ";; See: HOOKS_GUIDE.md for examples\n"
         ";; \n\n"
         
         "{:entity " (:entity config) "\n"
         " :title \"" (:title config) "\"  ;; Edit this to your preference\n"
         " :table \"" (:table config) "\"\n"
         " :connection " (:connection config) "\n"
         " :rights " (vec (:rights config)) "  ;; User levels: U=User, A=Admin, S=System\n"
         " :mode " (:mode config) "\n"
         " \n"
         " ;; Fields auto-detected from database schema\n"
         " :fields ["
         (str/join "" (map format-field (:fields config)))
         "]\n"
         " \n"
         " ;; Auto-generated queries (customize as needed)\n"
         " :queries {:list \"" (get-in config [:queries :list]) "\"\n"
         "           :get \"" (get-in config [:queries :get]) "\"}\n"
         " \n"
         " ;; Available actions\n"
         " :actions {:new " (get-in config [:actions :new])
         " :edit " (get-in config [:actions :edit])
         " :delete " (get-in config [:actions :delete]) "}\n"
         " \n"
         " ;; Enable audit trail (tracks who created/modified and when)\n"
         " ;; Uncomment to enable:\n"
         " ;; :audit? true\n"
         " \n"
         " ;; Lifecycle hooks for business logic\n"
         " ;; Senior developer: Implement these in " (hooks-path) entity-name ".clj\n"
         " ;; Uncomment and implement as needed:\n"
         " ;; :hooks {:before-load :{{sanitized}}.hooks." entity-name "/before-load\n"
         " ;;         :after-load :{{sanitized}}.hooks." entity-name "/after-load\n"
         " ;;         :before-save :{{sanitized}}.hooks." entity-name "/before-save\n"
         " ;;         :after-save :{{sanitized}}.hooks." entity-name "/after-save\n"
         " ;;         :before-delete :{{sanitized}}.hooks." entity-name "/before-delete\n"
         " ;;         :after-delete :{{sanitized}}.hooks." entity-name "/after-delete}\n"
         
         ;; Add subgrids if they exist
         (if has-subgrids?
           (str " \n"
                " ;; Subgrids for related tables (auto-detected)\n"
                " :subgrids ["
                (str/join "" (map format-subgrid (:subgrids config)))
                "]}\n")
           ;; No subgrids - show examples
           (str " \n"
                " ;; Subgrids (parent-child relationships)\n"
                " ;; Uncomment if this entity has child records:\n"
                " ;; :subgrids [{:entity :related-table\n"
                " ;;             :title \"Related Records\"\n"
                " ;;             :foreign-key :parent_id\n"
                " ;;             :icon \"bi bi-list\"\n"
                " ;;             :label \"Related Records\"}]\n"
                "}\n")))))

(defn write-entity-config
  "Writes entity configuration to file"
  [config & {:keys [force?] :or {force? false}}]
  (let [filename (str "resources/entities/" (name (:entity config)) ".edn")
        file (io/file filename)]
    
    (when (and (.exists file) (not force?))
      (throw (ex-info (str "Entity config already exists: " filename "\nUse --force to overwrite")
                      {:file filename})))
    
    (io/make-parents file)
    (spit file (generate-edn-content config))
    filename))

;; =============================================================================
;; CLI Interface
;; =============================================================================

(defn scaffold-table
  "Scaffolds a single table"
  [table-name & {:keys [conn rights title force? with-hooks?]
                 :or {conn :default
                      rights ["U" "A" "S"]
                      title nil
                      force? false
                      with-hooks? true}}]
  (try
    (println (str "Scaffolding " table-name "..."))
    
    (let [config (generate-entity-config table-name
                                         :conn conn
                                         :rights rights
                                         :title title)
          filename (write-entity-config config :force? force?)
          field-count (count (:fields config))
          subgrid-count (count (:subgrids config))]
      
      (println (str "✅ Generated " filename))
      (println (str "   - " field-count " fields detected"))
      (when (pos? subgrid-count)
        (println (str "   - " subgrid-count " subgrid(s) detected")))
      (println (str "   - Default queries created"))
      
      ;; Generate hook stub file
      (when with-hooks?
        (write-hook-stub (name table-name) (:fields config)))
      
      (println)
      (println "JUNIOR DEVELOPER - Next steps:")
      (println (str "  1. Edit " filename))
      (println (str "     - Customize field labels"))
      (println (str "     - Mark required fields"))
      (println (str "     - Test at: /admin/" (name table-name)))
      (println)
      (println "SENIOR DEVELOPER - When needed:")
      (println (str "  2. Implement hooks in: " (hooks-path) (name table-name) ".clj"))
      (println (str "  3. Uncomment :hooks in " filename))
      (println (str "  4. See: HOOKS_GUIDE.md for examples"))
      (println))
    
    (catch Exception e
      (println (str "❌ Failed to scaffold " table-name ": " (.getMessage e)))
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
    
    (println (str "✅ Scaffolded " (count tables-to-scaffold) " entities"))
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
  (println "  --rights [U A S]    User rights (default: U A S)")
  (println "  --title \"Title\"     Custom title")
  (println "  --force             Overwrite existing config")
  (println "  --no-hooks          Skip hook stub generation")
  (println "  --all               Scaffold all tables")
  (println "  --exclude a,b,c     Tables to exclude (with --all)")
  (println)
  (println "Examples:")
  (println "  lein scaffold products")
  (println "  lein scaffold users --rights [A S] --title \"User Management\"")
  (println "  lein scaffold orders --no-hooks")
  (println "  lein scaffold --all --exclude sessions,schema_migrations")
  (println)
  (println "What gets generated:")
  (println "  ✅ Entity EDN config in resources/entities/")
  (println (str "  ✅ Hook stub file in " (hooks-path) " (unless --no-hooks)"))
  (println "  ✅ Auto-detected fields, foreign keys, subgrids")
  (println "  ✅ Junior/Senior handoff comments")
  (println))

;; =============================================================================
;; Main Entry Point
;; =============================================================================

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
