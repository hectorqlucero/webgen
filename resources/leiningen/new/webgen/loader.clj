(ns {{sanitized}}.config.loader
  "Centralized configuration loading and management"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn load-config
  "Load configuration from resources/config directory"
  [config-name]
  (try
    (some-> (io/resource (str "config/" config-name ".edn"))
            slurp
            edn/read-string)
    (catch Exception e
      (println "[ERROR] Failed to load config" config-name ":" (.getMessage e))
      nil)))

(defn get-app-config
  "Get app configuration section"
  []
  (or (load-config "app-config")
      {:app {:session-timeout 28800
             :default-locale :es
             :cookie-name "LS"
             :max-file-size-mb 5
             :grid-display-limit 7
             :pagination-size 25}
       :database {:error-codes {:postgres {:unique "23505" :fk "23503"}
                                :mysql {:unique 1062 :fk 1451}}
                  :connection-params {:mysql {:use-ssl false :server-timezone "UTC"}
                                      :postgres {:sslmode "disable"}}}
       :ui {:themes ["sketchy" "cerulean" "cosmo" "cyborg" "darkly"]
            :assets {:thumbnail-width 42 :thumbnail-height 32}
            :frontend {:css ["dataTables.bootstrap5.min.css" "bootstrap-icons.css"]
                       :js ["jquery-3.7.1.min.js" "jquery.dataTables.min.js"]}}
       :security {:csrf-token-name "anti-forgery-token"
                  :ajax-header "x-requested-with"
                  :session-secret-key "rs-session-key"}
       :routes {:login "/home/login"}
       :roles {:hierarchy ["S" "A" "U"]
               :labels {"S" "System" "A" "Administrator" "U" "User"}}}))

(defn get-messages-config
  "Get messages configuration"
  []
  (or (load-config "messages")
      {:errors {:database {:unique {:en "Duplicate value for field" :es "Valor duplicado para el campo"}
                           :foreign-key {:en "Foreign key constraint violation" :es "Violación de restricción de clave foránea"}
                           :not-null {:en "Required field is missing" :es "Campo requerido está ausente"}
                           :general {:en "Invalid data" :es "Datos inválidos"}}
                :security {:csrf {:en "Invalid or missing CSRF token" :es "Token CSRF inválido o faltante"}}}}))

(defn get-private-config
  "Get private configuration"
  []
  (try
    (some-> (io/resource "private/config.clj")
            slurp
            read-string)
    (catch Exception _ {})))

;; Cache all configurations
(def ^:private config-cache (atom nil))

(defn get-all-configs
  "Get all configurations with caching"
  []
  (or @config-cache
      (reset! config-cache {:app-config (get-app-config)
                            :messages (get-messages-config)
                            :private-config (get-private-config)})))

(defn reload-config!
  "Force reload of all configurations"
  []
  (reset! config-cache nil)
  (get-all-configs))

(defn app-config
  "Get app configuration section.
   Accepts either a single key (keyword/string) or a vector of keys to navigate
   inside the :app map. Examples:
   (app-config :pagination-size)
   (app-config [:ui :assets :thumbnail-width])"
  [path & [default]]
  (let [ks (if (sequential? path) path [path])]
    (get-in (:app-config (get-all-configs)) (into [:app] ks) default)))

(defn messages
  "Get messages configuration"
  [path & [default]]
  (get-in (:messages (get-all-configs)) path default))

(defn private-config
  "Get private configuration"
  [path & [default]]
  (get-in (:private-config (get-all-configs)) path default))

(defn get-db-error-codes
  "Get database error codes for specific database type"
  [db-type]
  (get-in (get-all-configs) [:app-config :database :error-codes db-type]))

(defn get-db-connection-params
  "Get database connection parameters for specific database type"
  [db-type]
  (get-in (get-all-configs) [:app-config :database :connection-params db-type]))

(defn get-error-message
  "Get localized error message"
  [error-type error-key locale]
  (get-in (get-all-configs) [:messages error-type error-key locale]
          (get-in (get-all-configs) [:messages error-type error-key :en])))
