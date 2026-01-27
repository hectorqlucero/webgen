(ns {{sanitized}}.engine.render
  (:require
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.engine.query :as query]
   [{{sanitized}}.models.grid :as grid]
   [{{sanitized}}.models.form :as form]
   [{{sanitized}}.models.crud :as crud]
   [{{sanitized}}.i18n.core :as i18n]
   [clojure.string :as str]))

(defn- load-fk-options
  "Enhanced FK options with sorting, filtering, and parameter binding.
   Completely replaces entity query system for FK fields."
  [fk-entity fk-fields separator & {:keys [sort-by filter-field filter-value]}]
  (try
    ;; Build WHERE clause with parameter binding
    (let [where-clause (when (and filter-field filter-value)
                         (str "WHERE " (name filter-field) " = ?"))

          ;; Build ORDER BY clause for sorting
          order-by (when sort-by
                     (str "ORDER BY "
                          (clojure.string/join ", "
                                               (map name
                                                    (if (sequential? sort-by) sort-by [sort-by])))))

          ;; Complete SQL replacement
          sql (str "SELECT id, "
                   (clojure.string/join ", " (map name fk-fields))
                   " FROM "
                   (name fk-entity)
                   " "
                   where-clause
                   " "
                   order-by)

          ;; Parameter binding for SQL injection protection
          sql-params (if (and filter-field filter-value)
                       [sql filter-value]
                       [sql])

          ;; Execute with raw CRUD Query
          rows (crud/Query crud/db sql-params)

          separator (or separator " — ")
          label-fn (fn [row]
                     (->> fk-fields
                          (map #(str (get row % "")))
                          (str/join separator)))]

      (cons {:value "" :label (str "-- " (i18n/tr nil :common/select) " --")}
            (map (fn [row]
                   {:value (str (:id row))
                    :label (label-fn row)})
                 rows)))
    (catch Exception e
      (println "[WARN] Could not load enhanced FK options for" fk-entity ":" (.getMessage e))
      [{:value "" :label (str "-- " (i18n/tr nil :common/select) " --")}])))

(defn- populate-fk-options
  "Enhanced population with optional sort/filter parameters.
   Safely handles nil filter parameters."
  [field]
  (if (and (= :fk (:type field))
           (or (nil? (:options field))
               (and (coll? (:options field)) (empty? (:options field)))))

    (let [fk-entity (:fk field)
          fk-fields (:fk-field field)
          separator (:fk-separator field)
          sort-by (:fk-sort field)
          filter-pair (:fk-filter field)
          ;; Safe parameter extraction with nil checks
          filter-field (when (and filter-pair (sequential? filter-pair)) (first filter-pair))
          filter-value (when (and filter-pair (sequential? filter-pair)) (second filter-pair))]

      (if fk-entity
        (assoc field :options (load-fk-options
                               fk-entity
                               fk-fields
                               separator
                               :sort-by sort-by
                               :filter-field filter-field
                               :filter-value filter-value))
        field))

    field))

(defn- resolve-options
  [options]
  (cond
    (vector? options) options
    (nil? options) []
    (keyword? options)
    (let [ns-str (namespace options)
          name-str (name options)]
      (try
        (let [fn-sym (symbol ns-str name-str)
              fn-var (requiring-resolve fn-sym)]
          (if (and fn-var (fn? (var-get fn-var)))
            ((var-get fn-var))
            (do
              (println "[WARN] Could not resolve options function:" fn-sym)
              [])))
        (catch Exception e
          (println "[WARN] Exception resolving options for" options ":" (.getMessage e))
          [])))
    :else []))

(defn- render-field
  "Renders a single form field based on its configuration."
  [field row]
  (let [fk-field? (= :fk (:type field))
        populated-field (if fk-field? (populate-fk-options field) field)
        {:keys [id label type required? placeholder options value]} populated-field
        field-value (or (get row id) value "")
        resolved-options (if fk-field? options (resolve-options options))]
    (case type
      :hidden
      (form/build-field {:type "hidden"
                         :id (name id)
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
                         :placeholder (or placeholder (str label "..."))
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
                         :options resolved-options})

      :fk
      ;; Foreign key select
      (form/build-field {:label label
                         :type "select"
                         :id (name id)
                         :name (name id)
                         :required required?
                         :value (str field-value)
                         :options resolved-options})

      :radio
      (form/build-field {:label label
                         :type "radio"
                         :name (name id)
                         :value field-value
                         :options resolved-options})

      :checkbox
      (form/build-field {:label label
                         :type "checkbox"
                         :id (name id)
                         :name (name id)
                         :value field-value})

      :file
      ;; File input with preview
      (let [filename (if (and (string? field-value) (re-find #"^<img" field-value))
                       (when-let [match (re-find #"src='([^']+)'" field-value)]
                         (-> (second match)
                             (clojure.string/split #"\?")
                             first
                             (clojure.string/replace (:path crud/config) "")))
                       field-value)]
        [:div.mb-3
         [:label.form-label.fw-semibold {:for (name id)} label
          (when required? [:span.text-danger.ms-1 "*"])]
         (when (and filename (not (map? filename)) (not (empty? filename)))
           [:div.mb-2
            [:img {:src (str (:path crud/config) filename "?" (random-uuid))
                   :alt filename
                   :style "width: 100%; max-width: 100%; height: auto; border: 1px solid #dee2e6; border-radius: 4px; padding: 4px; cursor: pointer;"
                   :onclick "window.open(this.src, '_blank')"}]
            [:div.text-muted.small.mt-1 filename]])
         [:input {:type "file"
                  :class "form-control form-control-lg"
                  :id (name id)
                  :name (name id)
                  :required required?
                  :accept "image/*"}]])
      :computed
      ;; computed fields are displayed but not editable
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

(defn render-form
  "Renders a form for an entity based on its configuration."
  [request entity row]
  (let [config (config/get-entity-config entity)
        fields (config/get-form-fields entity)
        entity-name (name entity)
        href (str "/admin/" entity-name "/save")
        custom-form-fn (get-in config [:ui :form-fn])]
    (if custom-form-fn
      (custom-form-fn entity row)
      (let [field-elements (map #(render-field % row) fields)
            buttons (form/build-modal-buttons request)]
        (form/form href field-elements buttons)))))

(defn render-form-modal
  "Renders a form wrapped in a modal."
  [title entity row]
  (let [form-content (render-form entity row)]
    (grid/build-modal title row form-content)))

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
        custom-grid-fn (get-in config [:ui :grid-fn])]
    (if custom-grid-fn
      (custom-grid-fn entity rows)
      (grid/build-grid request title rows table-id fields href actions))))

(defn render-dashboard
  "Renders a read-only dashboard for an entity."
  [entity rows]
  (let [config (config/get-entity-config entity)
        entity-name (name entity)
        title (:title config)
        table-id (str entity-name "_dashboard")
        fields (build-fields-map entity)
        custom-dashboard-fn (get-in config [:ui :dashboard-fn])]
    (if custom-dashboard-fn
      (custom-dashboard-fn entity rows)
      (grid/build-dashboard title rows table-id fields))))

(defn render-report
  "Renders a report view (alias for dashboard)."
  [entity rows]
  (render-dashboard entity rows))

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
