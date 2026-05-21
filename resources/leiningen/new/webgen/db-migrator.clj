(ns {{sanitized}}.db.migrator
  "Database data migrator - copies data between configured databases"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [{{sanitized}}.models.crud :as crud]))

(defn get-db-spec [db-type]
  (get crud/dbs (keyword db-type)))

(defn get-table-names
  "Get all table names from a database"
  [source-db]
  (let [proto (:subprotocol source-db)]
    (cond
      (= proto "sqlite")
      (map :name (jdbc/query source-db
                             ["SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'ragtime_migrations' ORDER BY name"]))

      (= proto "mysql")
      (map :table_name (jdbc/query source-db
                                   ["SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name"]))

      :else
      (map :table_name (jdbc/query source-db
                                   ["SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name"])))))

(defn get-fk-dependencies
  "Get foreign key dependencies as a map of {child-table #{parent-table}}.
   Queries the source database for FK relationships."
  [source-db]
  (let [proto (:subprotocol source-db)]
    (cond
      (= proto "sqlite")
      (reduce (fn [acc table]
                (let [fk-rows (jdbc/query source-db [(str "PRAGMA foreign_key_list(" table ")")])]
                  (if (seq fk-rows)
                    (assoc acc table (set (map :table fk-rows)))
                    acc)))
              {}
              (get-table-names source-db))

      (= proto "mysql")
      (reduce (fn [acc row]
                (update acc (:table_name row) (fnil conj #{}) (:referenced_table_name row)))
              {}
              (jdbc/query source-db
                          ["SELECT table_name, referenced_table_name
                            FROM information_schema.key_column_usage
                            WHERE table_schema = DATABASE()
                            AND referenced_table_name IS NOT NULL"]))

      :else
      (reduce (fn [acc row]
                (update acc (:table_name row) (fnil conj #{}) (:referenced_table_name row)))
              {}
              (jdbc/query source-db
                          ["SELECT kcu.table_name, ccu.table_name AS referenced_table_name
                            FROM information_schema.key_column_usage kcu
                            JOIN information_schema.constraint_column_usage ccu
                            ON kcu.constraint_name = ccu.constraint_name
                            WHERE kcu.table_schema = 'public'
                            AND ccu.table_name IS NOT NULL"])))))

(defn topological-sort
  "Sort tables so that parent tables come before child tables based on FK dependencies."
  [tables fk-deps]
  (let [table-set (set tables)
        filtered-deps (into {} (for [[child parents] fk-deps
                                     :when (contains? table-set child)]
                                 [child (set (filter table-set parents))]))
        visited (atom #{})
        result (atom [])
        in-stack (atom #{})]
    (letfn [(visit [node]
              (when (contains? @in-stack node)
                (throw (ex-info (str "Circular dependency detected involving: " node) {:node node})))
              (when-not (contains? @visited node)
                (swap! in-stack conj node)
                (doseq [dep (get filtered-deps node #{})]
                  (visit dep))
                (swap! in-stack disj node)
                (swap! visited conj node)
                (swap! result conj node)))]
      (doseq [t tables]
        (visit t)))
    @result))

(defn get-column-types
  "Get column type info for a table as a map of {column-name type-string}."
  [db table-name]
  (let [proto (:subprotocol db)]
    (cond
      (= proto "sqlite")
      (into {} (for [row (jdbc/query db [(str "PRAGMA table_info(" table-name ")")])]
                 [(:name row) (:type row)]))

      (= proto "mysql")
      (into {} (for [row (jdbc/query db
                                      ["SELECT column_name, column_type
                                        FROM information_schema.columns
                                        WHERE table_schema = DATABASE()
                                        AND table_name = ?" table-name])]
                 [(:column_name row) (:column_type row)]))

      :else
      (into {} (for [row (jdbc/query db
                                      ["SELECT column_name, data_type
                                        FROM information_schema.columns
                                        WHERE table_schema = 'public'
                                        AND table_name = ?" table-name])]
                 [(:column_name row) (:data_type row)])))))

(defn datetime-type?
  "Check if a column type string represents a date/datetime/timestamp type."
  [type-str]
  (when type-str
    (let [t (str/lower-case type-str)]
      (or (str/includes? t "datetime")
          (str/includes? t "timestamp")
          (str/includes? t "date")))))

(defn unix-timestamp-ms?
  "Check if a value looks like a Unix timestamp in milliseconds."
  [v]
  (and (number? v)
       (or (> (Math/abs (double v)) 1000000000))))

(defn unix-timestamp-ms-string?
  "Check if a string value looks like a Unix timestamp in milliseconds."
  [v]
  (and (string? v)
       (re-matches #"-?\d{9,15}" v)
       (try
         (let [n (Long/parseLong v)]
           (> (Math/abs (double n)) 1000000000))
         (catch Exception _ false))))

(defn ms-to-timestamp
  "Convert Unix epoch milliseconds to a java.sql.Timestamp."
  [ms]
  (java.sql.Timestamp. ms))

(defn convert-row-values
  "Convert row values that are Unix timestamps to proper datetime values
   when the target column is a date/datetime/timestamp type."
  [row col-types]
  (into {} (for [[k v] row]
             (let [col-name (name k)
                   col-type (get col-types col-name)]
               (if (datetime-type? col-type)
                 (cond
                   (unix-timestamp-ms? v)
                   [k (ms-to-timestamp v)]

                   (unix-timestamp-ms-string? v)
                   [k (ms-to-timestamp (Long/parseLong v))]

                   :else
                   [k v])
                 [k v])))))

(defn get-column-names
  "Get column names for a table"
  [db table-name]
  (let [results (jdbc/query db [(str "SELECT * FROM " table-name " LIMIT 0")])]
    (keys (first (jdbc/result-set-seq (.getMetaData (first results)))))))

(defn table-exists?
  "Check if table exists in target database"
  [target-db table-name]
  (try
    (jdbc/query target-db [(str "SELECT 1 FROM " table-name " LIMIT 1")])
    true
    (catch Exception _ false)))

(defn get-row-count
  "Get number of rows in a table"
  [db table-name]
  (try
    (let [result (jdbc/query db [(str "SELECT COUNT(*) as cnt FROM " table-name)])]
      (or (:cnt (first result)) (:count (first result)) 0))
    (catch Exception _ 0)))

(defn clear-table
  "Clear all data from a table"
  [db table-name]
  (jdbc/execute! db [(str "DELETE FROM " table-name)]))

(defn copy-table-data
  "Copy all data from source table to target table"
  [source-db target-db table-name & {:keys [clear-target?] :or {clear-target? false}}]
  (let [source-count (get-row-count source-db table-name)
        target-exists? (table-exists? target-db table-name)]

    (cond
      (not target-exists?)
      {:table table-name :status :skipped :reason "Table doesn't exist in target database"}

      (zero? source-count)
      {:table table-name :status :skipped :reason "No data in source table"}

      :else
      (try
        (when clear-target?
          (clear-table target-db table-name))

        (let [rows (jdbc/query source-db [(str "SELECT * FROM " table-name)])
              ;; Get target column types and convert timestamp values if needed
              target-col-types (get-column-types target-db table-name)
              converted-rows (map #(convert-row-values % target-col-types) rows)
              inserted (jdbc/insert-multi! target-db table-name converted-rows)]
          {:table table-name
           :status :success
           :rows-copied (count inserted)
           :source-count source-count})

        (catch Exception e
          {:table table-name
           :status :error
           :error (.getMessage e)})))))

(defn copy-all-data
  "Copy all data between databases. Usage:
   lein copy-data mysql localdb          ; MySQL → SQLite
   lein copy-data localdb mysql          ; SQLite → MySQL
   lein copy-data mysql localdb --clear  ; clear target first"
  [& args]
  (let [all-args    (vec args)
        flags       (set (filter #(str/starts-with? (str %) "-") all-args))
        pos-args    (remove #(str/starts-with? (str %) "-") all-args)
        source-key  (keyword (or (first pos-args) "mysql"))
        target-key  (keyword (or (second pos-args) "localdb"))
        clear-target? (or (contains? flags "--clear") (contains? flags "-c"))
        source-db   (get-db-spec source-key)
        target-db-raw (get-db-spec target-key)
        ;; For SQLite targets, disable FK constraints during copy
        target-db   (if (= (:subprotocol target-db-raw) "sqlite")
                      (update target-db-raw :subname
                              #(str/replace (str %) #"foreign_keys=on" "foreign_keys=off"))
                      target-db-raw)]

    (println "\n=== Database Data Migrator ===")
    (println (str "Source: " (name source-key) " (" (:subprotocol source-db) ")"))
    (println (str "Target: " (name target-key) " (" (:subprotocol target-db) ")"))
    (println (str "Clear target tables: " (if clear-target? "YES" "NO")))
    (println)

    (try
      ;; Test connections
      (jdbc/query source-db ["SELECT 1"])
      (jdbc/query target-db ["SELECT 1"])

      (println "✓ Database connections successful\n")

      (let [raw-tables (remove #(#{"users_view" "ragtime_migrations"} %)
                               (get-table-names source-db))
            fk-deps (get-fk-dependencies source-db)
            tables (topological-sort raw-tables fk-deps)
            results (doall
                     (for [table tables]
                       (do
                         (print (str "Copying " table "... "))
                         (flush)
                         (let [result (copy-table-data source-db target-db table :clear-target? clear-target?)]
                           (case (:status result)
                             :success (println (str "✓ " (:rows-copied result) " rows"))
                             :skipped (println (str "⊘ " (:reason result)))
                             :error (println (str "✗ " (:error result))))
                           result))))
            success (count (filter #(= :success (:status %)) results))
            skipped (count (filter #(= :skipped (:status %)) results))
            errors (count (filter #(= :error (:status %)) results))
            total-rows (reduce + 0 (map #(or (:rows-copied %) 0) results))]

        (println)
        (println "=== Summary ===")
        (println (str "Tables processed: " (count tables)))
        (println (str "  Success: " success))
        (println (str "  Skipped: " skipped))
        (println (str "  Errors: " errors))
        (println (str "Total rows copied: " total-rows))

        (when (pos? errors)
          (println "\nErrors:")
          (doseq [result (filter #(= :error (:status %)) results)]
            (println (str "  " (:table result) ": " (:error result)))))

        (when (pos? success)
          (println "\n✓ Migration complete!")))

      (catch Exception e
        (println (str "✗ Connection error: " (.getMessage e)))
        (println "\nMake sure:")
        (println "1. Source database is accessible")
        (println "2. Target database is accessible")
        (println "3. Both databases have been migrated (run: lein migrate)")))))


(defn -main [& args]
  (apply copy-all-data args))
