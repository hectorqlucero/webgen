(ns {{sanitized}}.tabgrid.render
  "Clean TabGrid rendering - pure UI generation"
  (:require
   [{{sanitized}}.i18n.core :as i18n]
   [hiccup.util :refer [raw-string]]))

(defn- safe-id [s]
  "Convert string to safe HTML ID"
  (-> (str s)
      (clojure.string/lower-case)
      (clojure.string/replace #"[^a-z0-9]+" "-")))

(defn render-parent-grid-table
  "Renders the parent record as a simple detail view"
  [request entity-name title fields row actions]
  (if row
    [:div.parent-record-view
     [:table.table.table-bordered.table-hover.mb-0
      [:tbody
       (for [[field-id field-label] fields]
         (let [value (get row field-id)]
           [:tr
            [:th.bg-light.text-uppercase.fw-semibold {:style "width: 30%"} field-label]
            [:td (if (and (string? value) (re-find #"^<" value))
                   (raw-string value)
                   value)]]))
       [:tr
        [:th.bg-light.text-uppercase.fw-semibold (i18n/tr request :common/actions)]
        [:td
         [:div.btn-group.btn-group-sm
          (when (:edit actions)
            [:button.btn.btn-warning.btn-sm.edit-btn
             {:data-id (:id row)
              :data-url (str "/admin/" entity-name "/edit-form/" (:id row))
              :data-bs-toggle "modal"
              :data-bs-target "#exampleModal"}
             [:i.bi.bi-pencil.me-1]
             (i18n/tr request :common/edit)])
          (when (:delete actions)
            [:a.btn.btn-danger.btn-sm
             {:href (str "/admin/" entity-name "/delete/" (:id row))}
             [:i.bi.bi-trash.me-1]
             (i18n/tr request :common/delete)])]]]]]]
    [:div.text-center.p-4
     [:i.bi.bi-inbox.text-muted {:style "font-size: 3rem"}]
     [:p.text-muted.mt-2 (i18n/tr request :grid/no-records)]]))

(defn render-subgrid-table
  "Renders a subgrid table placeholder"
  [request entity-name sg-name title fields]
  [:div
   [:div.subgrid-loading.text-center.p-4
    [:div.spinner-border.text-primary {:role "status"}]
    [:p.mt-2 (i18n/tr request :common/loading)]]
   [:div.subgrid-table-wrapper {:style "display:none"}
    [:table.table.table-hover.table-bordered.table-sm.dataTable.w-100
     {:id (str entity-name "-" sg-name "-table")}
     [:thead [:tr (for [[_ label] fields] [:th label]) [:th "Actions"]]]
     [:tbody]]]])

(defn render-tab-nav
  "Renders Bootstrap tab navigation"
  [request entity-name title subgrids]
  [:ul.nav.nav-tabs.mb-3 {:role "tablist"}
   [:li.nav-item
    [:a.nav-link.active
     {:id (str entity-name "-parent-tab-link")
      :data-bs-toggle "tab"
      :data-bs-target (str "#" entity-name "-parent-tab")
      :href (str "#" entity-name "-parent-tab")
      :role "tab"
      :aria-controls (str entity-name "-parent-tab")
      :aria-selected "true"}
     [:i.bi.bi-table.me-2] title]]
   (for [subgrid subgrids]
     [:li.nav-item
      [:a.nav-link
       {:id (str entity-name "-" (safe-id (name (:entity subgrid))) "-tab-link")
        :data-bs-toggle "tab"
        :data-bs-target (str "#" entity-name "-" (safe-id (name (:entity subgrid))) "-tab")
        :href (str "#" entity-name "-" (safe-id (name (:entity subgrid))) "-tab")
        :role "tab"
        :aria-controls (str entity-name "-" (safe-id (name (:entity subgrid))) "-tab")}
       [:i.me-2 {:class (or (:icon subgrid) "bi bi-list-ul")}]
       (:title subgrid)]])])

(defn render-tab-content
  "Renders Bootstrap tab content panes"
  [request entity-name title fields rows actions subgrids selected-parent-id]
  [:div.tab-content
   [:div.tab-pane.fade.show.active
    {:id (str entity-name "-parent-tab")
     :role "tabpanel"
     :aria-labelledby (str entity-name "-parent-tab-link")}
    [:div.card.shadow-sm.mb-3
     [:div.card-body
      (render-parent-grid-table request entity-name title fields (first rows) actions)]]]
   (for [subgrid subgrids]
     (let [sg-name (safe-id (name (:entity subgrid)))]
       [:div.tab-pane.fade
        {:id (str entity-name "-" sg-name "-tab")
         :role "tabpanel"
         :aria-labelledby (str entity-name "-" sg-name "-tab-link")
         :data-subgrid-entity (name (:entity subgrid))
         :data-foreign-key (name (:foreign-key subgrid))}
        [:div.card.shadow-sm.mb-3
         [:div.card-body
          [:div.d-flex.justify-content-between.align-items-center.mb-3
           [:h5.mb-0
            [:i.me-2 {:class (or (:icon subgrid) "bi bi-list-ul")}]
            (:title subgrid)]
           [:button.btn.btn-sm.btn-primary.add-subgrid-btn
            {:data-subgrid-entity (name (:entity subgrid))
             :data-parent-id (str selected-parent-id)}
            [:i.bi.bi-plus-circle.me-1]
            (i18n/tr request :common/new)]]
          (render-subgrid-table request entity-name sg-name (:title subgrid) (:fields subgrid))]]]))])

(defn render-parent-selector-modal
  "Renders modal for selecting a different parent record"
  [request entity-name title fields all-rows]
  [:div.modal.fade
   {:id (str entity-name "-select-parent-modal")
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
             {:data-parent-id (:id row) :data-bs-dismiss "modal"}
             [:i.bi.bi-check-circle.me-1]
             (i18n/tr request :common/select)]]
           (for [[field-id _] fields]
             (let [value (get row field-id)]
               [:td (if (and (string? value) (re-find #"^<" value))
                      (raw-string value)
                      value)]))])]]]
     [:div.modal-footer
      [:button.btn.btn-secondary
       {:type "button" :data-bs-dismiss "modal"}
       (i18n/tr request :common/close)]]]]])

