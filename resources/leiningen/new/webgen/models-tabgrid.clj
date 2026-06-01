(ns {{sanitized}}.models.tabgrid
  "Optional tabbed interface for grids with subgrids"
  (:require
   [clojure.string :as str]
   [cheshire.core :as json]
   [{{sanitized}}.models.grid :refer [build-grid build-grid-with-custom-new]]))

;; Relationship SVG icon helper
(defn rel-svg-icon [rel-type]
  (case rel-type
    :1to1 [:span.ws-rel-pill "1:1"]
    :1ton [:span.ws-rel-pill "1:N"]
    :mton [:span.ws-rel-pill "N:M"]
    nil))

;; Relationship legend button and popup
(def rel-legend-btn
  [:li.nav-item.float-end.ms-auto.d-flex.align-items-center
   [:button.btn.btn-sm.btn-outline-secondary.ms-2 {:type "button" :data-bs-toggle "modal" :data-bs-target "#rel-legend-modal" :tabIndex -1 :title "Relationship legend"}
    [:span.d-none.d-md-inline "Rel legend"]
    [:span.d-md-none "?"]]])

(def rel-legend-popup
  [:div.modal.fade {:id "rel-legend-modal" :tabIndex -1 :role "dialog" :aria-labelledby "rel-legend-modal-label" :aria-hidden "true"}
   [:div.modal-dialog.modal-dialog-centered {:role "document"}
    [:div.modal-content
     [:div.modal-header
      [:h5.modal-title {:id "rel-legend-modal-label"} "Relationship Legend"]
      [:button.btn-close {:type "button" :data-bs-dismiss "modal" :aria-label "Close"}]]
     [:div.modal-body
      [:ul.list-unstyled.mb-0
       [:li.mb-2 [:span.me-2 (rel-svg-icon :1to1)] "1:1 (One to One)"]
       [:li.mb-2 [:span.me-2 (rel-svg-icon :1ton)] "1:N (One to Many)"]
       [:li.mb-2 [:span.me-2 (rel-svg-icon :mton)] "N:M (Many to Many)"]]]
     [:div.modal-footer
      [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"} "Close"]]]]])

(defn- safe-id [s]
  (-> (or s "")
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"(-)+" "-")
      (str/replace #"(^-|-$)" "")))

(defn- parent-id [row]
  (when (map? row)
    (or (:contacto_id row) (:contacto-id row) (:id row) (some-> (keys row) first))))

(defn- fields-map [dbfields labels]
  (if (and (seq dbfields) (seq labels))
    (apply array-map (interleave dbfields labels))
    {}))

(def ^:private onclick-fn
  "event.preventDefault(); (function(){var id=this.dataset.target; localStorage.setItem('active-tab', id); document.querySelectorAll('.nav-tabs .nav-link').forEach(function(x){x.classList.remove('active');});this.classList.add('active');document.querySelectorAll('.tab-pane').forEach(function(p){p.classList.remove('show','active');});var t=document.getElementById(id); if(t){t.classList.add('show','active');} if(history && history.replaceState){history.replaceState(null,null,'#'+id);}}).call(this);")

(defn- build-tab-nav-item [nav-id pane-id label active? & [rel-type]]
  [:li.nav-item {:role "presentation"}
   [:a.nav-link
    (merge
     {:id nav-id
      :class (when active? "active")
      :data-bs-toggle "tab"
      :href (str "#" pane-id)
      :role "tab"
      :aria-controls pane-id
      :aria-selected (if active? "true" "false")
      :data-target pane-id
      :onclick onclick-fn})
    (when rel-type (rel-svg-icon rel-type))
    label]])

(defn- build-tab-pane [pane-id nav-id active? content]
  [:div.tab-pane.fade
   (merge {:id pane-id
           :role "tabpanel"
           :aria-labelledby nav-id}
          (when active? {:class "show active"}))
   content])

(defn- parent-table-modal [parent-table dbfields labels all-records]
  (let [table-id (str "select-" (safe-id parent-table) "-modal-table")
        modal-id (str "select-" (safe-id parent-table) "-modal")]
    [:div
     [:button.btn.btn-outline-primary.mb-3 {:type "button" :data-bs-toggle "modal" :data-bs-target (str "#" modal-id)}
      "Select " (str/capitalize (name parent-table))]
     [:div.modal.fade {:id modal-id :tabIndex -1 :role "dialog" :aria-labelledby (str modal-id "Label") :aria-hidden "true"}
      [:div.modal-dialog.modal-xl {:role "document"}
       [:div.modal-content
        [:div.modal-header
         [:h5.modal-title {:id (str modal-id "Label")} "Select " (str/capitalize (name parent-table))]
         [:button.btn-close {:type "button" :data-bs-dismiss "modal" :aria-label "Close"}]]
        [:div.modal-body
         [:table.table.table-striped.table-bordered.w-100 {:id table-id}
          [:thead
           [:tr
            [:th "Select"]
            (for [label labels] [:th label])]]
          [:tbody]]]
        [:div.modal-footer
         [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"} "Close"]]]]]
     [:script
      (str
       "document.addEventListener('shown.bs.modal', function(e){\n"
       "  if(e.target && e.target.id==='" modal-id "'){\n"
       "    var tableEl = $('#" table-id "');\n"
       "    if ($.fn.DataTable.isDataTable(tableEl)) {\n"
       "      tableEl.DataTable().clear().destroy();\n"
       "      tableEl.find('tbody').empty();\n"
       "    }\n"
       "    var data = " (json/generate-string all-records) ";\n"
       "    tableEl.DataTable({\n"
       "      data: data,\n"
       "      columns: [\n"
       "        { data: null, render: function(data,type,row){ return '<form method=\"get\" style=\"display:inline\"><input type=\"hidden\" name=\"id\" value=\"'+row.id+'\"/><button class=\"btn btn-sm btn-success\" type=\"submit\">Select</button></form>'; } },\n"
       (clojure.string/join ",\n" (map #(str "        { data: '" (name %) "' }") dbfields))
       "      ]\n"
       "    });\n"
       "  }\n"
       "});")]]))

(defn- normalize-child [parent-table parent-id idx spec]
  (when-not (map? spec)
    (throw (ex-info "Child tab spec must be a map with at least :table and :fkey/:fk" {:spec spec})))
  (let [table (or (:table spec) (throw (ex-info "child spec missing :table" {:spec spec})))
        title (or (:title spec) (str/capitalize (name table)))
        fk (cond
             (contains? spec :fkey) (:fkey spec)
             (contains? spec :fk) (:fk spec)
             :else (throw (ex-info "Child tab missing required :fkey or :fk param" {:spec spec :table table :parent-table parent-table})))
        dbfields (or (:dbfields spec) [])
        labels (or (:labels spec) (mapv name dbfields))
        args (or (:args spec) {:new true :edit true :delete true})
        href (or (:href spec) (str "/admin/" table))
        new-href (or (:new-url spec) (when parent-id (str href "/add-form/" parent-id)))
        base (str (safe-id parent-table) "-" (safe-id (str parent-id)) "-" (safe-id title) "-" idx)
        pane-id (str base "-pane")
        nav-id (str base "-link")
        fields (fields-map dbfields labels)
        grid-fn (or (:grid-fn spec) build-grid-with-custom-new)
        ;; parent-id-kw (or (:parent-id-kw spec) :parent-id) ; removed unused binding
        parent-id-val parent-id
        fetch-args (or (:fetch-args spec) [parent-id-val])
        rows (cond
               (:rows spec) (:rows spec)
               (:fetch-fn spec) (apply (:fetch-fn spec) fetch-args)
               (:rows-fn spec) ((:rows-fn spec) parent-id-val)
               :else nil)]
    (merge
     {:id base
      :table table :title title :dbfields dbfields :labels labels :args args :rows rows
      :href href :new-href new-href :pane-id pane-id :nav-id nav-id :fields fields :grid-fn grid-fn :lazy? (boolean (:lazy spec))
      :fk fk
      :parent-id parent-id-val}
     (dissoc spec :rows :fetch-fn :fetch-args :rows-fn :table :fk :fkey :href :new-url :dbfields :labels :args :grid-fn :lazy :title :parent-id-kw))))

(defn- normalize-parent [parent-table parent-row spec]
  (let [p-id (parent-id parent-row)
        title (or (:title spec) (str/capitalize (name parent-table)))
        dbfields (or (:dbfields spec) [:name :email :phone :imagen])
        labels (or (:labels spec) ["Name" "Email" "Phone" "Imagen"])
        args (or (:args spec) {:new true :edit true :delete true})
        href (or (:href spec) (str "/admin/" parent-table))
        base (str (safe-id parent-table) "-" (safe-id (str p-id)) "-parent")
        pane-id (str base "-pane")
        nav-id (str base "-link")
        fields (fields-map dbfields labels)
        grid-fn (or (:grid-fn spec) build-grid)
        rows (cond
               (:rows spec) (:rows spec)
               (:fetch-fn spec) ((:fetch-fn spec))
               :else [parent-row])]
    {:id base :title title :dbfields dbfields :labels labels :args args :href href :fields fields :grid-fn grid-fn :rows rows :pane-id pane-id :nav-id nav-id
     :fetch-fn (:fetch-fn spec)
     :get-all-fn (:get-all-fn spec)}))

(defn build-tabs [parent-table parent-row parent-spec child-specs]
  (let [p-id (parent-id parent-row)
        parent-conf (normalize-parent parent-table parent-row parent-spec)
        parent-title (:title parent-conf)
        parent-fields (:fields parent-conf)
        parent-href (:href parent-conf)
        parent-args (:args parent-conf)
        parent-dbfields (:dbfields parent-conf)
        parent-labels (:labels parent-conf)
        grid-row (if (> (count parent-row) 0)
                   [parent-row]
                   nil)
        all-parent-records (if (fn? (:get-all-fn parent-conf)) (apply (:get-all-fn parent-conf) []) nil)
        child-confs (map-indexed (fn [idx s] (normalize-child parent-table p-id idx s)) child-specs)
        tabs (concat [{:tabname parent-title :id (:id parent-conf) :pane-id (:pane-id parent-conf) :nav-id (:nav-id parent-conf) :grid ((:grid-fn parent-conf) parent-title grid-row parent-table parent-fields parent-href parent-args) :rel-type (or (:rel-type parent-spec) :1to1)}]
                     (map-indexed (fn [i c]
                                    (assoc {:tabname (:title c) :id (:id c) :pane-id (:pane-id c) :nav-id (:nav-id c)
                                            :grid ((:grid-fn c) (:title c) (:rows c) (:table c) (:fields c) (:href c) (:args c) (:new-href c))}
                                           :rel-type (or (:rel-type c)
                                                         (when-let [spec (nth child-specs i nil)] (:rel-type spec))
                                                         (cond (= i 0) :1ton :else :mton))))
                                  child-confs))]
    [[:div
      (parent-table-modal parent-table parent-dbfields parent-labels all-parent-records)
      [:ul.nav.nav-tabs {:role "tablist"}
       (doall (map-indexed (fn [idx t]
                             (build-tab-nav-item (:nav-id t) (:pane-id t) (:tabname t) (zero? idx) (:rel-type t)))
                           tabs))
       rel-legend-btn rel-legend-popup]
      [:div.tab-content.mt-3
       (doall (map-indexed (fn [idx t]
                             (build-tab-pane (:pane-id t) (:nav-id t) (zero? idx) (:grid t)))
                           tabs))]]
     [:script
      "document.addEventListener('DOMContentLoaded', function(){var id=localStorage.getItem('active-tab'); if(id){var sel='.nav-tabs .nav-link[href=\\\"#'+id+'\\\"]'; var el=document.querySelector(sel); if(el){try{bootstrap.Tab.getOrCreateInstance(el).show();}catch(e){el.click();}}}});"]]))