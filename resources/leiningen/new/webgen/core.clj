(ns {{sanitized}}.core
  (:require
   [compojure.core :refer [routes]]
   [compojure.route :as route]
   [{{sanitized}}.models.crud :refer [config KEY]]
   [{{sanitized}}.routes.proutes :refer [proutes]]
   [{{sanitized}}.routes.routes :refer [open-routes]]
   [{{sanitized}}.routes.i18n :refer [i18n-routes]]
   [{{sanitized}}.routes.tabgrid :refer [tabgrid-routes]]
   [{{sanitized}}.engine.router :as engine]
   [{{sanitized}}.config.loader :as cfg]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response :refer [redirect]])
  (:gen-class))

;; Middleware for handling login
(defn wrap-login
  [handler]
  (fn [request]
    (if (nil? (get-in request [:session :user_id]))
      (redirect "home/login")
      (handler request))))

;; Middleware for handling exceptions
(defn wrap-exception-handling
  [handler]
  (letfn [(ajax? [req]
            (= "XMLHttpRequest" (get-in req [:headers "x-requested-with"])))
          (root-cause [^Throwable t]
            (loop [c t]
              (if-let [cause (.getCause c)]
                (recur cause)
                c)))
          ;; Attempt to extract duplicate key details (field/value/constraint)
          (dup-details [^String msg]
            (let [m (or msg "")]
              (or
               ;; MySQL: Extract value and key in two steps for robustness
               (when-let [[_ v] (re-find #"Duplicate entry '([^']+)'" m)]
                 (when-let [[_ k] (re-find #"for key '([^']+)'" m)]
                   (let [field (-> k (str/replace #".*\." "") (str/replace #"_UNIQUE$" ""))]
                     {:field field :value v :constraint k})))
               ;; Postgres detail (often present): Key (column)=(value) already exists.
               (when-let [[_ f v] (re-find #"Key \(([^\)]+)\)=\(([^\)]+)\) already exists" m)]
                 {:field f :value v})
               ;; SQLite: UNIQUE constraint failed: table.column
               (when-let [[_ qualified] (re-find #"UNIQUE constraint failed: ([^\s]+)" m)]
                 (let [field (last (str/split qualified #"\\."))]
                   {:field field})))))
          (sql-state [^Throwable t]
            (when (instance? java.sql.SQLException t)
              (.getSQLState ^java.sql.SQLException t)))
          (sql-code [^Throwable t]
            (when (instance? java.sql.SQLException t)
              (.getErrorCode ^java.sql.SQLException t)))
          (msg-has? [^String msg re]
            (boolean (re-find re (or msg ""))))
          ;; Configurable cross-DB classification
          (classify-sql [^Throwable rc ^String msg]
            (let [state (sql-state rc)
                  code  (sql-code rc)
                  mysql-codes (cfg/get-db-error-codes :mysql)
                  postgres-codes (cfg/get-db-error-codes :postgres)
                  sqlite-codes (cfg/get-db-error-codes :sqlite)]
              (cond
                ;; Unique violations
                (= state (:unique postgres-codes)) :unique
                (= code (:unique mysql-codes)) :unique
                (msg-has? msg (re-pattern (:unique sqlite-codes))) :unique

                ;; Foreign key
                (= state (:fk postgres-codes)) :fk
                (or (= code (:fk mysql-codes)) (= code 1452)) :fk
                (msg-has? msg (re-pattern (:fk sqlite-codes))) :fk

                ;; Not null
                (= state (:not-null postgres-codes)) :not-null
                (= code (:not-null mysql-codes)) :not-null
                (msg-has? msg (re-pattern (:not-null sqlite-codes))) :not-null

                ;; Check constraint
                (= state (:check postgres-codes)) :check

                ;; Data too long / truncation
                (= state (:too-long postgres-codes)) :too-long
                (= code (:too-long mysql-codes)) :too-long
                (msg-has? msg (re-pattern (:too-long sqlite-codes))) :too-long

                :else :other-sql)))]
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (let [rc (root-cause e)
                exd (ex-data e)
                csrf? (true? (:invalid-anti-forgery-token exd))
                sql? (instance? java.sql.SQLException rc)
                msg (.getMessage rc)
                kind (when sql? (classify-sql rc msg))
                dd (when (= kind :unique) (dup-details msg))
;; Decide status and friendly message (with localization support)
                 [status plain] (cond
                                  csrf? [403 (cfg/get-error-message :security :csrf :es)]
                                  (= kind :unique) [409 (if-let [f (:field dd)] 
                                                          (str (cfg/get-error-message :database :unique :es) " " f)
                                                          (cfg/get-error-message :database :unique :es))]
                                  (= kind :fk)     [409 (cfg/get-error-message :database :foreign-key :es)]
                                  (= kind :not-null) [422 (cfg/get-error-message :database :not-null :es)]
                                  (= kind :check)  [422 (cfg/get-error-message :database :check :es)]
                                  (= kind :too-long) [422 (cfg/get-error-message :database :too-long :es)]
                                  sql? [400 (cfg/get-error-message :database :general :es)]
                                  :else [400 (cfg/get-error-message :database :general :es)])
                body-json (let [base {:ok false :error plain}
                                base (if dd (merge base dd) base)]
                            (json/write-str base))]
            (try
              (println "[ERROR]" (.getName (class rc)) "-" msg
                       "->" (name (:request-method request)) (:uri request))
              (.printStackTrace rc)
              (catch Throwable _))
            (if (ajax? request)
              {:status status :headers {"Content-Type" "application/json"} :body body-json}
              {:status status :body plain})))))))

;; Middleware to wrap public and private routes
(defn wrap-routes
  [route-fn]
  (fn [routes]
    (route-fn routes)))

;; Define the application routes dynamically
;; NOTE: Route order matters; more specific routes should come before generic ones.
(def app-routes
  (fn []
    (routes
     (route/resources "/")
     (route/files (:path config) {:root (:uploads config)})
     (wrap-routes open-routes)
     ;; I18n language switching routes
     (wrap-routes i18n-routes)
     ;; TabGrid AJAX routes
     (wrap-login (wrap-routes tabgrid-routes))
     ;; Parameter-driven engine routes (NEW)
     (wrap-login (wrap-routes (engine/get-routes)))
     ;; Legacy generated routes (for backward compatibility)
     (wrap-login (wrap-routes proutes))
     (route/not-found "Not Found"))))

;; Ensure the uploads directory (and parents) exist, based on config
(defn ensure-upload-dirs! []
  (try
    (when-let [p (:uploads config)]
      (let [f (io/file (str p))]
        (when-not (.exists f)
          (.mkdirs f))))
    (catch Throwable _)))

;; Application configuration
;; The order of middleware matters: defaults/multipart first, exception handling outermost.
(defn create-app
  []
(-> (app-routes)
      (wrap-multipart-params)
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] true)
                         (assoc-in [:session :store] (cookie-store {:key KEY}))
                         (assoc-in [:session :cookie-attrs] {:max-age 28800})
                         (assoc-in [:session :cookie-name] "LS")))
      (wrap-exception-handling)))

(def app
  (reify
    clojure.lang.IDeref
    (deref [_] (create-app))
    clojure.lang.IFn
    (invoke [_ request] ((create-app) request))
    (applyTo [_ args] (apply (create-app) args))))

;; Main entry point
(defn -main
  []
  (ensure-upload-dirs!)
  (jetty/run-jetty app {:port (:port config)}))

(comment
  (:port config))
