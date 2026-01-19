(ns {{sanitized}}.scaffold.enhancement
  "Auto-TabGrid enhancement for existing scaffold system")

(defn should-use-tabgrid
  "Auto-detect if entity should use TabGrid UI"
  [entity-config]
  (and (seq (:subgrids entity-config))
       (:mode entity-config)))

(defn auto-enhance-entity
  "Auto-enhance entity configuration for TabGrid"
  [entity-config]
  (if (should-use-tabgrid entity-config)
    (-> entity-config
        (assoc :tabgrid-ui true :enhanced-tabgrid true)
        (update-in [:ui] merge {:split-view true :breadcrumbs true}))
    entity-config))

(defn check-tabgrid-status
  "Check TabGrid status for all entities"
  []
  (doseq [entity [:propiedades :alquileres :clientes :agentes]]
    (let [config (load-entity-config entity)]
      (when config
        (println (str (name entity) ": " 
                     (if (should-use-tabgrid config) 
                       "TabGrid UI" 
                       "Regular Grid UI")))))))

(defn get-tabgrid-entities
  "Get list of entities that will use TabGrid UI"
  []
  (filter should-use-tabgrid 
          (map load-entity-config [:propiedades :alquileres :clientes :agentes])))

(defn integrate-tabgrid-into-scaffold
  "One-call integration with existing scaffold system"
  []
  {:integrated true
   :tabgrid-entities (get-tabgrid-entities)
   :changes-required false})

(defn load-entity-config
  "Load entity configuration"
  [entity-keyword]
  (try
    (require 'clojure.edn :as edn)
    (edn/read-string (slurp (str "resources/entities/" (name entity-keyword) ".edn")))
    (catch Exception e
      (println (str "Error loading " (name entity-keyword) ":" (.getMessage e)))
      {})))

(defn test-enhancement
  "Test the enhancement system"
  []
  (check-tabgrid-status)
  (let [result (integrate-tabgrid-into-scaffold)]
    (println (str "Result: " result))))
