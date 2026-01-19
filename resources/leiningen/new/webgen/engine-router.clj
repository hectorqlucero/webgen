(ns {{sanitized}}.engine.router
  (:require
   [compojure.core :refer [defroutes GET POST context]]
   [ring.util.response :refer [redirect]]
   [hiccup.core :refer [html]]
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.query :as query]
   [{{sanitized}}.engine.crud :as crud]
   [{{sanitized}}.engine.render :as render]
   [{{sanitized}}.tabgrid.core :as tabgrid]
   [{{sanitized}}.models.util :refer [get-session-id user-level]]
   [{{sanitized}}.layout :refer [application error-404]]))

(defn- get-entity-from-request
  "Extracts and validates entity from request parameters."
  [request]
  (let [entity-str (or (get-in request [:params :entity])
                       (get-in request [:route-params :entity]))
        entity (when entity-str (keyword entity-str))]
    (if entity
      (try
        (config/get-entity-config entity)
        entity
        (catch Exception _
          nil))
      nil)))

(defn- check-permission
  "Checks if user has permission to access entity."
  [entity request]
  (let [user-lvl (user-level request)]
    (config/has-permission? entity user-lvl)))

(defn- unauthorized-response
  "Returns an unauthorized response."
  [entity request]
  (let [title "Access Denied"
        ok (get-session-id request)
        content (render/render-not-authorized entity (user-level request))]
    (application request title ok nil content)))

(defn handle-grid
  "Handles grid/list view for an entity."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (do
      (if (check-permission entity request)
        (try
          (let [config (config/get-entity-config entity)
                title (:title config)
                ok (get-session-id request)
                parent-id (get-in request [:params :id])]
            
            ;; Always use TabGrid for consistency
            (let [content (tabgrid/render-tabgrid request entity parent-id)]
              (application request title ok nil content)))
          (catch Exception e
            (println "[ERROR] Grid handler failed:" (.getMessage e))
            (.printStackTrace e)
            (let [title "Error"
                  ok (get-session-id request)
                  content (render/render-error (.getMessage e))]
              (application request title ok nil content))))
        (do
          (unauthorized-response entity request))))
    (error-404 "Entity not found" "/")))

(defn handle-dashboard
  "Handles dashboard view for an entity."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (if (check-permission entity request)
      (try
        (let [config (config/get-entity-config entity)
              title (:title config)
              ok (get-session-id request)
              rows (query/list-with-hooks entity)
              content (render/render-dashboard entity rows)]
          (application request title ok nil content))
        (catch Exception e
          (println "[ERROR] Dashboard handler failed:" (.getMessage e))
          (.printStackTrace e)
          (let [title "Error"
                ok (get-session-id request)
                content (render/render-error (.getMessage e))]
            (application request title ok nil content))))
      (unauthorized-response entity request))
    (error-404 "Entity not found" "/")))

(defn handle-add-form
  "Handles add form display."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (if (check-permission entity request)
      (try
        (let [config (config/get-entity-config entity)
              title (str "New " (:title config))
              parent-id (get-in request [:params :parent_id])
              ;; For subgrids, we need to find which FK field to populate
              ;; Look for a hidden FK field (parent reference)
              row (when parent-id
                    (let [fk-field (first (filter #(and (= (:type %) :hidden)
                                                         (not= (:id %) :id))
                                                   (:fields config)))]
                      (when fk-field
                        {(:id fk-field) parent-id})))]
          (html (render/render-form request entity row)))
        (catch Exception e
          (println "[ERROR] Add form handler failed:" (.getMessage e))
          (.printStackTrace e)
          (html (render/render-error (.getMessage e)))))
      (html (render/render-error "Not authorized")))
    (html (render/render-error "Entity not found"))))

(defn handle-edit-form
  "Handles edit form display."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (if (check-permission entity request)
      (try
        (let [config (config/get-entity-config entity)
              id (get-in request [:params :id])
              title (str "Edit " (:title config))
              row (query/get-with-hooks entity id)]
          (if row
            (html (render/render-form request entity row))
            (html (render/render-error "Record not found"))))
        (catch Exception e
          (println "[ERROR] Edit form handler failed:" (.getMessage e))
          (.printStackTrace e)
          (html (render/render-error (.getMessage e)))))
      (html (render/render-error "Not authorized")))
    (html (render/render-error "Entity not found"))))

(defn handle-save
  "Handles form save (create/update)."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (if (check-permission entity request)
      (try
        (let [params (or (:params request) (:form-params request))
              user-id (get-in request [:session :user_id])
              config (config/get-entity-config entity)
              
              ;; Save with or without audit
              result (if (:audit? config)
                       (crud/save-with-audit entity params user-id)
                       (crud/save-record entity params))]
          
          (if (:success result)
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body "{\"ok\":true}"}
            {:status 400
             :headers {"Content-Type" "application/json"}
             :body (str "{\"ok\":false,\"errors\":"
                        (pr-str (:errors result))
                        "}")}))
        (catch Exception e
          (println "[ERROR] Save handler failed:" (.getMessage e))
          (.printStackTrace e)
          {:status 500
           :headers {"Content-Type" "application/json"}
           :body (str "{\"ok\":false,\"error\":" (pr-str (.getMessage e)) "}")}))
      {:status 403
       :headers {"Content-Type" "application/json"}
       :body "{\"ok\":false,\"error\":\"Not authorized\"}"})
    {:status 404
     :headers {"Content-Type" "application/json"}
     :body "{\"ok\":false,\"error\":\"Entity not found\"}"}))

