;; WebGen Configuration File
;; This file uses template variables that will be replaced when you create a new project

{:connections
 {;; --- MySQL Database ---
  :mysql {:db-type   "mysql"
          :db-class  "com.mysql.cj.jdbc.Driver"
          :db-name   "//localhost:3306/{{name}}"
          :db-user   "root"
          :db-pwd    "your_password"}

  ;; --- Local SQLite Database (Great for development) ---
  :sqlite {:db-type   "sqlite"
           :db-class  "org.sqlite.JDBC"
           :db-name   "db/{{name}}.sqlite"}

  ;; --- PostgreSQL Database ---
  :postgres {:db-type   "postgresql"
             :db-class  "org.postgresql.Driver"
             :db-name   "//localhost:5432/{{name}}"
             :db-user   "postgres"
             :db-pwd    "your_password"}

  ;; --- Default connection used by the app ---
  ;; SQLite by default for easy prototyping, switch to MySQL/PostgreSQL for production
  :main :sqlite        ; Used for migrations (lein migrate)
  :default :sqlite     ; Used by the application
  :db :mysql           ; MySQL connection reference
  :pg :postgres        ; PostgreSQL connection reference
  :localdb :sqlite}    ; SQLite connection reference

 ;; --- Application Settings ---
 :uploads      "./uploads/{{name}}/"
 :site-name    "{{name}}"
 :company-name "Your Company"
 :port         8080
 :tz           "US/Pacific"
 :base-url     "http://localhost:8080/"
 :img-url      "http://localhost:8080/uploads/"
 :path         "/uploads/"
 
 ;; --- File Upload Settings ---
 :max-upload-mb 5
 :allowed-image-exts ["jpg" "jpeg" "png" "gif" "bmp" "webp"]
 
 ;; --- Theme Selection ---
 ;; Options: "default" (Bootstrap), "cerulean", "slate", "minty", "lux", "cyborg", 
 ;;          "sandstone", "superhero", "flatly", "yeti", "sketchy"
 :theme "sketchy"
 
 ;; --- Optional Email Configuration ---
 ;; Uncomment and configure if you need email functionality
 ;; :email-host   "smtp.example.com"
 ;; :email-user   "user@example.com"
 ;; :email-pwd    "your_password"
 ;; :email-port   587
 ;; :email-tls    true
}
