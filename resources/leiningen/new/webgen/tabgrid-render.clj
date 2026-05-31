(ns {{sanitized}}.tabgrid.render
  "Entity Workspace -- navigator + pinned record header + relationship tabs."
  (:require
   [clojure.string :as str]
   [{{sanitized}}.i18n.core :as i18n]
   [hiccup.util :refer [raw-string]]
   [{{sanitized}}.engine.config :as config]
   [{{sanitized}}.web.csrf :refer [csrf-field]]))

;;; -- Utilities -------------------------------------------------------

(defn- safe-id [s]
  (-> (str s) str/lower-case (str/replace #"[^a-z0-9]+" "-")))

(defn get-record-id
  "Returns the primary key; composite PKs are pipe-joined."
  [entity-name row]
  (try
    (let [pk (:primary-key (config/get-entity-config entity-name))]
      (if (and pk (vector? pk) (> (count pk) 1))
        (str/join "|" (map #(get row %) pk))
        (:id row)))
    (catch Exception _ (:id row))))

(defn parent-display-label
  "First non-id string value, falling back to \"#<id>\"."
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

(defn- row-display-label [fields row]
  (or (some (fn [[fid _]]
              (when (not= fid :id)
                (let [v (get row fid)]
                  (when (and v (string? v) (seq v)) v))))
            fields)
      (str "#" (:id row))))

(defn- row-secondary-label [fields row]
  (second
   (keep (fn [[fid _]]
           (when (not= fid :id)
             (let [v (get row fid)]
               (when (and v (string? v) (seq v)) v))))
         fields)))

(defn- ws-initials [s]
  (let [s (str s)]
    (str/upper-case (subs s 0 (min 2 (count s))))))

;;; -- Navigator (left panel) ------------------------------------------

(defn- render-navigator
  [request entity-name title fields all-rows selected-parent-id actions]
  [:aside.ws-nav
   [:div.ws-nav-header
    [:div.ws-nav-title
     [:span title]
     [:span.ws-count-badge (count all-rows)]]
    (when (:new actions)
      [:button.ws-new-btn.edit-btn
       {:data-url (str "/admin/" entity-name "/add-form")
        :title    (str (i18n/tr request :common/new) " " title)}
       [:i.bi.bi-plus-lg]])]
   [:div.ws-nav-search
    [:div.ws-search-wrap
     [:i.bi.bi-search.ws-search-icon]
     [:input.ws-search-input
      {:type        "search"
       :placeholder (str (i18n/tr request :common/search) "...")
       :oninput     "TabGrid.filterRecordList(this)"}]]]
   [:ul.ws-record-list
    {:id (str entity-name "-record-list")}
    (for [row    all-rows
          :let   [row-id    (str (get-record-id entity-name row))
                  label     (row-display-label fields row)
                  secondary (row-secondary-label fields row)
                  active?   (= row-id (str selected-parent-id))]]
      [:li.ws-record-item
       {:data-parent-id row-id
        :class          (when active? "active")}
       [:span.ws-avatar (ws-initials label)]
       [:div.ws-record-info
        [:span.ws-record-label label]
        (when secondary [:span.ws-record-secondary secondary])]])]])

;;; -- Pinned record header --------------------------------------------

(defn- confirm-js
  "JS confirm() string for delete onsubmit."
  [request]
  (str "return confirm('" (i18n/tr request :confirm/delete) "')"))

(defn- field-display-value
  "Resolved display value — runs compute-fn for computed fields."
  [entity-name field-id row]
  (let [cfg (first (filter #(= (:id %) field-id)
                           (:fields (config/get-entity-config entity-name))))]
    (if (= :computed (:type cfg))
      (some-> (:compute-fn cfg) (apply [row]))
      (get row field-id))))

(defn- render-field-pair
  "Single label/value field cell for the fields grid."
  [entity-name field-id field-label row]
  [:div.ws-field
   [:span.ws-field-label field-label]
   [:span.ws-field-value
    (let [v (render-field-value (field-display-value entity-name field-id row))]
      (if (or (nil? v) (= v ""))
        [:span.text-muted "—"]
        v))]])

(defn- render-record-header
  [request entity-name title fields row actions]
  (if-not row
    [:div.ws-empty-state [:i.bi.bi-inbox] [:p (i18n/tr request :grid/no-records)]]
    (let [label (parent-display-label fields row nil)
          rid   (get-record-id entity-name row)]
      [:div.ws-record-header
       [:div.ws-record-hero
        [:div.ws-hero-avatar (ws-initials label)]
        [:div.ws-hero-meta
         [:h2.ws-hero-name label]
         [:span.ws-hero-id [:i.bi.bi-hash] rid [:span.ms-2.fw-normal.text-muted title]]]
        [:div.ws-hero-actions
         (when (:edit actions)
           [:button.btn.btn-sm.btn-primary.edit-btn
            {:data-url (str "/admin/" entity-name "/edit-form/" rid)
             :data-bs-toggle "modal" :data-bs-target "#exampleModal"}
            [:i.bi.bi-pencil.me-1] (i18n/tr request :common/edit)])
         (when (:delete actions)
           [:form.d-inline
            {:method "POST"
             :action (str "/admin/" entity-name "/delete/" rid)
             :onsubmit (confirm-js request)}
            (csrf-field)
            [:button.btn.btn-sm.btn-outline-danger {:type "submit"}
             [:i.bi.bi-trash.me-1] (i18n/tr request :common/delete)]])]]
       [:div.ws-fields-grid
        (for [[field-id field-label] fields]
          (render-field-pair entity-name field-id field-label row))]])))

;;; -- 1:1 pane --------------------------------------------------------

(defn- render-o2o-pane
  [request parent-entity-name parent-id subgrid]
  (let [sg-name (name (:entity subgrid))
        record  (:record subgrid)
        fields  (:fields subgrid)
        actions (:actions subgrid)]
    [:div.ws-o2o-card
     [:div.ws-o2o-header
      [:span.ws-o2o-title
       [:i.me-1 {:class (or (:icon subgrid) "bi bi-person-vcard")}]
       (:title subgrid)]
      [:div.d-flex.align-items-center.gap-2
       (if record
         [:span.ws-o2o-status-linked
          [:i.bi.bi-check-circle-fill.me-1] "Linked"]
         [:span.ws-o2o-status-unlinked
          [:i.bi.bi-dash-circle.me-1] "Not set"])
       (if record
         (when (:edit actions)
           [:button.btn.btn-sm.btn-outline-primary.edit-btn
            {:data-url       (str "/admin/" sg-name "/edit-form/" (:id record))
             :data-bs-toggle "modal"
             :data-bs-target "#exampleModal"}
            [:i.bi.bi-pencil.me-1] (i18n/tr request :common/edit)])
         [:button.btn.btn-sm.btn-outline-primary.edit-btn
          {:data-url       (str "/admin/" sg-name "/add-form"
                                "?parent_id=" parent-id
                                "&parent_entity=" parent-entity-name)
           :data-bs-toggle "modal"
           :data-bs-target "#exampleModal"}
          [:i.bi.bi-plus-circle.me-1]
          (str "Create " (:title subgrid))])]]
     (if record
       [:div.ws-o2o-body
        (for [[fid flabel] fields
              :let [v (get record fid)]
              :when (some? v)]
          [:div.ws-o2o-field
           [:span.ws-o2o-field-label flabel]
           [:span.ws-o2o-field-value (render-field-value v)]])]
       [:div.ws-o2o-empty
        [:i.bi.bi-dash-circle.me-2]
        "No " (:title subgrid) " linked yet"])]))

;;; -- 1:M pane --------------------------------------------------------

(defn render-subgrid-table
  "Spinner placeholder replaced by JS on first tab activation."
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

(defn- render-otm-pane
  [request entity-name subgrid selected-parent-id]
  (let [sg-name (safe-id (name (:entity subgrid)))]
    [:div.ws-pane-card
     [:div.ws-pane-toolbar
      [:span.ws-pane-title
       [:i.me-1 {:class (or (:icon subgrid) "bi bi-list-ul")}]
       (:title subgrid)]
      [:button.btn.btn-sm.btn-success.add-subgrid-btn
       {:data-subgrid-entity (name (:entity subgrid))
        :data-parent-id      (str selected-parent-id)
        :data-parent-entity  entity-name}
       [:i.bi.bi-plus-circle.me-1] (i18n/tr request :common/new)]]
     (render-subgrid-table request entity-name sg-name
                           (:title subgrid) (:fields subgrid))]))

;;; -- M2M pane --------------------------------------------------------

(defn- render-m2m-row
  "One <tr> for a linked M2M record."
  [fields has-pivot? through fk related-fk parent-id row]
  (let [related-id (when related-fk (get row (keyword related-fk)))]
    (into [:tr]
          (concat
           (for [[fid _] fields]
             [:td.small (render-field-value (get row fid))])
           [[:td
             (when has-pivot?
               [:button.btn.btn-sm.btn-outline-secondary.edit-btn.me-1
                {:title          "Edit attributes"
                 :data-url       (str "/tabgrid/pivot-form"
                                      "?through_table=" through
                                      "&parent_fk="  (when fk (name fk))
                                      "&parent_id="  parent-id
                                      "&related_fk=" (when related-fk (name related-fk))
                                      "&related_id=" related-id)
                 :data-bs-toggle "modal"
                 :data-bs-target "#exampleModal"}
                [:i.bi.bi-sliders]])
             [:form.d-inline.m2m-dissociate-form
              {:method      "POST"
               :action      "/tabgrid/dissociate"
               :data-row-id (str related-id)}
              (csrf-field)
              [:input {:type "hidden" :name "through_table"
                       :value through}]
              [:input {:type "hidden" :name "parent_fk"
                       :value (when fk (name fk))}]
              [:input {:type "hidden" :name "parent_id"
                       :value (str parent-id)}]
              [:input {:type "hidden" :name "related_fk"
                       :value (when related-fk (name related-fk))}]
              [:input {:type "hidden" :name "related_id"
                       :value (str related-id)}]
              [:span.m2m-unlink-confirm {:style "display:none"}
               [:span.small.me-1 "Unlink?"]
               [:button.btn.btn-sm.btn-danger.m2m-confirm-yes
                {:type "submit"} [:i.bi.bi-check]]
               [:button.btn.btn-sm.btn-outline-secondary.m2m-confirm-no
                {:type "button"} [:i.bi.bi-x]]]
              [:button.btn.btn-sm.btn-outline-danger.m2m-unlink-btn
               {:type "button" :title "Unlink"}
               [:i.bi.bi-x-circle]]]]]))))

(defn- render-m2m-avail-row
  "One <tr> for an available (not yet linked) M2M record."
  [fields through fk related-fk parent-id row]
  (into [:tr
         [:td
          [:form.d-inline.m2m-associate-form
           {:method "POST" :action "/tabgrid/associate"}
           (csrf-field)
           [:input {:type "hidden" :name "through_table"
                    :value through}]
           [:input {:type "hidden" :name "parent_fk"
                    :value (when fk (name fk))}]
           [:input {:type "hidden" :name "parent_id"
                    :value (str parent-id)}]
           [:input {:type "hidden" :name "related_fk"
                    :value (when related-fk (name related-fk))}]
           [:input {:type "hidden" :name "related_id"
                    :value (str (:id row))}]
           [:button.btn.btn-sm.btn-success {:type "submit"}
            [:i.bi.bi-link-45deg.me-1] "Link"]]]]
        (for [[fid _] fields]
          [:td.small (render-field-value (get row fid))])))

(defn render-m2m-pane
  "M2M pane: linked table + inline unlink + link picker modal."
  [request entity-name _entity-title _parent-display subgrid parent-id]
  (let [sg-name      (safe-id (name (:entity subgrid)))
        records      (or (:records subgrid) [])
        fields       (:fields subgrid)
        related-fk   (:related-fk subgrid)
        fk           (:foreign-key subgrid)
        through      (name (or (:through-table subgrid) (:entity subgrid)))
        junction     (or (:through-table subgrid) (:entity subgrid))
        modal-id     (str entity-name "-" sg-name "-link-modal")
        available    (or (:available subgrid) [])
        junction-cfg (config/get-entity-config junction)
        fk-ids       (into #{} (keep #(when % (keyword (name %)))
                                     [fk related-fk]))
        has-pivot?   (seq (remove #(or (= :hidden (:type %))
                                       (contains? fk-ids (:id %)))
                                  (:fields junction-cfg)))]
    [:div.m2m-pane
     {:data-entity         entity-name
      :data-parent-id      (str parent-id)
      :data-subgrid-entity (name (:entity subgrid))}
     [:div.ws-pane-card
      [:div.ws-m2m-toolbar
       [:span.ws-m2m-title
        [:i.me-2 {:class (or (:icon subgrid) "bi bi-diagram-3")}]
        (:title subgrid)
        [:span.ws-m2m-count (count records)]]
       (if (seq available)
         [:button.btn.btn-sm.btn-primary
          {:data-bs-toggle "modal"
           :data-bs-target (str "#" modal-id)}
          [:i.bi.bi-link-45deg.me-1] "Link"]
         (when (seq records)
           [:span.badge.bg-success.rounded-pill
            [:i.bi.bi-check2-all.me-1] "All linked"]))]
      (if (seq records)
        (into [:table.table.table-hover.table-sm.table-bordered.mb-0
               [:thead
                [:tr
                 (for [[_ label] fields] [:th label])
                 [:th {:style "width:100px"}
                  (i18n/tr request :common/actions)]]]]
              (cons
               (into [:tbody]
                     (map (partial render-m2m-row
                                   fields has-pivot? through
                                   fk related-fk parent-id)
                          records))
               []))
        [:div.text-center.p-4.text-muted
         [:i.bi.bi-link {:style "font-size:2rem"}]
         [:p.mt-2 "No associations yet"]])]
     [:div.modal.fade {:id modal-id :tabindex "-1"}
      [:div.modal-dialog.modal-lg
       [:div.modal-content
        [:div.modal-header.ws-link-modal-header
         [:h5.modal-title
          [:i.bi.bi-link-45deg.me-2] "Link " (:title subgrid)]
         [:button.btn-close.btn-close-white
          {:type "button" :data-bs-dismiss "modal"}]]
        [:div.modal-body
         (if (seq available)
           [:div
            [:input.form-control.form-control-sm.mb-3.m2m-search-input
             {:type "text" :placeholder "Filter..."
              :oninput "TabGrid.filterM2MModal(this)"}]
            (into [:table.table.table-hover.table-sm.m2m-available-table
                   [:thead
                    [:tr
                     [:th {:style "width:80px"} ""]
                     (for [[_ label] fields] [:th label])]]]
                  (cons
                   (into [:tbody]
                         (map (partial render-m2m-avail-row
                                       fields through fk related-fk parent-id)
                              available))
                   []))]
           [:div.text-center.p-4.text-muted
            [:i.bi.bi-check-circle {:style "font-size:2rem"}]
            [:p.mt-2 "All records are already linked"]])]
        [:div.modal-footer
         [:button.btn.btn-secondary
          {:type "button" :data-bs-dismiss "modal"}
          (i18n/tr request :common/close)]]]]]]))

;;; -- Tab strip + panes -----------------------------------------------

(defn- tab-cls [rel-type idx]
  (str (case rel-type
         :one-to-one   "ws-tab ws-tab-o2o"
         :many-to-many "ws-tab ws-tab-m2m"
         "ws-tab ws-tab-otm")
       (when (= idx 0) " active")))

(defn- tab-icon-cls [rel-type icon]
  (case rel-type
    :many-to-many "bi bi-diagram-3"
    :one-to-one   "bi bi-person-vcard"
    (or icon "bi bi-list-ul")))

(defn- render-tab-strip [entity-name subgrids]
  (into [:div.ws-tab-strip {:role "tablist"}]
        (map-indexed
         (fn [idx sg]
           (let [sg-name (safe-id (name (:entity sg)))
                 pane-id (str entity-name "-" sg-name "-pane")]
             [:button
              {:class     (tab-cls (:relationship-type sg) idx)
               :role      "tab"
               :data-pane (str "#" pane-id)}
              [:i.me-1 {:class (tab-icon-cls (:relationship-type sg) (:icon sg))}]
              (:title sg)
              (when-let [cnt (:count sg)]
                [:span.ws-tab-count cnt])]))
         subgrids)))

(defn- render-tab-panes
  [request entity-name subgrids selected-parent-id]
  (into [:div.ws-tab-content]
        (map-indexed
         (fn [idx sg]
           (let [sg-name  (safe-id (name (:entity sg)))
                 pane-id  (str entity-name "-" sg-name "-pane")
                 rel-type (:relationship-type sg)]
             [:div
              {:id                  pane-id
               :class               (str "ws-tab-pane" (when (= idx 0) " active"))
               :data-subgrid-entity (when (= :one-to-many rel-type)
                                      (name (:entity sg)))
               :data-foreign-key    (when (and (= :one-to-many rel-type)
                                               (:foreign-key sg))
                                      (name (:foreign-key sg)))}
              (case rel-type
                :one-to-one
                (render-o2o-pane request entity-name selected-parent-id sg)
                :many-to-many
                (render-m2m-pane request entity-name nil nil sg selected-parent-id)
                (render-otm-pane request entity-name sg selected-parent-id))]))
         subgrids)))

;;; -- Public API ------------------------------------------------------

(defn render-parent-grid-table
  "Backward-compat: standalone parent record card."
  [request entity-name _title fields row actions]
  (if-not row
    [:div.ws-empty-state [:i.bi.bi-inbox] [:p (i18n/tr request :grid/no-records)]]
    (let [rid (get-record-id entity-name row)]
      [:div
       [:div.d-flex.align-items-center.gap-2.p-2.border-bottom
        [:small.text-muted [:i.bi.bi-hash] rid]
        [:div.ms-auto.d-flex.gap-2
         (when (:edit actions)
           [:button.btn.btn-sm.btn-primary.edit-btn
            {:data-url (str "/admin/" entity-name "/edit-form/" rid)
             :data-bs-toggle "modal" :data-bs-target "#exampleModal"}
            [:i.bi.bi-pencil.me-1] (i18n/tr request :common/edit)])
         (when (:delete actions)
           [:form.d-inline
            {:method "POST"
             :action (str "/admin/" entity-name "/delete/" rid)
             :onsubmit (confirm-js request)}
            (csrf-field)
            [:button.btn.btn-sm.btn-outline-danger {:type "submit"}
             [:i.bi.bi-trash.me-1] (i18n/tr request :common/delete)]])]]
       [:div.ws-fields-grid
        (for [[field-id field-label] fields]
          (render-field-pair entity-name field-id field-label row))]])))

(defn render-accordion-content
  "Legacy name -- now renders workspace tab content."
  [request entity-name title fields rows actions subgrids selected-parent-id]
  (let [parent-row (first rows)]
    [:div.ws-main
     (render-record-header request entity-name title fields parent-row actions)
     (when (seq subgrids)
       [:div.ws-tabs-container
        (render-tab-strip entity-name subgrids)
        (render-tab-panes request entity-name subgrids selected-parent-id)])]))

(defn render-tab-content
  "Legacy alias."
  [request entity-name title fields rows actions subgrids selected-parent-id]
  (render-accordion-content request entity-name title fields rows actions
                            subgrids selected-parent-id))

(defn render-parent-selector-modal
  "Modal to pick a parent record."
  [request entity-name title fields all-rows]
  [:div.modal.fade
   {:id (str entity-name "-select-parent-modal") :tabindex "-1"}
   [:div.modal-dialog.modal-xl
    [:div.modal-content
     [:div.modal-header.bg-primary.text-white
      [:h5.modal-title
       [:i.bi.bi-search.me-2] (i18n/tr request :common/select) " " title]
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
             [:i.bi.bi-check-circle.me-1] (i18n/tr request :common/select)]]
           (for [[field-id _] fields]
             [:td (render-field-value (get row field-id))])])]]]
     [:div.modal-footer
      [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"}
       (i18n/tr request :common/close)]]]]])

(defn render-tabgrid
  "Entry point: full Entity Workspace."
  [request entity-name title fields rows all-rows actions subgrids]
  (let [first-row          (first rows)
        selected-parent-id (or (some-> (get-in request [:params :id]) str)
                               (when first-row
                                 (str (get-record-id entity-name first-row))))]
    [:div.tabgrid-container
     {:id                      (str entity-name "-tabgrid")
      :data-entity             entity-name
      :data-selected-parent-id (or selected-parent-id "")}
     [:div.ws-layout
      (render-navigator request entity-name title fields
                        all-rows selected-parent-id actions)
      [:div.ws-main
       (render-record-header request entity-name title fields
                             first-row actions)
       (if (seq subgrids)
         [:div.ws-tabs-container
          (render-tab-strip entity-name subgrids)
          (render-tab-panes request entity-name subgrids selected-parent-id)]
         [:div.ws-tabs-container
          [:div.ws-empty-state
           [:i.bi.bi-diagram-2]
           [:p "No relationships configured"]]])]]]))