(defn handle-delete
  "Handles record deletion."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (if (check-permission entity request)
      (try
        (let [id (get-in request [:params :id])
              user-id (get-in request [:session :user_id])
              config (config/get-entity-config entity)
              entity-name (name entity)
              
              ;; Delete with or without audit
              result (if (:audit? config)
                       (crud/delete-with-audit entity id user-id)
                       (crud/delete-record entity id))]
          
          (if (:success result)
            {:status 302
             :headers {"Location" (str "/admin/" entity-name)}}
            (error-404 "Unable to delete record!" (str "/admin/" entity-name))))
        (catch Exception e
          (println "[ERROR] Delete handler failed:" (.getMessage e))
          (.printStackTrace e)
          (error-404 (.getMessage e) (str "/admin/" (name entity)))))
      (error-404 "Not authorized" "/"))
    (error-404 "Entity not found" "/")))

(defn handle-subgrid
  "Handles subgrid AJAX requests."
  [request]
  (if-let [entity (get-entity-from-request request)]
    (if (check-permission entity request)
      (try
        (let [parent-id (get-in request [:params :parent_id])
              parent-entity-str (get-in request [:params :parent_entity])
              config (config/get-entity-config entity)
              title (:title config)
              
              ;; Get all rows first
              all-rows (query/list-records entity)
              
              ;; Filter by parent FK if we have parent info
              ;; Try to find the FK field automatically
              rows (if parent-id
                     (let [parent-entity (when parent-entity-str (keyword parent-entity-str))
                           ;; Find FK field that references parent entity
                           fk-field (or
                                     ;; Look for FK in subgrid config
                                     (when parent-entity
                                       (let [parent-config (try (config/get-entity-config parent-entity) (catch Exception _ nil))
                                             subgrids (:subgrids parent-config)
                                             matching-sg (first (filter #(= (:entity %) entity) subgrids))]
                                         (:foreign-key matching-sg)))
                                     ;; Try common patterns: id_propiedades → :id_propiedad
                                     (let [fields (:fields config)
                                           fk-candidates (filter #(= :select (:type %)) fields)]
                                       (:id (first fk-candidates))))]
                       (if fk-field
                         (do
                           (filter #(= (str (get % fk-field)) (str parent-id)) all-rows))
                         (do
                           (println "[WARN] Could not determine FK field for subgrid filtering")
                           all-rows)))
                     all-rows)
              
              content (render/render-subgrid request entity parent-id rows)]
          
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (html content)})
        (catch Exception e
          (println "[ERROR] Subgrid handler failed:" (.getMessage e))
          (.printStackTrace e)
          {:status 500
           :headers {"Content-Type" "text/html"}
           :body (html (render/render-error (.getMessage e)))}))
      {:status 403
       :headers {"Content-Type" "text/html"}
       :body (html (render/render-error "Not authorized"))})
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body (html (render/render-error "Entity not found"))}))

(defroutes engine-routes
  ;; Admin Grid Routes
  (context "/admin/:entity" [entity]
    (GET "/" request
      (handle-grid (assoc-in request [:params :entity] entity)))
    
    (GET "/add-form" request
      (handle-add-form (assoc-in request [:params :entity] entity)))
    
    (GET "/add-form/:parent_id" [parent_id :as request]
      (handle-add-form (-> request
                           (assoc-in [:params :entity] entity)
                           (assoc-in [:params :parent_id] parent_id))))
    
    (GET "/edit-form/:id" [id :as request]
      (handle-edit-form (-> request
                            (assoc-in [:params :entity] entity)
                            (assoc-in [:params :id] id))))
    
    (POST "/save" request
      (handle-save (assoc-in request [:params :entity] entity)))
    
    (GET "/delete/:id" [id :as request]
      (handle-delete (-> request
                         (assoc-in [:params :entity] entity)
                         (assoc-in [:params :id] id))))
    
    (GET "/subgrid" request
      (handle-subgrid (assoc-in request [:params :entity] entity))))
  
  ;; Dashboard Routes
  (GET "/dashboard/:entity" [entity :as request]
    (handle-dashboard (assoc-in request [:params :entity] entity)))
  
  ;; Report Routes (alias for dashboard)
  (GET "/reports/:entity" [entity :as request]
    (handle-dashboard (assoc-in request [:params :entity] entity)))
  
  ;; Development/Admin Routes
  (GET "/admin/reload-config" request
    (try
      (config/reload-all!)
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (str "<html><body style='font-family: monospace; padding: 20px;'>"
                  "<h2>Configuration Reloaded</h2>"
                  "<p>All entity configurations have been reloaded from disk.</p>"
                  "<ul>"
                  "<li>Entity EDN files: reloaded</li>"
                  "<li>Hook functions: reloaded</li>"
                  "<li>Config cache: cleared</li>"
                  "</ul>"
                  "<p><a href='javascript:history.back()'>← Go Back</a></p>"
                  "</body></html>")}
      (catch Exception e
        {:status 500
         :headers {"Content-Type" "text/html"}
         :body (str "<html><body style='font-family: monospace; padding: 20px;'>"
                    "<h2>Reload Failed</h2>"
                    "<pre>" (.getMessage e) "</pre>"
                    "<p><a href='javascript:history.back()'>← Go Back</a></p>"
                    "</body></html>")}))))

(defn get-routes
  "Returns the dynamic engine routes to be included in the main app."
  []
  engine-routes)

(comment
  ;; The engine routes replace all generated controller/model/view files
  ;; Simply include in your main routes:
  ;; (def app-routes
  ;;   (routes
  ;;     ... other routes ...
  ;;     (wrap-login (wrap-routes (engine/get-routes)))
  ;;     ...))
  )
