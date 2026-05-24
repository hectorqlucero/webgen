(ns {{sanitized}}.tabgrid.handlers
  "HTTP handlers for TabGrid AJAX requests"
  (:require
   [hiccup2.core :refer [html]]
   [{{sanitized}}.tabgrid.data :as data]
   [{{sanitized}}.tabgrid.render :as render]
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.models.crud :as crud]
   [cheshire.core :as json]
   [{{sanitized}}.web.csrf :refer [csrf-field]]))

(defn- render-html
  [hiccup-body]
  (str (html hiccup-body)))

(defn handle-load-subgrid
  "AJAX handler: loads subgrid data for a specific parent"
  [request]
  (let [params (:params request)
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

(defn handle-associate
  "POST /tabgrid/associate — inserts a row into the junction table."
  [request]
  (let [params        (:params request)
        through-table (:through_table params)
        parent-fk     (keyword (:parent_fk params))
        parent-id     (Long/parseLong (:parent_id params))
        related-fk    (keyword (:related_fk params))
        related-id    (Long/parseLong (:related_id params))
        row           {parent-fk parent-id related-fk related-id}]
    (try
      (crud/Insert through-table row)
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:success true})}
      (catch Exception _e
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:success true})}))))

(defn handle-dissociate
  "POST /tabgrid/dissociate — deletes a row from the junction table."
  [request]
  (let [params        (:params request)
        through-table (:through_table params)
        parent-fk     (keyword (:parent_fk params))
        parent-id     (Long/parseLong (:parent_id params))
        related-fk    (keyword (:related_fk params))
        related-id    (Long/parseLong (:related_id params))]
    (try
      (let [where [(str (name parent-fk) " = ? AND " (name related-fk) " = ?")
                   parent-id related-id]]
        (crud/Delete through-table where))
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:success true})}
      (catch Exception _e
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:success true})}))))

(defn handle-m2m-pane
  "GET /tabgrid/m2m-pane — returns fresh HTML fragment for one M2M accordion section.
   Used by the client after associate/dissociate to refresh the pane without a full reload."
  [request]
  (let [params          (:params request)
        entity          (keyword (:entity params))
        parent-id       (:parent_id params)
        subgrid-entity  (:subgrid_entity params)   ;; raw entity name, e.g. "employee_skills"
        entity-config   (config/get-entity-config entity)
        entity-title    (or (:title entity-config) (name entity))
        parent-record   (data/fetch-parent-record entity parent-id)
        fields          (data/build-fields-map entity)
        parent-display  (render/parent-display-label fields parent-record parent-id)
        subgrid-spec    (first (filter #(= (name (:entity %)) subgrid-entity)
                                       (:subgrids entity-config)))
        subgrid         (data/prepare-subgrid-config entity subgrid-spec parent-id)]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (render-html (render/render-m2m-pane request (name entity) entity-title
                                                parent-display subgrid parent-id))}))

(defn- pivot-visible-fields
  "Returns non-hidden, non-FK fields from a junction entity config."
  [junction-kw parent-fk-str related-fk-str]
  (let [cfg    (config/get-entity-config junction-kw)
        fk-ids #{(keyword parent-fk-str) (keyword related-fk-str)}]
    (remove #(or (= :hidden (:type %)) (contains? fk-ids (:id %)))
            (:fields cfg))))

(defn handle-pivot-form
  "GET /tabgrid/pivot-form — returns HTML form for editing junction pivot attributes."
  [request]
  (let [params      (:params request)
        through-str (:through_table params)
        parent-fk   (:parent_fk params)
        parent-id   (:parent_id params)
        related-fk  (:related_fk params)
        related-id  (:related_id params)
        junction    (keyword through-str)
        fields      (pivot-visible-fields junction parent-fk related-fk)
        sql         [(str "SELECT * FROM " through-str
                          " WHERE " parent-fk " = ? AND " related-fk " = ?")
                     (Long/parseLong parent-id) (Long/parseLong related-id)]
        current-row (first (crud/Query sql))]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body
     (render-html
      [:form {:method "POST" :action "/tabgrid/save-pivot"}
       (csrf-field)
       [:input {:type "hidden" :name "through_table" :value through-str}]
       [:input {:type "hidden" :name "parent_fk"     :value parent-fk}]
       [:input {:type "hidden" :name "parent_id"     :value parent-id}]
       [:input {:type "hidden" :name "related_fk"    :value related-fk}]
       [:input {:type "hidden" :name "related_id"    :value related-id}]
       (if (seq fields)
         (for [field fields
               :let [fid   (:id field)
                     ftype (or (:type field) :text)
                     value (get current-row fid "")]]
           [:div.mb-3
            [:label.form-label (:label field)]
            (case ftype
              :number   [:input.form-control
                         {:type "number" :name (name fid) :value (str value)}]
              :textarea [:textarea.form-control {:name (name fid) :rows 3} (str value)]
              [:input.form-control {:type "text" :name (name fid) :value (str value)}])])
         [:p.text-muted "No pivot attributes to edit."])
       [:div.d-flex.gap-2.mt-3
        [:button.btn.btn-primary {:type "submit"}
         [:i.bi.bi-check.me-1] "Save"]
        [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"}
         "Cancel"]]])}))

(defn handle-save-pivot
  "POST /tabgrid/save-pivot — updates pivot attributes on a junction table row."
  [request]
  (let [params          (:params request)
        through-str     (:through_table params)
        parent-fk       (:parent_fk params)
        parent-id       (Long/parseLong (:parent_id params))
        related-fk      (:related_fk params)
        related-id      (Long/parseLong (:related_id params))
        junction        (keyword through-str)
        pivot-field-ids (map :id (pivot-visible-fields junction parent-fk related-fk))
        row             (into {} (for [fid pivot-field-ids
                                       :let [v (get params (keyword (name fid)))]
                                       :when (some? v)]
                                   [fid v]))
        where           [(str parent-fk " = ? AND " related-fk " = ?")
                         parent-id related-id]]
    (try
      (when (seq row)
        (crud/Update junction row where))
      {:status 302 :headers {"Location" (get-in request [:headers "referer"] "/")}}
      (catch Exception _e
        {:status 302 :headers {"Location" (get-in request [:headers "referer"] "/")}}))))
