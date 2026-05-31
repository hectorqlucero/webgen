(ns {{sanitized}}.models.cdb
  (:require
   [clojure.string :as st]
   [buddy.hashers :as hashers]
   [{{sanitized}}.models.crud :as crud :refer [Insert-multi Query!]]))

(def users-rows
  [{:lastname  "User"
    :firstname "Regular"
    :username  "user@example.com"
    :password  (hashers/derive "user")
    :dob       "1957-02-07"
    :email     "user@example.com"
    :level     "U"
    :active    "T"}
   {:lastname "User"
    :firstname "Admin"
    :username "admin@example.com"
    :password (hashers/derive "admin")
    :dob "1957-02-07"
    :email "admin@example.com"
    :level "A"
    :active "T"}
   {:lastname "User"
    :firstname "System"
    :username "system@example.com"
    :password (hashers/derive "system")
    :dob "1957-02-07"
    :email "system@example.com"
    :level "S"
    :active "T"}])

(def organizations-rows
  [{:id 1 :name "Acme Corp" :code "ACME" :active "T"}
   {:id 2 :name "Globex" :code "GLOB" :active "T"}])

(def departments-rows
  [{:id 1 :organization_id 1 :name "Engineering" :code "ENG"}
   {:id 2 :organization_id 1 :name "Operations" :code "OPS"}
   {:id 3 :organization_id 2 :name "Sales" :code "SAL"}])

(def employees-rows
  [{:id 1 :department_id 1 :manager_id nil :first_name "Alice" :last_name "Johnson" :email "alice@acme.test" :active "T"}
   {:id 2 :department_id 1 :manager_id 1 :first_name "Bob" :last_name "Rivera" :email "bob@acme.test" :active "T"}
   {:id 3 :department_id 2 :manager_id 1 :first_name "Carol" :last_name "Ng" :email "carol@acme.test" :active "T"}
   {:id 4 :department_id 3 :manager_id nil :first_name "David" :last_name "Kim" :email "david@globex.test" :active "T"}])

(def projects-rows
  [{:id 1 :project_code "PJT-100" :name "ERP Modernization" :starts_on "2024-01-15" :ends_on "2024-12-20"}
   {:id 2 :project_code "PJT-200" :name "Customer Portal" :starts_on "2024-03-01" :ends_on "2024-11-30"}])

(def skills-rows
  [{:id 1 :name "Clojure" :category "Backend"}
   {:id 2 :name "SQL" :category "Data"}
   {:id 3 :name "Project Management" :category "Management"}])

(def employee-profiles-rows
  [{:id 1 :employee_id 1 :bio "Engineering manager" :avatar nil :emergency_phone "555-1001"}
   {:id 2 :employee_id 2 :bio "Senior developer" :avatar nil :emergency_phone "555-1002"}
   {:id 3 :employee_id 3 :bio "Operations specialist" :avatar nil :emergency_phone "555-1003"}
   {:id 4 :employee_id 4 :bio "Sales lead" :avatar nil :emergency_phone "555-1004"}])

(def employee-projects-rows
  [{:id 1 :employee_id 1 :project_id 1 :role "Lead" :hours_per_week 20 :assigned_on "2024-01-20"}
   {:id 2 :employee_id 2 :project_id 1 :role "Developer" :hours_per_week 35 :assigned_on "2024-01-22"}
   {:id 3 :employee_id 3 :project_id 2 :role "Coordinator" :hours_per_week 15 :assigned_on "2024-03-10"}
   {:id 4 :employee_id 4 :project_id 2 :role "Analyst" :hours_per_week 25 :assigned_on "2024-03-12"}])

(def employee-skills-rows
  [{:employee_id 1 :skill_id 3 :proficiency 5}
   {:employee_id 2 :skill_id 1 :proficiency 5}
   {:employee_id 2 :skill_id 2 :proficiency 4}
   {:employee_id 3 :skill_id 2 :proficiency 4}
   {:employee_id 4 :skill_id 3 :proficiency 4}])

