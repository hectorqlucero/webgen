(ns {{sanitized}}.dev
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [{{sanitized}}.models.crud :refer [config]]
            [{{sanitized}}.engine.config :as entity-config]
            [{{sanitized}}.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private last-reload (atom 0))
(def ^:private last-hook-check (atom 0))
(def ^:private hooks-last-modified (atom {}))
(def ^:private validators-last-modified (atom {}))

(defn- get-base-ns
  "Gets the base namespace (project name) from the current namespace"
  []
  (-> (str *ns*)
      (str/split #"\.")
      first))

(defn- hooks-path
  "Returns the path to hooks directory for this project"
  []
  (str "src/" (get-base-ns) "/hooks"))

(defn- validators-path
  "Returns the path to validators directory for this project"
  []
  (str "src/" (get-base-ns) "/validators"))

(defn wrap-auto-reload-entities
  "Middleware that checks if entity EDN files or hook CLJ files have changed and reloads them.
   Checks every 2 seconds to avoid excessive file system checks."
  [handler]
  (fn [request]
    (let [now (System/currentTimeMillis)
          last @last-reload
          elapsed (- now last)]
      ;; Check every 2000ms (2 seconds)
      (when (> elapsed 2000)
        (try
          ;; Check EDN entity configs
          (let [entities-dir (io/resource "entities")]
            (when entities-dir
              (let [edn-files (filter #(.endsWith (.getName %) ".edn")
                                      (file-seq (io/file entities-dir)))
                    newest-mod (apply max (map #(.lastModified %) edn-files))]
                (when (> newest-mod last)
                  (println "[DEV] Entity configs changed, reloading...")
                  (entity-config/reload-all!)
                  (println "[DEV] ✓ Reloaded all entity configs")))))
          
          ;; Check hook files
          (let [hooks-dir (io/file (hooks-path))]
            (when (.exists hooks-dir)
              (let [hook-files (filter #(.endsWith (.getName %) ".clj")
                                       (file-seq hooks-dir))
                    changes (atom [])]
                (doseq [f hook-files]
                  (let [path (.getPath f)
                        current-mod (.lastModified f)
                        last-mod (@hooks-last-modified path)]
                    (when (or (nil? last-mod) (> current-mod last-mod))
                      (swap! changes conj (.getName f))
                      (swap! hooks-last-modified assoc path current-mod))))
                (when (seq @changes)
                  (println "[DEV] Hook files changed:" (clojure.string/join ", " @changes))
                  (println "[DEV] Reloading affected entities...")
                  (entity-config/reload-all!)
                  (println "[DEV] ✓ Reloaded all entity configs with fresh hooks")))))
          
          ;; Check validator files
          (let [validators-dir (io/file (validators-path))]
            (when (.exists validators-dir)
              (let [validator-files (filter #(.endsWith (.getName %) ".clj")
                                            (file-seq validators-dir))
                    changes (atom [])]
                (doseq [f validator-files]
                  (let [path (.getPath f)
                        current-mod (.lastModified f)
                        last-mod (@validators-last-modified path)]
                    (when (or (nil? last-mod) (> current-mod last-mod))
                      (swap! changes conj (.getName f))
                      (swap! validators-last-modified assoc path current-mod))))
                (when (seq @changes)
                  (println "[DEV] Validator files changed:" (clojure.string/join ", " @changes))
                  (println "[DEV] Reloading affected entities...")
                  (entity-config/reload-all!)
                  (println "[DEV] ✓ Reloaded all entity configs with fresh validators")))))
          
          (catch Exception e
            (println "[WARN] Auto-reload failed:" (.getMessage e))))
        (reset! last-reload now)))
    (handler request)))

(defn -main []
  (jetty/run-jetty 
   (-> #'core/app
       wrap-reload
       wrap-auto-reload-entities)
   {:port (:port config)}))
