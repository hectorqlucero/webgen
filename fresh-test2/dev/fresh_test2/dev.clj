(ns fresh_test2.dev
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [fresh_test2.models.crud :refer [config]]
            [fresh_test2.engine.config :as entity-config]
            [fresh_test2.core :as core]
            [clojure.java.io :as io]))

(def ^:private last-reload (atom 0))
(def ^:private last-hook-check (atom 0))
(def ^:private hooks-last-modified (atom {}))

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
          (let [hooks-dir (io/file "src/rs/hooks")]
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
