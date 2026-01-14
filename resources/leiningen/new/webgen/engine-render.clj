(ns {{sanitized}}.engine.render
  "UI rendering engine for parameter-driven entities.
   Generates grids, forms, and dashboards from entity configurations."
  (:require
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.query :as query]
   [{{sanitized}}.models.grid :as grid]
   [{{sanitized}}.models.form :as form]
   [{{sanitized}}.models.crud]
   [{{sanitized}}.i18n.core :as i18n]
   [clojure.string :as str]))

;; =============================================================================
;; Foreign Key Options Loading
;; =============================================================================

(defn- extract-fk-entity
  "Extracts the foreign key entity name from field configuration or comments."
  [field]
  (when (= :select (:type field))
    (let [label (:label field)
          id (:id field)]
      ;; Try to infer from label (e.g., "Clientes" -> :clientes)
      ;; or from field id (e.g., :id_cliente -> :clientes)
      (cond
        ;; Check if label matches known entities
        (re-find #"(?i)propiedades?" label) :propiedades
        (re-find #"(?i)clientes?" label) :clientes
        (re-find #"(?i)agentes?" label) :agentes
        (re-find #"(?i)alquileres?" label) :alquileres
        (re-find #"(?i)ventas?" label) :ventas
        (re-find #"(?i)fiadores?" label) :fiadores
        
        ;; Try from field ID
        (re-find #"id_propiedad" (name id)) :propiedades
        (re-find #"id_cliente" (name id)) :clientes
        (re-find #"id_agente" (name id)) :agentes
        (re-find #"id_alquiler" (name id)) :alquileres
        (re-find #"id_venta" (name id)) :ventas
        (re-find #"id_fiador" (name id)) :fiadores
        (re-find #"id_comprador" (name id)) :clientes
        (re-find #"id_vendedor" (name id)) :clientes
        (re-find #"id_propietario" (name id)) :clientes
        
        :else nil))))

(defn- load-fk-options
  "Loads foreign key options from related table.
   Returns options in format [{:value id :label name}]"
  [fk-entity]
  (when fk-entity
    (try
      (let [rows (query/list-records fk-entity)
            ;; Try to find a suitable display field
            config (config/get-entity-config fk-entity)
            fields (:fields config)
            ;; Look for common name fields
            nombre-field (first (filter #(re-find #"^nombre$|^name$" (str/lower-case (name (:id %)))) fields))
            apellido-field (first (filter #(re-find #"apellido_paterno|lastname" (str/lower-case (name (:id %)))) fields))
            titulo-field (first (filter #(re-find #"titulo|title|descripcion" (str/lower-case (name (:id %)))) fields))
            
            ;; Build label from available fields
            label-fn (cond
                       ;; If both nombre and apellido exist, concatenate
                       (and nombre-field apellido-field)
                       (fn [row] (str (get row (:id nombre-field) "")
                                     " "
                                     (get row (:id apellido-field) "")))
                       
                       ;; Just nombre
                       nombre-field
                       (fn [row] (str (get row (:id nombre-field) "")))
                       
                       ;; Titulo/title
                       titulo-field
                       (fn [row] (str (get row (:id titulo-field) "")))
                       
                       ;; Fallback to second field
                       :else
                       (fn [row] (str (get row (:id (second fields)) (:id row)))))]
        
        (cons {:value "" :label "-- Select --"}
              (map (fn [row]
                     {:value (str (:id row))
                      :label (label-fn row)})
                   rows)))
      (catch Exception e
        (println "[WARN] Could not load FK options for" fk-entity ":" (.getMessage e))
        [{:value "" :label "-- Select --"}]))))

(defn- populate-fk-options
  "Populates foreign key options for select fields if they're empty."
  [field]
  (if (and (= :select (:type field))
           (empty? (:options field)))
    (if-let [fk-entity (extract-fk-entity field)]
      (assoc field :options (load-fk-options fk-entity))
      field)
    field))

;; =============================================================================
;; Field Rendering
;; =============================================================================

(defn- render-field
  "Renders a single form field based on its configuration."
  [field row]
  (let [;; Auto-populate foreign key options if empty
        field (populate-fk-options field)
        {:keys [id label type required? placeholder options value]} field
        ;; Resolve options: can be a vector, function, or keyword reference
        options (cond
                  (fn? options) (options)
                  (keyword? options) (if-let [f (config/resolve-fn-ref options)]
                                      (f)
                                      options)
                  :else options)
        field-value (or (get row id) value "")
        ;; If it's a select field (FK) with a pre-populated value and hidden-in-grid flag,
        ;; render it as hidden (used in subgrids where parent FK is auto-set)
        effective-type (if (and (= type :select)
                                (:hidden-in-grid? field)
                                (not (or (nil? field-value) (= field-value "") (= field-value 0))))
                         :hidden
                         type)]
    
    (case effective-type
      :hidden
      (form/build-field {:id (name id)
                         :type "hidden"
                         :name (name id)
                         :value field-value})
      
      :text
      (form/build-field {:label label
                         :type "text"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :placeholder (or placeholder (str label "..."))
                         :value field-value})
      
      :email
      (form/build-field {:label label
                         :type "email"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :placeholder (or placeholder (str label "..."))
                         :value field-value})
      
      :password
      (form/build-field {:label label
                         :type "password"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :placeholder (or placeholder "Enter password...")
                         :value field-value})
      
      :date
      (form/build-field {:label label
                         :type "date"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :value field-value})
      
      :datetime
      (form/build-field {:label label
                         :type "datetime-local"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :value field-value})
      
      :number
      (form/build-field {:label label
                         :type "number"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :placeholder (or placeholder "0")
                         :value field-value})
      
      :decimal
      (form/build-field {:label label
                         :type "number"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :step "0.01"
                         :placeholder (or placeholder "0.00")
                         :value field-value})
      
      :textarea
      (form/build-field {:label label
                         :type "textarea"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :placeholder (or placeholder (str label "..."))
                         :value field-value})
      
      :select
      (form/build-field {:label label
                         :type "select"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :value (str field-value)  ;; Convert to string to match option values
                         :options options})
      
      :radio
      (form/build-field {:label label
                         :type "radio"
                         :name (name id)
                         :value field-value
                         :options options})
      
      :checkbox
      (form/build-field {:label label
                         :type "checkbox"
                         :id (name id)
                         :name (name id)
                         :value field-value})
      
      :file
      (let [;; Extract filename if field-value is HTML from after-load hook
            filename (if (and (string? field-value) (re-find #"^<img" field-value))
                       ;; Extract src from img tag: src='/uploads/filename.jpg?uuid'
                       (when-let [match (re-find #"src='([^']+)'" field-value)]
                         (-> (second match)
                             (clojure.string/split #"\?")
                             first
                             (clojure.string/replace (:path {{sanitized}}.models.crud/config) "")))
                       field-value)]
        [:div.mb-3
         [:label.form-label.fw-semibold {:for (name id)} label
          (when required? [:span.text-danger.ms-1 "*"])]
         ;; Show existing image if present
         (when (and filename (not (map? filename)) (not (empty? filename)))
           [:div.mb-2
            [:img {:src (str (:path {{sanitized}}.models.crud/config) filename "?" (random-uuid))
                   :alt filename
                   :style "width: 100%; max-width: 100%; height: auto; border: 1px solid #dee2e6; border-radius: 4px; padding: 4px; cursor: pointer;"
                   :onclick "window.open(this.src, '_blank')"}]
            [:div.text-muted.small.mt-1 filename]])
         ;; File input
         [:input {:type "file"
                  :class "form-control form-control-lg"
                  :id (name id)
                  :name (name id)
                  :required required?
                  :accept "image/*"}]])
      
      :computed
      ;; Computed fields are displayed but not editable
      [:div.mb-3
       [:label.form-label.fw-semibold label]
       [:p.form-control-plaintext field-value]]
      
      ;; Default
      (form/build-field {:label label
                         :type "text"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :value field-value}))))

;; =============================================================================
;; Form Rendering
;; =============================================================================

(defn render-form
  "Renders a form for an entity based on its configuration."
  [request entity row]
  (let [config (config/get-entity-config entity)
        fields (config/get-form-fields entity)
        entity-name (name entity)
        href (str "/admin/" entity-name "/save")
        
        ;; Check for custom form renderer
        custom-form-fn (get-in config [:ui :form-fn])]
    
    (if custom-form-fn
      ;; Use custom renderer
      (custom-form-fn entity row)
      
      ;; Default form rendering
      (let [field-elements (map #(render-field % row) fields)
            buttons (form/build-modal-buttons request)]
        (form/form href field-elements buttons)))))

(defn render-form-modal
  "Renders a form wrapped in a modal."
  [title entity row]
  (let [form-content (render-form entity row)]
    (grid/build-modal title row form-content)))

;; =============================================================================
;; Grid Rendering
;; =============================================================================

(defn- build-fields-map
  "Builds a field map for grid rendering from entity config."
  [entity]
  (let [display-fields (config/get-display-fields entity)]
    (apply array-map
           (mapcat (fn [field]
                     [(:id field) (:label field)])
                   display-fields))))

(defn render-grid
  "Renders a grid for an entity."
  [request entity rows]
  (let [config (config/get-entity-config entity)
        entity-name (name entity)
        title (:title config)
        table-id (str entity-name "_table")
        fields (build-fields-map entity)
        href (str "/admin/" entity-name)
        actions (or (:actions config) config/default-actions)
        
        ;; Check for custom grid renderer
        custom-grid-fn (get-in config [:ui :grid-fn])
        
        ;; Check for subgrids and enhanced flag
        subgrids (:subgrids config)
        enhanced? (:enhanced-tabgrid config)]
    
    (println "[DEBUG render-grid]" entity "enhanced?" enhanced? "has subgrids:" (boolean (seq subgrids)))
    (when (seq subgrids)
      (println "[DEBUG] Subgrid count:" (count subgrids)))
    
    (cond
      ;; Custom grid renderer
      custom-grid-fn
      (custom-grid-fn entity rows)
      
      :else
      (grid/build-grid request title rows table-id fields href actions))))

;; =============================================================================
;; Dashboard Rendering
;; =============================================================================

(defn render-dashboard
  "Renders a read-only dashboard for an entity."
  [entity rows]
  (let [config (config/get-entity-config entity)
        entity-name (name entity)
        title (:title config)
        table-id (str entity-name "_dashboard")
        fields (build-fields-map entity)
        
        ;; Check for custom dashboard renderer
        custom-dashboard-fn (get-in config [:ui :dashboard-fn])]
    
    (if custom-dashboard-fn
      (custom-dashboard-fn entity rows)
      (grid/build-dashboard title rows table-id fields))))

;; =============================================================================
;; Report Rendering
;; =============================================================================

(defn render-report
  "Renders a report view (alias for dashboard)."
  [entity rows]
  (render-dashboard entity rows))

;; =============================================================================
;; Subgrid Rendering
;; =============================================================================

(defn render-subgrid
  "Renders a subgrid for a parent-child relationship."
  [request entity parent-id rows]
  (let [config (config/get-entity-config entity)
        entity-name (name entity)
        title (:title config)
        table-id (str entity-name "_subgrid")
        fields (build-fields-map entity)
        href (str "/admin/" entity-name)
        actions (or (:actions config) config/default-actions)
        new-href (str href "/add-form/" parent-id)]
    
    (grid/build-grid-with-custom-new request title rows table-id fields href actions new-href)))

;; =============================================================================
;; List/Select Rendering
;; =============================================================================

(defn render-select-list
  "Renders a simple list for selection (used in modals)."
  [entity rows select-url]
  (let [config (config/get-entity-config entity)
        fields (build-fields-map entity)
        entity-name (name entity)]
    [:div.table-responsive
     [:table.table.table-striped.table-bordered.table-hover
      [:thead
       [:tr
        [:th "Select"]
        (for [[_ label] fields]
          [:th label])]]
      [:tbody
       (for [row rows]
         [:tr
          [:td
           [:form {:method "get" :action select-url :style "display:inline"}
            [:input {:type "hidden" :name "id" :value (:id row)}]
            [:button.btn.btn-sm.btn-success {:type "submit"} "Select"]]]
          (for [[field-id _] fields]
            [:td (get row field-id)])])]]]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn render-error
  "Renders an error message."
  [message]
  [:div.alert.alert-danger.m-3
   [:i.bi.bi-exclamation-triangle.me-2]
   message])

(defn render-success
  "Renders a success message."
  [message]
  [:div.alert.alert-success.m-3
   [:i.bi.bi-check-circle.me-2]
   message])

(defn render-not-authorized
  "Renders a not authorized message."
  [entity user-level]
  (let [config (config/get-entity-config entity)
        required-rights (:rights config)]
    (render-error
     (str "Not authorized to access " (:title config)
          "! Required level(s): " (clojure.string/join ", " required-rights)
          ". Your level: " user-level))))

(comment
  ;; Usage examples
  (render-grid :users (query/list-records :users))
  (render-form :users nil)
  (render-form :users {:id 1 :lastname "Doe"})
  (render-dashboard :users (query/list-records :users))
  (render-subgrid :user-roles 1 []))
