(ns {{sanitized}}.dev
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [{{sanitized}}.models.crud :refer [config]]
            [{{sanitized}}.engine.config :as entity-config]
            [{{sanitized}}.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private reload-state
  (atom {:last-check 0
         :src {}
         :entities-last-mod 0}))

(defn- get-base-ns []
  (-> (str *ns*) (str/split #"\.") first))

(defn- src-path []     (str "src/" (get-base-ns) "/"))

(defn- reload-ns! [ns-sym label]
  (try
    (require ns-sym :reload-all)
    (println "[DEV] ✓ Reloaded" label ":" ns-sym)
    (catch Exception e
      (println "[WARN] Failed to reload" label ":" ns-sym)
      (.printStackTrace e))))

(defn- check-directory-changes
  "Checks a directory for modified .clj files."
  [dir-path state-key label]
  (let [dir (io/file dir-path)]
    (when (.exists dir)
      (let [files (filter #(and (.isFile %) (.endsWith (.getName %) ".clj"))
                          (file-seq dir))
            changes (atom [])]
        (doseq [f files]
          (let [path (.getPath f)
                mtime (.lastModified f)
                last-mtime (get (@reload-state state-key) path)]
            (when (or (nil? last-mtime) (> mtime last-mtime))
              (swap! reload-state assoc-in [state-key path] mtime)
              (swap! changes conj (.getName f)))))
        @changes))))

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
  "Development middleware for src files and entities."
  [handler]
  (fn [request]
    (let [now (System/currentTimeMillis)
          last-check (:last-check @reload-state)]
      (when (> (- now last-check) 2000)
        (try
           ;; Reload all namespaces under src/
          (doseq [rel-path (check-directory-changes (src-path) :src "source file")]
            (let [ns-sym (-> rel-path
                             (str/replace #"\.clj$" "")
                             (str/replace #"/" ".")
                             (symbol (str (get-base-ns) ".")))]
              (reload-ns! ns-sym "source file")))

           ;; Reload entities if EDN files changed
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
       wrap-reload
       wrap-auto-reload)
   {:port (:port config)
    :join? false}))
