(ns {{sanitized}}.tabgrid.render
  "Clean TabGrid rendering - pure UI generation"
  (:require
   [clojure.string :as str]
   [{{sanitized}}.i18n.core :as i18n]
   [hiccup.util :refer [raw-string]]
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.web.csrf :refer [csrf-field]]))

;;; ---- Utilities -------------------------------------------------------

(defn- safe-id [s]
  (-> (str s)
      (str/lower-case)
      (str/replace #"[^a-z0-9]+" "-")))

(defn- get-record-id
  "Returns the record primary key. Composite keys are pipe-joined: \"1|2\"."
  [entity-name row]
  (try
    (let [pk (:primary-key (config/get-entity-config entity-name))]
      (if (and pk (vector? pk) (> (count pk) 1))
        (str/join "|" (map #(get row %) pk))
        (:id row)))
    (catch Exception _
      (:id row))))

(defn parent-display-label
  "First non-id string field value from parent-row, falling back to \"#<id>\"."
  [fields parent-row selected-parent-id]
  (or (some (fn [[fid _]]
              (when (not= fid :id)
                (let [v (get parent-row fid)]
                  (when (and v (string? v) (seq v)) v))))
            fields)
      (str "#" selected-parent-id)))

(defn- render-field-value [value]
  (if (and (string? value) (re-find #"^<" value))
    (raw-string value)
    value))

;;; ---- Leaf-level public renderers (no forward refs) ------------------

(defn render-parent-grid-table
  "Detail card for one parent record: action bar at top + 2-column field grid."
  [request entity-name _title fields row actions]
  (if row
    [:div.tg-record-detail
     [:div.tg-detail-actions
      [:span.tg-detail-id
       [:i.bi.bi-hash]
       (get-record-id entity-name row)]
      [:div.ms-auto.d-flex.align-items-center.gap-2
       (when (:edit actions)
         [:button.btn.btn-sm.btn-primary.edit-btn
          {:data-id        (get-record-id entity-name row)
           :data-url       (str "/admin/" entity-name "/edit-form/" (get-record-id entity-name row))
           :data-bs-toggle "modal"
           :data-bs-target "#exampleModal"}
          [:i.bi.bi-pencil.me-1]
          (i18n/tr request :common/edit)])
       (when (:delete actions)
         [:form.d-inline {:method   "POST"
                          :action   (str "/admin/" entity-name "/delete/" (get-record-id entity-name row))
                          :onsubmit (str "return confirm('" (i18n/tr request :confirm/delete) "')")}
          (csrf-field)
          [:button.btn.btn-sm.btn-outline-danger {:type "submit"}
           [:i.bi.bi-trash.me-1]
           (i18n/tr request :common/delete)]])]]
     [:div.tg-fields-grid
      (for [[field-id field-label] fields
            :let [value        (get row field-id)
                  field-config (first (filter #(= (:id %) field-id)
                                              (:fields (config/get-entity-config entity-name))))
                  display-val  (if (= (:type field-config) :computed)
                                 (if-let [f (:compute-fn field-config)] (f row) "")
                                 value)]]
        [:div.tg-field-item
         [:span.tg-field-label field-label]
         [:span.tg-field-value
          (let [v (render-field-value display-val)]
            (if (or (nil? v) (= v "")) [:span.text-muted "—"] v))]])]]
    [:div.tg-empty-state
     [:i.bi.bi-inbox {:style "font-size:2.5rem"}]
     [:p.mt-2.mb-0 (i18n/tr request :grid/no-records)]]))

(defn render-subgrid-table
  "Spinner placeholder replaced by JS once subgrid data loads."
  [request entity-name sg-name _title fields]
  [:div
   [:div.subgrid-loading.text-center.p-4
    [:div.spinner-border.text-primary {:role "status"}]
    [:p.mt-2 (i18n/tr request :common/loading)]]
   [:div.subgrid-table-wrapper {:style "display:none"}
    [:table.table.table-hover.table-bordered.table-sm.dataTable.w-100
     {:id (str entity-name "-" sg-name "-table")}
     [:thead [:tr (for [[_ label] fields] [:th label]) [:th "Actions"]]]
     [:tbody]]]])

;;; ---- Small private primitives used by tab-pane composers ------------

(defn- render-parent-context-header
  "Card header strip: 'Skills > Leadership'."
  [entity-title parent-display]
  [:div.card-header.bg-light.border-bottom.py-2.px-3
   [:span.text-muted.small
    [:i.bi.bi-layers.me-1]
    entity-title " > "
    [:strong.text-dark parent-display]]])

(defn- render-subgrid-toolbar
  "Title + New-button row at the top of a subgrid card body."
  [request entity-name subgrid selected-parent-id]
  [:div.d-flex.justify-content-between.align-items-center.mb-3
   [:h5.mb-0
    [:i.me-2 {:class (or (:icon subgrid) "bi bi-list-ul")}]
    (:title subgrid)]
   [:button.btn.btn-sm.btn-primary.add-subgrid-btn
    {:data-subgrid-entity (name (:entity subgrid))
     :data-parent-id      (str selected-parent-id)
     :data-parent-entity  entity-name}
    [:i.bi.bi-plus-circle.me-1]
    (i18n/tr request :common/new)]])

;;; ---- Tab-pane composers ---------------------------------------------

(defn- render-one-to-one-section
  "Inline card for a one-to-one relationship, shown inside the parent tab pane.
   Shows the related record's fields directly; no separate tab is created.
   parent-id is passed so the Create button can pre-fill the FK field."
  [request parent-entity-name parent-id subgrid]
  (let [sg-name  (name (:entity subgrid))
        record   (:record subgrid)
        fields   (:fields subgrid)
        actions  (:actions subgrid)]
    [:div.card.border.mb-3
     [:div.card-header.d-flex.justify-content-between.align-items-center.py-2
      [:span.fw-semibold.text-secondary.small
       [:i.me-2 {:class (or (:icon subgrid) "bi bi-person-vcard")}]
       (:title subgrid)
       (if record
         [:span.badge.bg-success.ms-2 "Linked"]
         [:span.badge.bg-light.text-secondary.border.ms-2 "Not set"])]
      (if record
        (when (:edit actions)
          [:button.btn.btn-sm.btn-outline-warning.edit-btn
           {:data-id        (str (:id record))
            :data-url       (str "/admin/" sg-name "/edit-form/" (:id record))
            :data-bs-toggle "modal"
            :data-bs-target "#exampleModal"}
           [:i.bi.bi-pencil.me-1] (i18n/tr request :common/edit)])
        ;; Create button: pre-fills the FK via parent_id + parent_entity query params
        [:button.btn.btn-sm.btn-outline-primary.edit-btn
         {:data-url       (str "/admin/" sg-name "/add-form"
                               "?parent_id=" parent-id
                               "&parent_entity=" parent-entity-name)
          :data-bs-toggle "modal"
          :data-bs-target "#exampleModal"}
         [:i.bi.bi-plus-circle.me-1]
         (str "Create " (:title subgrid))])]
     (when record
       [:div.card-body.p-0
        [:table.table.table-sm.table-bordered.mb-0
         [:tbody
          (for [[fid flabel] fields
                :let [v (get record fid)]
                :when (some? v)]
            [:tr
             [:th.bg-light.fw-semibold.small {:style "width:35%"} flabel]
             [:td.small (render-field-value v)]])]]])]))

(defn- render-parent-pane
  "First tab pane: the selected parent record detail + any one-to-one inline sections."
  [request entity-name title fields parent-row actions one-to-one-subgrids]
  [:div.tab-pane.fade.show.active
   {:id              (str entity-name "-parent-tab")
    :role            "tabpanel"
    :aria-labelledby (str entity-name "-parent-tab-link")}
   [:div.card.shadow-sm.mb-3
    [:div.card-body
     (render-parent-grid-table request entity-name title fields parent-row actions)]]
   ;; One-to-one sections appear inline below the parent record card
   (for [sg one-to-one-subgrids]
     (render-one-to-one-section request entity-name (get-record-id entity-name parent-row) sg))])

(defn- render-subgrid-pane
  "One tab pane for a single subgrid relationship."
  [request entity-name entity-title parent-display subgrid selected-parent-id]
  (let [sg-name (safe-id (name (:entity subgrid)))]
    [:div.tab-pane.fade
     {:id                  (str entity-name "-" sg-name "-tab")
      :role                "tabpanel"
      :aria-labelledby     (str entity-name "-" sg-name "-tab-link")
      :data-subgrid-entity (name (:entity subgrid))
      :data-foreign-key    (name (:foreign-key subgrid))}
     [:div.card.shadow-sm.mb-3
      (render-parent-context-header entity-title parent-display)
      [:div.card-body
       (render-subgrid-toolbar request entity-name subgrid selected-parent-id)
       (render-subgrid-table request entity-name sg-name (:title subgrid) (:fields subgrid))]]]))

;;; ---- Public rendering functions ------------------------------------

(defn render-m2m-pane
  "Body content for a many-to-many relationship (no tab/accordion wrapper).
   Shows linked records with Unlink buttons (inline confirm), plus a Link picker modal.
   Junction rows with pivot attributes get an Edit button."
  [request entity-name entity-title parent-display subgrid parent-id]
  (let [sg-name      (safe-id (name (:entity subgrid)))
        records      (or (:records subgrid) [])
        fields       (:fields subgrid)
        related-fk   (:related-fk subgrid)
        fk           (:foreign-key subgrid)
        through      (name (or (:through-table subgrid) (:entity subgrid)))
        junction     (or (:through-table subgrid) (:entity subgrid))
        modal-id     (str entity-name "-" sg-name "-link-modal")
        available    (or (:available subgrid) [])
        ;; Determine if junction table has editable pivot fields
        junction-cfg (config/get-entity-config junction)
        fk-ids       (into #{} (keep #(when % (keyword (name %))) [fk related-fk]))
        pivot-fields (remove #(or (= :hidden (:type %)) (contains? fk-ids (:id %)))
                             (:fields junction-cfg))
        has-pivot?   (seq pivot-fields)]
    [:div.m2m-pane {:data-entity        entity-name
                    :data-parent-id     (str parent-id)
                    :data-subgrid-entity (name (:entity subgrid))}
     [:div.card.shadow-sm.mb-3
      (render-parent-context-header entity-title parent-display)
      [:div.card-body
       [:div.d-flex.justify-content-between.align-items-center.mb-3
        [:h5.mb-0
         [:i.me-2 {:class (or (:icon subgrid) "bi bi-diagram-3")}]
         (:title subgrid)
         [:span.badge.bg-secondary.ms-2 (count records)]]
        ;; #4: Show Link button only when unlinked records exist; else "All linked" badge
        (if (seq available)
          [:button.btn.btn-sm.btn-primary
           {:data-bs-toggle "modal"
            :data-bs-target (str "#" modal-id)}
           [:i.bi.bi-link-45deg.me-1]
           "Link"]
          (when (seq records)
            [:span.badge.bg-success.rounded-pill
             [:i.bi.bi-check2-all.me-1] "All linked"]))]
       (if (seq records)
         [:table.table.table-hover.table-sm.table-bordered
          [:thead
           [:tr
            (for [[_ label] fields] [:th label])
            [:th {:style "width:100px"} (i18n/tr request :common/actions)]]]
          [:tbody
           (for [row records]
             (let [related-id (when related-fk (get row (keyword related-fk)))]
               [:tr
                (for [[fid _] fields]
                  [:td.small (render-field-value (get row fid))])
                [:td
                 ;; #2: Pivot edit button — shown only when junction has extra fields
                 (when has-pivot?
                   [:button.btn.btn-sm.btn-outline-secondary.edit-btn.me-1
                    {:title          "Edit attributes"
                     :data-url       (str "/tabgrid/pivot-form"
                                          "?through_table=" through
                                          "&parent_fk=" (when fk (name fk))
                                          "&parent_id=" parent-id
                                          "&related_fk=" (when related-fk (name related-fk))
                                          "&related_id=" related-id)
                     :data-bs-toggle "modal"
                     :data-bs-target "#exampleModal"}
                    [:i.bi.bi-sliders]])
                 ;; #5/#6: Unlink form — JS handles AJAX + inline confirm
                 [:form.d-inline.m2m-dissociate-form
                  {:method      "POST"
                   :action      "/tabgrid/dissociate"
                   :data-row-id (str related-id)}
                  (csrf-field)
                  [:input {:type "hidden" :name "through_table" :value through}]
                  [:input {:type "hidden" :name "parent_fk"     :value (when fk (name fk))}]
                  [:input {:type "hidden" :name "parent_id"     :value (str parent-id)}]
                  [:input {:type "hidden" :name "related_fk"    :value (when related-fk (name related-fk))}]
                  [:input {:type "hidden" :name "related_id"    :value (str related-id)}]
                  [:span.m2m-unlink-confirm {:style "display:none"}
                   [:span.small.me-1 "Unlink?"]
                   [:button.btn.btn-sm.btn-danger.m2m-confirm-yes {:type "submit"}
                    [:i.bi.bi-check]]
                   [:button.btn.btn-sm.btn-outline-secondary.m2m-confirm-no {:type "button"}
                    [:i.bi.bi-x]]]
                  [:button.btn.btn-sm.btn-outline-danger.m2m-unlink-btn
                   {:type "button" :title "Unlink"}
                   [:i.bi.bi-x-circle]]]]]))]]
         [:div.text-center.p-4.text-muted
          [:i.bi.bi-link {:style "font-size:2.5rem"}]
          [:p.mt-2 "No associations yet"]])]]
     ;; Link picker modal with #3 search filter
     [:div.modal.fade {:id modal-id :tabindex "-1"}
      [:div.modal-dialog.modal-lg
       [:div.modal-content
        [:div.modal-header.bg-primary.text-white
         [:h5.modal-title [:i.bi.bi-link-45deg.me-2] "Link " (:title subgrid)]
         [:button.btn-close.btn-close-white {:type "button" :data-bs-dismiss "modal"}]]
        [:div.modal-body
         (if (seq available)
           (into [:div
                  [:input.form-control.form-control-sm.mb-3.m2m-search-input
                   {:type "text" :placeholder "Filter…"
                    :oninput "TabGrid.filterM2MModal(this)"}]
                  [:table.table.table-hover.table-sm.m2m-available-table
                   [:thead [:tr [:th {:style "width:80px"} ""]
                            (for [[_ label] fields] [:th label])]]
                   (into [:tbody]
                         (for [row available]
                           (into [:tr
                                  [:td
                                   [:form.d-inline.m2m-associate-form
                                    {:method "POST" :action "/tabgrid/associate"}
                                    (csrf-field)
                                    [:input {:type "hidden" :name "through_table" :value through}]
                                    [:input {:type "hidden" :name "parent_fk" :value (when fk (name fk))}]
                                    [:input {:type "hidden" :name "parent_id" :value (str parent-id)}]
                                    [:input {:type "hidden" :name "related_fk" :value (when related-fk (name related-fk))}]
                                    [:input {:type "hidden" :name "related_id" :value (str (:id row))}]
                                    [:button.btn.btn-sm.btn-success {:type "submit"}
                                     [:i.bi.bi-link-45deg.me-1] "Link"]]]]
                                 (for [[fid _] fields]
                                   [:td.small (render-field-value (get row fid))]))))]]
                 [])
           [:div.text-center.p-4.text-muted
            [:i.bi.bi-check-circle {:style "font-size:2.5rem"}]
            [:p.mt-2 "All records are already linked"]])]
        [:div.modal-footer
         [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"}
          (i18n/tr request :common/close)]]]]]]))

(defn render-accordion-content
  "Accordion layout — parent record is always expanded; each relationship
   is a separate collapsible section. One-to-one subgrids appear inline
   under the parent. One-to-many subgrids lazy-load on first expand.
   Many-to-many sections are rendered server-side."
  [request entity-name title fields rows actions subgrids selected-parent-id]
  (let [parent-row     (first rows)
        label          (parent-display-label fields parent-row selected-parent-id)
        entity-config  (config/get-entity-config entity-name)
        entity-icon    (get entity-config :icon "bi bi-table")
        oto-subgrids   (filter #(= :one-to-one (:relationship-type %)) subgrids)
        other-subgrids (remove #(= :one-to-one (:relationship-type %)) subgrids)
        accordion-id   (str entity-name "-accordion")]
    [:div.accordion {:id accordion-id}
     ;; ── Parent record section (always open) ──────────────────────────
     (let [pane-id (str entity-name "-parent")]
       [:div.accordion-item
        [:h2.accordion-header {:id (str pane-id "-heading")}
         [:button.accordion-button
          {:type           "button"
           :data-bs-toggle "collapse"
           :data-bs-target (str "#" pane-id "-collapse")
           :aria-expanded  "true"
           :aria-controls  (str pane-id "-collapse")}
          [:i.me-2.text-primary {:class entity-icon}]
          [:span.fw-semibold label]
          [:span.text-muted.fw-normal.ms-2.small title]]]
        [:div.accordion-collapse.collapse.show
         {:id              (str pane-id "-collapse")
          :aria-labelledby (str pane-id "-heading")}
         [:div.accordion-body
          [:div.card.border-0.shadow-none
           [:div.card-body
            (render-parent-grid-table request entity-name title fields parent-row actions)]]
          ;; One-to-one relationships rendered inline
          (for [sg oto-subgrids]
            (render-one-to-one-section
             request entity-name (get-record-id entity-name parent-row) sg))]]])
     ;; ── Relationship sections (collapsed by default) ──────────────────
     (map (fn [sg]
            (let [sg-name (safe-id (name (:entity sg)))
                  pane-id (str entity-name "-" sg-name)
                  icon    (case (:relationship-type sg)
                            :many-to-many "bi bi-diagram-3"
                            (or (:icon sg) "bi bi-list-ul"))
                  m2m?    (= :many-to-many (:relationship-type sg))]
              [:div.accordion-item
               [:h2.accordion-header {:id (str pane-id "-heading")}
                [:button.accordion-button.collapsed
                 {:type           "button"
                  :data-bs-toggle "collapse"
                  :data-bs-target (str "#" pane-id "-collapse")
                  :aria-expanded  "false"
                  :aria-controls  (str pane-id "-collapse")}
                 [:i.me-2 {:class icon}]
                 (:title sg)
                 (when-let [cnt (:count sg)]
                   [:span.badge.bg-secondary.rounded-pill.ms-2 cnt])]]
               (if m2m?
                 [:div.accordion-collapse.collapse
                  {:id              (str pane-id "-collapse")
                   :aria-labelledby (str pane-id "-heading")}
                  [:div.accordion-body
                   (render-m2m-pane
                    request entity-name title label sg selected-parent-id)]]
                 [:div.accordion-collapse.collapse
                  {:id                  (str pane-id "-collapse")
                   :aria-labelledby     (str pane-id "-heading")
                   :data-subgrid-entity (name (:entity sg))
                   :data-foreign-key    (name (:foreign-key sg))}
                  [:div.accordion-body
                   (render-subgrid-toolbar request entity-name sg selected-parent-id)
                   (render-subgrid-table
                    request entity-name sg-name (:title sg) (:fields sg))]])]))
          other-subgrids)]))

(defn render-tab-content
  "Tab content area. Routes by :relationship-type:
   :one-to-one  → inline in parent pane
   :many-to-many → M2M pane with link/unlink UI
   :one-to-many  → existing lazy AJAX subgrid pane"
  [request entity-name title fields rows actions subgrids selected-parent-id]
  (let [parent-row     (first rows)
        label          (parent-display-label fields parent-row selected-parent-id)
        oto-subgrids   (filter #(= :one-to-one (:relationship-type %)) subgrids)
        other-subgrids (remove #(= :one-to-one (:relationship-type %)) subgrids)]
    [:div.tab-content
     (render-parent-pane request entity-name title fields parent-row actions oto-subgrids)
     (map (fn [sg]
            (if (= :many-to-many (:relationship-type sg))
              (render-m2m-pane request entity-name title label sg selected-parent-id)
              (render-subgrid-pane request entity-name title label sg selected-parent-id)))
          other-subgrids)]))

(defn render-parent-selector-modal
  "Modal for choosing a different parent record from the full list."
  [request entity-name title fields all-rows]
  [:div.modal.fade
   {:id       (str entity-name "-select-parent-modal")
    :tabindex "-1"}
   [:div.modal-dialog.modal-xl
    [:div.modal-content
     [:div.modal-header.bg-primary.text-white
      [:h5.modal-title
       [:i.bi.bi-search.me-2]
       (i18n/tr request :common/select) " " title]
      [:button.btn-close.btn-close-white
       {:type "button" :data-bs-dismiss "modal"}]]
     [:div.modal-body
      [:table.table.table-hover.table-sm.dataTable.w-100
       {:id (str entity-name "-select-table")}
       [:thead
        [:tr
         [:th (i18n/tr request :common/select)]
         (for [[_ label] fields] [:th label])]]
       [:tbody
        (for [row all-rows]
          [:tr
           [:td
            [:button.btn.btn-sm.btn-success.select-parent-btn
             {:data-parent-id  (get-record-id entity-name row)
              :data-bs-dismiss "modal"}
             [:i.bi.bi-check-circle.me-1]
             (i18n/tr request :common/select)]]
           (for [[field-id _] fields]
             [:td (render-field-value (get row field-id))])])]]]
     [:div.modal-footer
      [:button.btn.btn-secondary
       {:type "button" :data-bs-dismiss "modal"}
       (i18n/tr request :common/close)]]]]])

(defn- row-display-label
  "Pick the best human-readable label for a record in the left-panel list.
  Returns the first non-id string value found in fields order, or '#<id>' as fallback."
  [fields row]
  (or (some (fn [[fid _]]
              (when (not= fid :id)
                (let [v (get row fid)]
                  (when (and v (string? v) (seq v)) v))))
            fields)
      (str "#" (:id row))))

(defn- render-record-list-panel
  "Left panel: avatar list of all parent records with search and New button."
  [request entity-name title fields all-rows selected-parent-id actions]
  [:div.tg-list-panel
   [:div.tg-list-header
    [:div.tg-list-title
     [:span.fw-semibold title]
     [:span.tg-count-badge (count all-rows)]]
    (when (:new actions)
      [:button.btn.btn-sm.btn-primary.edit-btn
       {:data-url (str "/admin/" entity-name "/add-form")
        :title    (str "New " title)}
       [:i.bi.bi-plus-lg]])]
   [:div.tg-list-search
    [:div.input-group.input-group-sm
     [:span.input-group-text.tg-search-icon
      [:i.bi.bi-search.text-muted]]
     [:input.form-control.tg-search-input
      {:type        "search"
       :placeholder (str (i18n/tr request :common/search) "...")
       :oninput     "TabGrid.filterRecordList(this)"}]]]
   [:ul.tg-record-list
    {:id (str entity-name "-record-list")}
    (for [row all-rows
          :let [row-id   (str (get-record-id entity-name row))
                label    (row-display-label fields row)
                lstr     (str label)
                initials (str/upper-case (subs lstr 0 (min 2 (count lstr))))
                active?  (= row-id selected-parent-id)]]
      [:li.tg-record-item
       {:data-parent-id row-id
        :class          (when active? "active")}
       [:span.tg-record-avatar initials]
       [:span.tg-record-label label]])]])

(defn render-tabgrid
  "Entry point: renders the complete tabgrid interface as a master-detail split view."
  [request entity-name title fields rows all-rows actions subgrids]
  (let [first-row          (first rows)
        selected-parent-id (or (some-> (get-in request [:params :id]) str)
                               (when first-row (str (get-record-id entity-name first-row))))]
    [:div.tabgrid-container
     {:id                      (str entity-name "-tabgrid")
      :data-entity             entity-name
      :data-selected-parent-id (or selected-parent-id "")}
     [:div.tg-split
      (render-record-list-panel request entity-name title fields all-rows selected-parent-id actions)
      [:div.tg-detail-panel
       (if (seq subgrids)
         (render-accordion-content request entity-name title fields rows actions subgrids selected-parent-id)
         [:div.card.shadow-sm
          [:div.card-body
           (render-parent-grid-table request entity-name title fields first-row actions)]])]]]))