(def ^:private first-names
  ["Juan" "Maria" "Luis" "Ana" "Carlos" "Sofia" "Pedro" "Laura" "Diego" "Valentina"
   "Andres" "Camila" "Jorge" "Isabella" "Miguel" "Gabriela" "Fernando" "Lucia"
   "Alejandro" "Elena" "Pablo" "Mariana" "Sergio" "Daniela" "Ricardo" "Rosa"
   "Alberto" "Carmen" "Rafael" "Patricia" "Hector" "Monica" "Enrique" "Teresa"
   "Oscar" "Claudia" "Arturo" "Leticia" "Raul" "Veronica"])

(def ^:private last-names
  ["Garcia" "Rodriguez" "Martinez" "Hernandez" "Lopez" "Gonzalez" "Perez" "Sanchez"
   "Ramirez" "Torres" "Flores" "Rivera" "Gomez" "Diaz" "Cruz" "Morales" "Ortiz"
   "Reyes" "Vazquez" "Ramos" "Jimenez" "Moreno" "Romero" "Alvarez" "Castillo"
   "Medina" "Acosta" "Delgado" "Chavez" "Santiago" "Cardenas" "Padilla" "Rios"
   "Mendoza" "Guerrero" "Pena" "Aguilar" "Cano" "Vega" "Campos"])

(def ^:private car-companies
  ["Toyota" "Honda" "Ford" "Chevrolet" "Nissan" "Volkswagen" "Hyundai" "Kia"
   "Mazda" "Subaru" "BMW" "Mercedes-Benz" "Audi" "Lexus" "Tesla"])

(def ^:private car-models
  {"Toyota" ["Corolla" "Camry" "RAV4" "Tacoma" "Highlander" "Prius"]
   "Honda" ["Civic" "Accord" "CR-V" "Pilot" "Fit" "HR-V"]
   "Ford" ["Mustang" "F-150" "Explorer" "Escape" "Focus" "Ranger"]
   "Chevrolet" ["Silverado" "Equinox" "Malibu" "Tahoe" "Camaro" "Traverse"]
   "Nissan" ["Altima" "Sentra" "Rogue" "Frontier" "Pathfinder" "Versa"]
   "Volkswagen" ["Jetta" "Passat" "Tiguan" "Golf" "Atlas" "Beetle"]
   "Hyundai" ["Elantra" "Sonata" "Tucson" "Santa Fe" "Kona" "Palisade"]
   "Kia" ["Forte" "Sportage" "Sorento" "Telluride" "Soul" "Rio"]
   "Mazda" ["Mazda3" "CX-5" "CX-9" "MX-5 Miata" "CX-30" "Mazda6" "CX-50" "MX-30" "CX-90"]
   "Subaru" ["Outback" "Forester" "Crosstrek" "Legacy" "Impreza" "Ascent"]
   "BMW" ["3 Series" "5 Series" "X3" "X5" "7 Series" "M4"]
   "Mercedes-Benz" ["C-Class" "E-Class" "GLC" "GLE" "A-Class" "S-Class"]
   "Audi" ["A3" "A4" "Q5" "Q7" "A6" "e-tron"]
   "Lexus" ["RX" "ES" "NX" "IS" "GX" "UX"]
   "Tesla" ["Model 3" "Model Y" "Model S" "Model X" "Cybertruck"]
   "Mini" ["Cooper" "Countryman" "Clubman" "Convertible"]
   "Jeep" ["Wrangler" "Grand Cherokee" "Cherokee" "Compass" "Renegade"]
   "Volvo" ["XC40" "XC60" "XC90" "S60" "S90"]
   "Ram" ["1500" "2500" "ProMaster"]})

(defn- random-contactos
  [n start-id]
  (let [rng (java.util.Random. 42)]
    (mapv (fn [i]
            (let [first-name (nth first-names (mod i (count first-names)))
                  last-name  (nth last-names (mod (* i 7) (count last-names)))]
              {:id i
               :name (str first-name " " last-name)
               :email (str (st/lower-case first-name) "." (st/lower-case last-name) i "@example.com")
               :phone (str "555-" (format "%04d" (+ 3000 i)))
               :imagen nil}))
          (range start-id (inc n)))))

