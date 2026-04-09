(ns {{sanitized}}.menu
  "Menu configuration - auto-generated from entity configs with manual overrides"
  (:require
   [{{sanitized}}.engine.menu :as auto-menu]
   [clojure.pprint :as pp]))

;; Custom navigation links (non-dropdown, not entity-based).
;; These are standalone pages — they have no backing entity.
;; Format: ["/path" "Label" "Rights" order]
;;   - Rights (optional): "U" = Users+, "A" = Admins only, nil = everyone
;;   - order  (optional): lower number = appears first (default: 0)
;; Examples:
;;   ["/dashboard" "DASHBOARD" "U" 10]  - Users+, order 10
;;   ["/admin"     "ADMIN"     "A" 20]  - Admins only, order 20
;;   ["/settings"  "SETTINGS"  nil 30]  - Everyone, order 30
;;   ["/home"      "HOME"]              - Everyone, default order 0
(def custom-nav-links
  "Custom navigation links (non-dropdown, not entity-based)"
  [["/" "HOME" nil 0]])

;; Custom dropdown menus (not entity-based).
;; Use :order on the dropdown itself to control its position among other dropdowns.
;; Each item in :items follows the nav-link format: ["/path" "Label" "Rights" order]
;; Format: {Key {:id "unique-id" :data-id "key" :label "Display" :order N :items [[...]]}}
;; Example:
;;   {:Reports
;;    {:id "navdrop3"
;;     :data-id "Reports"
;;     :label "Reportes"
;;     :order 40
;;     :items [["/reports/contactos" "Contactos" "U" 10]
;;             ["/reports/users" "Usuarios" "A" 20]]}}
;; Notice the order in items... It orders the dropdown items
(def custom-dropdowns
  "Custom dropdown menus (not entity-based)"
  {}
  )

;; Convert custom nav links to auto-menu format
(defn ^:private format-custom-nav-link
  [[href label rights order]]
  {:href href
   :label label
   :rights (when rights (vector rights))
   :order (or order 0)})

;; Convert all custom nav links to auto-menu format
(defn ^:private format-custom-nav-links
  [links]
  (map format-custom-nav-link links))

;; Combine and sort nav links by order (lowest first)
(defn ^:private combine-and-sort-nav-links
  [auto-links custom-links]
  (sort-by :order (concat auto-links custom-links)))

;; Convert custom dropdown items to auto-menu format
(defn ^:private format-custom-dropdown-item
  [[href label rights order]]
  {:href href
   :label label
   :rights (when rights (vector rights))
   :order (or order 0)})

;; Convert all items in a custom dropdown to auto-menu format
(defn ^:private format-custom-dropdown-items
  [items]
  (map format-custom-dropdown-item items))

;; Convert a custom dropdown to auto-menu format
(defn ^:private format-custom-dropdown
  [dropdown]
  (update dropdown :items format-custom-dropdown-items))

;; Convert all custom dropdowns to auto-menu format
(defn ^:private format-custom-dropdowns
  [dropdowns-map]
  (into {} (map (fn [[k v]] [k (format-custom-dropdown v)])
                dropdowns-map)))

;; Convert a menu-item map back to the vector format layout.clj expects
(defn ^:private map->vec
  [{:keys [href label rights]}]
  (if (seq rights)
    [href label (first rights)]
    [href label]))

;; Main function to get the complete menu configuration
(defn get-menu-config
  "Returns the complete menu configuration with custom overrides"
  []
  (let [auto-config (auto-menu/get-menu-config)
        ; Process custom nav links
        formatted-custom-nav-links (format-custom-nav-links custom-nav-links)
        ; Convert auto-generated nav-links from vectors to maps for consistent sorting
        auto-nav-links-as-maps (map (fn [[href label & rights]]
                                      {:href href
                                       :label label
                                       :rights (when rights (vector (first rights)))
                                       :order 999}) ; Default order for auto-generated items
                                    (:nav-links auto-config))
        ; Combine and sort all nav links by order
        sorted-nav-links (combine-and-sort-nav-links auto-nav-links-as-maps formatted-custom-nav-links)
        ; Process custom dropdowns
        formatted-custom-dropdowns (format-custom-dropdowns custom-dropdowns)
        ; Convert auto-generated dropdown items to maps for consistent sorting
        auto-dropdown-items-as-maps (fn [dropdown-config]
                                      (update dropdown-config :items
                                              (fn [items]
                                                (map (fn [[href label & rights]]
                                                       {:href href
                                                        :label label
                                                        :rights (when rights (vector (first rights)))
                                                        :order 999})
                                                     items))))
        ; Apply conversion to all auto-generated dropdowns
        auto-dropdowns-as-maps (into {} (map (fn [[category-key dropdown-config]]
                                               [category-key (auto-dropdown-items-as-maps dropdown-config)])
                                             (:dropdowns auto-config)))
        ; Combine all dropdowns
        combined-dropdowns (merge auto-dropdowns-as-maps formatted-custom-dropdowns)
        ; Sort dropdowns by :order, then sort items within each and convert back to vectors
        sorted-dropdowns (->> combined-dropdowns
                              (sort-by (fn [[_ cfg]] (or (:order cfg) 999)))
                              (map (fn [[category dropdown-config]]
                                     [category (update dropdown-config :items
                                                       (fn [items]
                                                         (map map->vec (sort-by :order items))))])))]
    {:nav-links (map map->vec sorted-nav-links)
     :dropdowns sorted-dropdowns}))

(comment
  ;; Test menu generation
  (pp/pprint (get-menu-config))

  ;; Force menu refresh (useful during development)
  (auto-menu/refresh-menu!))
