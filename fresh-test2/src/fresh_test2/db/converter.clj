(ns fresh_test2.db.converter
  "Database migration converter - converts SQLite migrations to MySQL/PostgreSQL"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]))

;; SQL Type Mappings
(def type-mappings
  {:mysql
   {"INTEGER PRIMARY KEY AUTOINCREMENT" "INT AUTO_INCREMENT PRIMARY KEY"
    "INTEGER AUTOINCREMENT" "INT AUTO_INCREMENT"
    "INTEGER" "INT"
    "TEXT" "TEXT"
    "REAL" "DOUBLE"
    "BLOB" "BLOB"
    "datetime('now')" "CURRENT_TIMESTAMP"
    "date('now')" "CURRENT_DATE"
    "strftime('%d/%m/%Y', " "DATE_FORMAT("
    "strftime('%Y-%m-%d', " "DATE_FORMAT("
    "strftime('%H:%M:%S', " "TIME_FORMAT("
    "strftime('%Y', " "YEAR("
    "strftime('%m', " "MONTH("
    "strftime('%d', " "DAY("}
   
   :postgresql
   {"INTEGER PRIMARY KEY AUTOINCREMENT" "SERIAL PRIMARY KEY"
    "INTEGER AUTOINCREMENT" "SERIAL"
    "INTEGER" "INTEGER"
    "TEXT" "TEXT"
    "REAL" "DOUBLE PRECISION"
    "BLOB" "BYTEA"
    "datetime('now')" "CURRENT_TIMESTAMP"
    "date('now')" "CURRENT_DATE"
    "strftime('%d/%m/%Y', " "TO_CHAR("
    "strftime('%Y-%m-%d', " "TO_CHAR("
    "strftime('%H:%M:%S', " "TO_CHAR("
    "strftime('%Y', " "EXTRACT(YEAR FROM "
    "strftime('%m', " "EXTRACT(MONTH FROM "
    "strftime('%d', " "EXTRACT(DAY FROM "}})

