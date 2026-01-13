(ns fresh_test2.routes.i18n
  "Routes for language switching"
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [ring.util.response :refer [redirect]]
   [fresh_test2.i18n.core :as i18n]))

(defn handle-set-language
  "Sets the language in session and redirects back"
  [request]
  (let [locale (keyword (get-in request [:params :locale] "es"))
        referer (get-in request [:headers "referer"] "/")
        session (:session request)]
    (-> (redirect referer)
        (assoc :session (i18n/set-locale! session locale)))))

(defroutes i18n-routes
  (GET "/set-language/:locale" request
    (handle-set-language request))
  
  (POST "/set-language" request
    (handle-set-language request)))
