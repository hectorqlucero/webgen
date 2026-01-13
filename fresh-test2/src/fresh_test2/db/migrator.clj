(ns fresh_test2.db.migrator
  "Database data migrator - copies data from SQLite to MySQL/PostgreSQL"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]))

(def db-configs
  {:sqlite
   {:classname "org.sqlite.JDBC"
    :subprotocol "sqlite"
    :subname "db/fresh_test2.sqlite"}
   
   :mysql
   {:classname "com.mysql.cj.jdbc.Driver"
    :subprotocol "mysql"
    :subname "//localhost:3306/rs"
    :user "root"
    :password "Patito0257."}
   
   :postgresql
   {:classname "org.postgresql.Driver"
    :subprotocol "postgresql"
    :subname "//localhost:5432/rs"
    :user "root"
    :password "Patito0257."}})

(defn get-db-spec [db-type]
  (get db-configs db-type))

(defn get-table-names
  "Get all table names from SQLite database"
  [source-db]
  (let [results (jdbc/query source-db
                           ["SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"])]
    (map :name results)))

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
              inserted (jdbc/insert-multi! target-db table-name rows)]
          {:table table-name
           :status :success
           :rows-copied (count inserted)
           :source-count source-count})
        
        (catch Exception e
          {:table table-name
           :status :error
           :error (.getMessage e)})))))

(defn copy-all-data
  "Copy all data from SQLite to target database"
  [& args]
  (let [target-type (keyword (or (first args) "mysql"))
        clear-target? (some #{"--clear" "-c"} args)
        source-db (get-db-spec :sqlite)
        target-db (get-db-spec target-type)]
    
    (println "\n=== Database Data Migrator ===")
    (println (str "Source: SQLite (db/fresh_test2.db)"))
    (println (str "Target: " (name target-type)))
    (println (str "Clear target tables: " (if clear-target? "YES" "NO")))
    (println)
    
    (try
      ;; Test connections
      (jdbc/query source-db ["SELECT 1"])
      (jdbc/query target-db ["SELECT 1"])
      
      (println "✓ Database connections successful\n")
      
      (let [tables (get-table-names source-db)
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
        (println "1. SQLite database exists at db/fresh_test2.db")
        (println "2. Target database server is running")
        (println "3. Target database credentials are correct in fresh_test2.db.migrator/db-configs")
        (println "4. Target database has been migrated (run: lein migrate)")))))

(defn -main [& args]
  (apply copy-all-data args))