(defn render-tabgrid-header
  "Renders header with title and actions"
  [request entity-name title row-count actions]
  [:div.card.shadow-sm.mb-3
   [:div.card-body
    [:div.d-flex.justify-content-between.align-items-center
     [:div
      [:h3.mb-0
       [:i.bi.bi-layers.me-2]
       title
       [:span.badge.bg-secondary.ms-2 (str row-count " " (i18n/tr request :grid/items))]]]
     [:div.btn-group
      [:button.btn.btn-outline-primary
       {:data-bs-toggle "modal"
        :data-bs-target (str "#" entity-name "-select-parent-modal")}
       [:i.bi.bi-search.me-1]
       (i18n/tr request :common/select)]
      (when (:new actions)
        [:a.btn.btn-success
         {:href (str "/admin/" entity-name "/add-form")
          :data-bs-toggle "modal"
          :data-bs-target "#exampleModal"}
         [:i.bi.bi-plus-circle.me-1]
         (i18n/tr request :common/new)])
      [:button.btn.btn-outline-secondary
       {:onclick "location.reload()"}
       [:i.bi.bi-arrow-clockwise.me-1]
       (i18n/tr request :common/back)]]]]])

(defn render-tabgrid
  "Main function: renders complete tabgrid interface"
  [request entity-name title fields rows all-rows actions subgrids]
  (let [has-subgrids? (seq subgrids)
        first-row (first rows)
        selected-parent-id (or (when-let [id-param (get-in request [:params :id])]
                                 (str id-param))
                               (when first-row (str (:id first-row))))]
    [:div.tabgrid-container
     {:id (str entity-name "-tabgrid")
      :data-entity entity-name
      :data-selected-parent-id (or selected-parent-id "")}
     (render-tabgrid-header request entity-name title (count all-rows) actions)
     (if has-subgrids?
       [:div
        (render-tab-nav request entity-name title subgrids)
        (render-tab-content request entity-name title fields rows actions subgrids selected-parent-id)]
       [:div.card.shadow-sm
        [:div.card-body
         (render-parent-grid-table request entity-name title fields first-row actions)]])
     (render-parent-selector-modal request entity-name title fields all-rows)]))
