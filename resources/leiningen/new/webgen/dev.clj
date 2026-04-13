(ns {{sanitized}}.dev
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [{{sanitized}}.models.crud :refer [config]]
            [{{sanitized}}.engine.config :as entity-config]
            [{{sanitized}}.core :as core]
            [clojure.java.io :as io]))

(def ^:private reload-state
  (atom {:last-check 0
         :entities-last-mod 0}))

(defn- entities-changed? []
  (when-let [dir (io/resource "entities")]
    (let [edn-files (filter #(-> % .getName (.endsWith ".edn"))
                            (file-seq (io/file dir)))]
      (when (seq edn-files)
        (let [newest-mod (apply max (map #(.lastModified %) edn-files))
              last-mod  (:entities-last-mod @reload-state 0)]
          (when (> newest-mod last-mod)
            (swap! reload-state assoc :entities-last-mod newest-mod)
            true))))))

(defn wrap-auto-reload
  "Development middleware for entity config hot-reloading."
  [handler]
  (fn [request]
    (let [now (System/currentTimeMillis)
          last-check (:last-check @reload-state)]
      (when (> (- now last-check) 2000)
        (try
          (when (entities-changed?)
            (println "[DEV] Entity configs changed, reloading...")
            (entity-config/reload-all!)
            (println "[DEV] ✓ Reloaded all entity configs"))
          (catch Exception e
            (println "[WARN] Auto-reload failed:" (.getMessage e))))
        (swap! reload-state assoc :last-check now)))
    (handler request)))

(defn -main []
  (jetty/run-jetty
   (-> #'core/app
       (wrap-reload {:reload-compile-errors? false})
       wrap-auto-reload)
   {:port (:port config)
    :join? false}))