(defn convert-date-format
  "Convert SQLite date format strings to MySQL/PostgreSQL format"
  [sql target-db]
  (if (= target-db :mysql)
    (-> sql
        (str/replace #"strftime\('%d/%m/%Y',\s*([^)]+)\)" "DATE_FORMAT($1, '%d/%m/%Y')")
        (str/replace #"strftime\('%Y-%m-%d',\s*([^)]+)\)" "DATE_FORMAT($1, '%Y-%m-%d')")
        (str/replace #"strftime\('%H:%M:%S',\s*([^)]+)\)" "TIME_FORMAT($1, '%H:%i:%S')")
        (str/replace #"strftime\('%Y',\s*([^)]+)\)" "YEAR($1)")
        (str/replace #"strftime\('%m',\s*([^)]+)\)" "MONTH($1)")
        (str/replace #"strftime\('%d',\s*([^)]+)\)" "DAY($1)")
        ;; Convert julianday date calculations to TIMESTAMPDIFF
        (str/replace #"\(julianday\('now'\)\s*-\s*julianday\(([^)]+)\)\)\s*/\s*365\.25" "TIMESTAMPDIFF(YEAR, $1, NOW())")
        (str/replace #"julianday\('now'\)" "UNIX_TIMESTAMP(NOW()) / 86400")
        (str/replace #"julianday\(([^)]+)\)" "UNIX_TIMESTAMP($1) / 86400")
        ;; Convert || string concatenation to CONCAT
        (str/replace #"\|\|" "CONCAT_OPERATOR_PLACEHOLDER"))
    (-> sql
        (str/replace #"strftime\('%d/%m/%Y',\s*([^)]+)\)" "TO_CHAR($1, 'DD/MM/YYYY')")
        (str/replace #"strftime\('%Y-%m-%d',\s*([^)]+)\)" "TO_CHAR($1, 'YYYY-MM-DD')")
        (str/replace #"strftime\('%H:%M:%S',\s*([^)]+)\)" "TO_CHAR($1, 'HH24:MI:SS')")
        (str/replace #"strftime\('%Y',\s*([^)]+)\)" "EXTRACT(YEAR FROM $1)")
        (str/replace #"strftime\('%m',\s*([^)]+)\)" "EXTRACT(MONTH FROM $1)")
        (str/replace #"strftime\('%d',\s*([^)]+)\)" "EXTRACT(DAY FROM $1)")
        ;; PostgreSQL supports || natively
        (str/replace #"julianday\('now'\)" "EXTRACT(EPOCH FROM NOW()) / 86400")
        (str/replace #"julianday\(([^)]+)\)" "EXTRACT(EPOCH FROM $1) / 86400"))))

(defn fix-string-concat
  "Convert SQLite || concatenation to MySQL CONCAT() for expressions within parentheses"
  [sql]
  (loop [s sql]
    (if-let [match (re-find #"\(([^()]*CONCAT_OPERATOR_PLACEHOLDER[^()]*)\)" s)]
      (let [[full inner] match
            parts (str/split inner #"\s*CONCAT_OPERATOR_PLACEHOLDER\s*")
            concat-expr (str "CONCAT(" (str/join ", " parts) ")")]
        (recur (str/replace s full (str "(" concat-expr ")"))))
      (str/replace s "CONCAT_OPERATOR_PLACEHOLDER" ", "))))

(defn convert-sql-types
  "Convert SQLite SQL syntax to target database syntax"
  [sql target-db]
  (let [mappings (get type-mappings target-db)
        sql-with-types (reduce (fn [s [from to]]
                                 (str/replace s (re-pattern (str "(?i)" (java.util.regex.Pattern/quote from))) to))
                               sql
                               mappings)
        sql-with-dates (convert-date-format sql-with-types target-db)]
    (if (= target-db :mysql)
      (fix-string-concat sql-with-dates)
      sql-with-dates)))

(defn parse-migration-number
  "Extract migration number from filename (e.g., '003' from '003-contactos.sqlite.up.sql')"
  [filename]
  (when-let [match (re-find #"^(\d+)-" filename)]
    (second match)))

(defn get-migration-name
  "Extract migration name from filename"
  [filename]
  (when-let [match (re-find #"^\d+-([^.]+)\." filename)]
    (second match)))

(defn read-migration-file
  "Read and return contents of a migration file"
  [file-path]
  (slurp file-path))

(defn write-migration-file
  "Write converted SQL to a new migration file"
  [file-path content]
  (spit file-path content))

(defn convert-migration-file
  "Convert a single SQLite migration file to target database"
  [sqlite-file target-db]
  (let [filename (.getName sqlite-file)
        migration-num (parse-migration-number filename)
        migration-name (get-migration-name filename)
        direction (if (str/includes? filename ".up.") "up" "down")
        target-ext (name target-db)
        target-filename (str migration-num "-" migration-name "." target-ext "." direction ".sql")
        target-file (io/file (.getParent sqlite-file) target-filename)
        sql-content (read-migration-file sqlite-file)
        converted-sql (convert-sql-types sql-content target-db)]
    
    (write-migration-file target-file converted-sql)
    {:source filename
     :target target-filename
     :status :converted}))

(defn find-sqlite-migrations
  "Find all SQLite migration files that don't have target database equivalents"
  [migrations-dir target-db]
  (let [all-files (file-seq (io/file migrations-dir))
        sqlite-files (filter #(and (.isFile %)
                                   (str/ends-with? (.getName %) ".sqlite.up.sql"))
                            all-files)
        target-ext (name target-db)]
    (filter (fn [sqlite-file]
              (let [filename (.getName sqlite-file)
                    migration-num (parse-migration-number filename)
                    migration-name (get-migration-name filename)
                    target-filename (str migration-num "-" migration-name "." target-ext ".up.sql")
                    target-file (io/file (.getParent sqlite-file) target-filename)]
                (not (.exists target-file))))
            sqlite-files)))

(defn convert-migrations
  "Convert all SQLite migrations to target database format"
  [& args]
  (let [target-db (keyword (or (first args) "mysql"))
        migrations-dir "resources/migrations"
        sqlite-files (find-sqlite-migrations migrations-dir target-db)]
    
    (println (str "\n=== Database Migration Converter ==="))
    (println (str "Target database: " (name target-db)))
    (println (str "Migrations directory: " migrations-dir))
    (println (str "Found " (count sqlite-files) " SQLite migration(s) to convert\n"))
    
    (if (empty? sqlite-files)
      (println "No migrations to convert. All SQLite migrations already have target equivalents.")
      (do
        (doseq [sqlite-file sqlite-files]
          (let [result (convert-migration-file sqlite-file target-db)]
            (println (str "✓ Converted: " (:source result) " → " (:target result)))))
        
        ;; Also convert .down.sql files
        (doseq [sqlite-file sqlite-files]
          (let [up-file (.getName sqlite-file)
                down-filename (str/replace up-file ".up.sql" ".down.sql")
                down-file (io/file (.getParent sqlite-file) down-filename)]
            (when (.exists down-file)
              (let [result (convert-migration-file down-file target-db)]
                (println (str "✓ Converted: " (:source result) " → " (:target result)))))))
        
        (println (str "\nConversion complete! " (* 2 (count sqlite-files)) " file(s) created."))
        (println "\nNext steps:")
        (println "1. Review the generated migration files")
        (println "2. Adjust any database-specific SQL if needed")
        (println "3. Run: lein migrate to apply migrations to your database")))))

(defn -main [& args]
  (apply convert-migrations args))