(defn- random-siblings
  [contactos-rows]
  (let [rng (java.util.Random. 123)
        nombres-hijos ["Ana" "Jose" "Elena" "Carlos" "Sofia" "Miguel" "Laura"
                       "Diego" "Camila" "Andres" "Valentina" "Fernando" "Lucia"
                       "Gabriela" "Pablo" "Mariana" "Sergio" "Daniela" "Ricardo"
                       "Rosa" "Hector" "Monica" "Alberto" "Pedro" "Rafael"]]
    (->> contactos-rows
         (mapcat (fn [contacto]
                   (let [n-sibs (mod (.nextInt rng) 4)] ;; 0-3 siblings
                     (mapv (fn [j]
                             (let [sib-name (str (nth nombres-hijos
                                                      (mod (+ (* (:id contacto) 7) j)
                                                           (count nombres-hijos)))
                                                 " "
                                                 (last (re-find #"^(\S+)\s+(\S+)" (:name contacto))))]
                               {:name sib-name
                                :age (+ 15 (mod (.nextInt rng) 35))
                                :imagen nil
                                :contacto_id (:id contacto)}))
                           (range n-sibs)))))
         (map-indexed (fn [idx m] (assoc m :id (inc idx))))
         (vec))))

(defn- random-cars
  [contactos-rows]
  (let [rng (java.util.Random. 456)]
    (->> contactos-rows
         (mapcat (fn [contacto]
                   (let [n-cars (mod (.nextInt rng) 3)] ;; 0-2 cars
                     (mapv (fn [j]
                             (let [company (nth car-companies
                                                (mod (+ (* (:id contacto) 13) j)
                                                     (count car-companies)))
                                   models (get car-models company (car-models "Toyota"))]
                               {:company company
                                :model (nth models (mod (+ (* (:id contacto) 3) j) (count models)))
                                :year (+ 2015 (mod (.nextInt rng) 10))
                                :imagen nil
                                :contacto_id (:id contacto)}))
                           (range n-cars)))))
         (map-indexed (fn [idx m] (assoc m :id (inc idx))))
         (vec))))

(def contactos-rows (random-contactos 30 1))
(def siblings-rows (random-siblings contactos-rows))
(def cars-rows (random-cars contactos-rows))

(def audit-log-rows
  [{:id 1 :entity "employees" :operation "seed" :data "initial dataset" :user_id 1 :timestamp "2024-01-01 10:00:00"}
   {:id 2 :entity "projects" :operation "seed" :data "initial dataset" :user_id 1 :timestamp "2024-01-01 10:01:00"}
   {:id 3 :entity "contactos" :operation "seed" :data "initial dataset" :user_id 1 :timestamp "2024-01-01 10:02:00"}])

(def ^:private non-users-seed-plan
  [{:table "organizations" :rows organizations-rows}
   {:table "departments" :rows departments-rows}
   {:table "employees" :rows employees-rows}
   {:table "projects" :rows projects-rows}
   {:table "skills" :rows skills-rows}
   {:table "employee_profiles" :rows employee-profiles-rows}
   {:table "employee_projects" :rows employee-projects-rows}
   {:table "employee_skills" :rows employee-skills-rows}
   {:table "contactos" :rows contactos-rows}
   {:table "cars" :rows cars-rows}
   {:table "siblings" :rows siblings-rows}
   {:table "audit_log" :rows audit-log-rows}])

(def ^:private non-users-clear-order
  ["employee_skills"
   "employee_projects"
   "employee_profiles"
   "employees"
   "departments"
   "organizations"
   "cars"
   "siblings"
   "contactos"
   "projects"
   "skills"
   "audit_log"])


(defn- normalize-token [s]
  (some-> s str st/trim (st/replace #"^:+" "") st/lower-case))

(def ^:private vendor->subprotocol
  {"mysql"     #(or (= % "mysql") (= % :mysql))
   "postgres"  #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "postgresql" #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "pg"        #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "sqlite"    #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))
   "sqlite3"   #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))})

(defn- choose-conn-key
  "Resolve a user token (e.g., nil, pg, :pg, localdb, mysql) to a key in crud/dbs.
  Prefers exact connection keys (e.g., :pg, :localdb, :main, :default). Falls back to
  the first connection whose subprotocol matches a known vendor token. Defaults to :default."
  [token]
  (let [t (normalize-token token)
        dbs crud/dbs
        keys* (set (keys dbs))
        ;; map some common nicknames directly to configured keys
        t->key {"default" :default
                "mysql"   :default   ; assume default is mysql per config
                "main"    :main
                "pg"      :pg
                "postgres" :pg
                "postgresql" :pg
                "local"   :localdb
                "localdb" :localdb
                "sqlite"  :localdb
                "sqlite3" :localdb}
        direct (when (seq t)
                 (some (fn [k] (when (= (name k) t) k)) keys*))
        mapped (get t->key t)
        by-vendor (when (seq t)
                    (let [pred (get vendor->subprotocol t)]
                      (when pred
                        (some (fn [[k v]] (when (pred (:subprotocol v)) k)) dbs))))]
    (or direct mapped by-vendor :default)))

(defn populate-tables
  "Populate a table with rows on the selected connection. This version avoids vendor-specific
  locking and uses simple DELETE + batch insert wrapped in a transaction by Insert-multi."
  [table rows & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))
        ;; coerce row values to DB-appropriate types using schema introspection
        typed-rows (mapv (fn [row]
                           (crud/build-postvars table-s row :conn conn*))
                         rows)]
    (println (format "[database] Seeding %s on connection %s" table-s (name conn*)))
    (try
      ;; Clear existing rows (portable across MySQL/Postgres/SQLite)
      (Query! (str "DELETE FROM " table-s) :conn conn*)
      ;; Batch insert rows
      (Insert-multi (keyword table-s) typed-rows :conn conn*)
      (println (format "[database] Seeded %d rows into %s (%s)"
                       (count typed-rows) table-s (name conn*)))
      (catch Exception e
        (println "[ERROR] Seeding failed for" table-s "on" (name conn*) ":" (.getMessage e))
        (throw e)))))

(defn- clear-table
  [table & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))]
    (Query! (str "DELETE FROM " table-s) :conn conn*)
    (println (format "[database] Cleared %s (%s)" table-s (name conn*)))))

(defn- insert-rows
  [table rows & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))
        typed-rows (mapv (fn [row]
                           (crud/build-postvars table-s row :conn conn*))
                         rows)]
    (when (seq typed-rows)
      (Insert-multi (keyword table-s) typed-rows :conn conn*)
      (println (format "[database] Seeded %d rows into %s (%s)"
                       (count typed-rows) table-s (name conn*))))))

