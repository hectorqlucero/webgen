(ns {{sanitized}}.handlers.home.controller
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as st]
   [{{sanitized}}.i18n.core :as i18n]
   [{{sanitized}}.handlers.home.model :refer [get-user get-users update-password]]
   [{{sanitized}}.handlers.home.view :refer [change-password-view home-view
                                         main-view]]
   [{{sanitized}}.layout :refer [application]]
   [{{sanitized}}.models.util :refer [get-session-id]]
   [ring.util.response :refer [redirect]]))

(defn main
  [request]
  (let [title "Home"
        ok (get-session-id request)
        js nil
        content (if (> ok 0)
                  (home-view)
                  [:h2.text-info.text-center "Welcome to the Home Page"])]
    (application request title ok js content)))

(defn login
  [request]
  (let [title "Login"
        ok (get-session-id request)
        js nil
        content (main-view title)]
    (application request title ok js content)))

(defn main
  [request]
  (let [title "Home"
        ok (get-session-id request)
        js nil
        content (if (> ok 0)
                  (home-view)
                  [:h2.text-info.text-center (i18n/tr request :auth/welcome)])]
    (application request title ok js content)))

(defn login
  [request]
  (let [title (i18n/tr request :auth/login)
        ok (get-session-id request)
        js nil
        content (main-view title)]
    (application request title ok js content)))

(defn login-user
  [{:keys [params session]}]
  (let [title (i18n/tr params :auth/login)
        username (:username params)
        password (:password params)
        row (first (get-user username))
        active (:active row)
        return-path "/"
        back-msg (i18n/tr params :common/back)
        error-general (i18n/tr params :error/general)
        content-error-general [:p error-general [:a {:href return-path} back-msg]]
        error-forbidden (i18n/tr params :auth/invalid-credentials)
        content-error-forbidden [:p error-forbidden [:a {:href return-path} back-msg]]]
    (if (= active "T")
      (if (hashers/check password (:password row))
        (-> (redirect "/")
            (assoc :session (assoc session :user_id (:id row))))
        (application params title 0 nil content-error-general))
      (application params title 0 nil content-error-forbidden))))

(defn change-password
  [request]
  (let [title (i18n/tr request :auth/change-password)
        ok (get-session-id request)
        js nil
        content (change-password-view title)]
    (application request title ok js content)))

(defn process-password
  [{:keys [params] :as request}]
  (let [title (i18n/tr request :auth/login)
        username (:email params)
        password (:password params)
        row (first (get-user username))
        result (or (update-password username (hashers/derive password)) 0)
        return-path "/home/login"
        back-msg (i18n/tr request :common/back)
        error-general (i18n/tr request :error/general)
        content-error-general [:p error-general [:a {:href return-path} back-msg]]
        error-not-found (i18n/tr request :error/not-found)
        content-error-not-found [:p error-not-found [:a {:href return-path} back-msg]]
        success-updated (i18n/tr request :success/updated)
        content-success [:p success-updated [:a {:href return-path} back-msg]]]
    (if (and row (= (:active row) "T"))
      (if (and password (not (st/blank? password)))
        (if (> result 0)
          (application request title 0 nil content-success)
          (application request title 0 nil content-error-general))
        (application request title 0 nil content-error-not-found))
      (application request title 0 nil content-error-general))))

(defn logoff-user
  [_]
  (-> (redirect "/")
      (assoc :session {})))

(comment
  (:username (first (get-users))))
