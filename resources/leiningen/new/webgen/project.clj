(defproject {{name}} "0.1.0"
  :description "{{name}}"
  :url "http://example.com/FIXME" ; Change me - optional
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [org.clojure/data.csv "1.1.1"]
                 [org.clojure/data.json "2.5.2"]
                 [org.slf4j/slf4j-simple "2.0.18"]
                 [compojure "1.7.2"]
                 [hiccup "2.0.0"]
                 [buddy/buddy-hashers "2.0.167"]
                 [com.draines/postal "2.0.5"]
                 [cheshire "6.2.0"]
                 [clj-pdf "2.7.4"]
                 [ondrs/barcode "0.1.0"]
                 [pdfkit-clj "0.1.7"]
                 [cljfmt "0.9.2"]
                 [clj-jwt "0.1.1"]
                 [clj-time "0.15.2"]
                 [date-clj "1.0.1"]
                 [org.clojure/java.jdbc "0.7.12"]
                 ;; Active JDBC drivers (MySQL, PostgreSQL, SQLite)
                 [mysql/mysql-connector-java "8.0.33"]
                 [org.postgresql/postgresql "42.7.11"]
                 [org.xerial/sqlite-jdbc "3.53.1.0"]
                 ;; Optional JDBC drivers (uncomment if needed)
                 ;; [com.microsoft.sqlserver/mssql-jdbc "12.8.1.jre11"]   ; SQL Server
                 ;; [com.h2database/h2 "2.2.224"]                        ; H2
                 ;; [com.oracle.database.jdbc/ojdbc8 "21.11.0.0"]        ; Oracle (check licensing)
                 [ragtime "0.8.1"]
                 [ring/ring-core "1.15.4"]
                 [ring/ring-jetty-adapter "1.15.4"]
                 [ring/ring-defaults "0.7.0"]
                 [ring/ring-devel "1.15.4"]
                 [ring/ring-codec "1.3.0"]]
  :main {{sanitized}}.core
  :plugins [[lein-ancient "0.7.0"]
            [lein-pprint "1.3.2"]]
  :uberjar-name "{{sanitized}}.jar"
  :target-path "target/%s"
  :ring {:handler {{sanitized}}.core
         :auto-reload? true
         :auto-refresh? false}
  :resource-paths ["shared" "resources"]
  :aliases {"migrate"  ["run" "-m" "{{sanitized}}.migrations/migrate" "--"]
            "rollback" ["run" "-m" "{{sanitized}}.migrations/rollback" "--"]
            ;; Forward any extra args to the seeder fn, e.g.:
            ;;   lein database          ; default (mysql)
            ;;   lein database pg       ; postgres (:pg)
            ;;   lein database :pg      ; postgres (:pg)
            ;;   lein database localdb  ; sqlite (:localdb)
            "database" ["run" "-m" "{{sanitized}}.models.cdb/database" "--"]
            "scaffold" ["run" "-m" "{{sanitized}}.engine.scaffold"]
            ;; Convert SQLite migrations to MySQL/PostgreSQL
            ;;   lein convert-migrations mysql        ; default (mysql)
            ;;   lein convert-migrations postgresql   ; postgres
            "convert-migrations" ["run" "-m" "{{sanitized}}.db.converter" "--"]
            ;; Copy data between databases
            ;;   lein copy-data localdb mysql          ; SQLite → MySQL
            ;;   lein copy-data mysql localdb          ; MySQL → SQLite
            ;;   lein copy-data localdb postgresql     ; SQLite → PostgreSQL
            ;;   lein copy-data mysql postgresql       ; MySQL → PostgreSQL
            ;;   lein copy-data localdb mysql --clear  ; clear target tables first
            ;;   lein copy-data mysql                  ; MySQL → SQLite (default target)
            "copy-data" ["run" "-m" "{{sanitized}}.db.migrator" "--"]
            ;; Generate/remove handler skeleton (controller, model, view)
            ;;   lein gen-handler reports          ; create
            ;;   lein gen-handler reports remove   ; remove
            "gen-handler" ["run" "-m" "{{sanitized}}.gen.handler" "--"]}
  :profiles {:uberjar {:aot :all
                       :main {{sanitized}}.core
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:source-paths ["src" "dev"]
                   :main {{sanitized}} .dev}})