(defn seed-non-users
  "Usage:
   - lein run -m contactos.models.cdb/seed-non-users
   - lein run -m contactos.models.cdb/seed-non-users pg
   - lein run -m contactos.models.cdb/seed-non-users localdb

   Seeds all configured tables except users."
  [& args]
  (let [token (first args)
        conn  (choose-conn-key token)
        dbspec (get crud/dbs conn)
        sp (:subprotocol dbspec)]
    (println (format "[database] Seeding non-user tables on connection: %s (subprotocol=%s)" (name conn) sp))
    ;; Clear child tables first to avoid FK violations.
    (doseq [table non-users-clear-order]
      (clear-table table :conn conn))
    ;; Insert parent tables first, then dependent/link tables.
    (doseq [{:keys [table rows]} non-users-seed-plan]
      (insert-rows table rows :conn conn))
    (println "[database] Non-user seed completed.")))

(defn database
  "Usage:
   - lein database                 ; seeds default (mysql per config)
   - lein database pg              ; seeds Postgres (:pg)
   - lein database :pg             ; same as above
   - lein database localdb         ; seeds SQLite (:localdb)"
  [& args]
  (let [token (first args)
        conn  (choose-conn-key token)
        dbspec (get crud/dbs conn)
        sp (:subprotocol dbspec)]
    (println (format "[database] Using connection: %s (subprotocol=%s)" (name conn) sp))
    ;; add other tables here if needed
    (populate-tables "users" users-rows :conn conn)
    (println "[database] Done.")))