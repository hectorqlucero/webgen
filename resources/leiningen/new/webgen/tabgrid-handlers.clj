(ns {{sanitized}}.tabgrid.handlers
  "HTTP handlers for TabGrid AJAX requests"
  (:require
   [ring.util.response :refer [response]]
   [hiccup.core :refer [html]]
   [{{sanitized}}.tabgrid.data :as data]
   [{{sanitized}}.tabgrid.render :as render]
   [{{sanitized}}.engine.config :as config]
   [cheshire.core :as json]))

(defn handle-load-subgrid
  "AJAX handler: loads subgrid data for a specific parent"
  [request]
  (let [params (:params request)
        entity (keyword (:entity params))
        subgrid-entity (keyword (:subgrid_entity params))
        parent-id (:parent_id params)
        foreign-key (:foreign_key params)]
    
     (try
      (let [records (data/fetch-subgrid-records subgrid-entity parent-id foreign-key)
            fields (data/build-fields-map subgrid-entity)
            subgrid-config (config/get-entity-config subgrid-entity)
            actions (or (:actions subgrid-config) {:new true :edit true :delete true})]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success true
                 :records records
                 :count (count records)
                 :fields fields
                 :actions actions})})
      (catch Exception e
        (.printStackTrace e)
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success false
                 :error (.getMessage e)})}))))

(defn handle-get-parent
  "AJAX handler: gets a specific parent record"
  [request]
  (let [params (:params request)
        entity (keyword (:entity params))
        parent-id (:parent_id params)]
    
    (try
      (let [record (data/fetch-parent-record entity parent-id)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success true
                 :record record})})
      (catch Exception e
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success false
                 :error (.getMessage e)})}))))
