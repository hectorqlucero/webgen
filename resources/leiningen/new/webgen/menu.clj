(ns {{sanitized}}.menu
  "Menu configuration - auto-generated from entity configs with manual overrides"
  (:require
   [{{sanitized}}.engine.menu :as auto-menu]
   [clojure.pprint :as pp]
   [clojure.string :as str]))

;; Custom navigation links (non-dropdown, not entity-based).
;; These are standalone pages — they have no backing entity.
;; Format: ["/path" "Label" "Rights" order icon]
;;   - Rights (optional): "U" = Users+, "A" = Admins only, nil = everyone
;;   - order  (optional): lower number = appears first (default: 0)
;;   - icon   (optional): Bootstrap icon class string, e.g. "bi bi-house"
;; Examples:
;;   ["/dashboard" "DASHBOARD" "U" 10]
;;   ["/admin"     "ADMIN"     "A" 20]
;;   ["/settings"  "SETTINGS"  nil 30]
;;   ["/home"      "HOME"]
;;   ["/help"      "HELP"      "bi bi-info-circle"]
;;   ["/settings"  "SETTINGS"  "U" 30 "bi bi-gear"]
(def custom-nav-links
  "Custom navigation links (non-dropdown, not entity-based)"
  [["/" "HOME" "bi bi-house" nil 0]])

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
;;     :icon "bi bi-chart"
;;     :items [["/reports/contactos" "Contactos" "U" 10 "bi bi-people"]
;;             ["/reports/users" "Usuarios" "A" 20 "bi bi-people"]]}}
;; Notice the order in items... It orders the dropdown items
(def custom-dropdowns
  "Custom dropdown menus (not entity-based)"
  {})

(defn ^:private parse-custom-menu-item
  "Parses a custom nav link or dropdown item vector.
   Supported forms:
     [href label]
     [href label rights]
     [href label rights order]
     [href label rights order icon]
     [href label icon]
     [href label icon order]
   If the third element begins with 'bi ', it is treated as icon."
  [[href label & rest]]
  (let [[rights order icon] rest
        [rights order icon]
        (cond
          (and rights (string? rights) (str/starts-with? rights "bi "))
          [nil 0 rights]

          (and order (string? order) (str/starts-with? order "bi "))
          [rights 0 order]

          :else
          [rights order icon])]
    {:href href
     :label label
     :rights (when rights (vector rights))
     :order (or order 0)
     :icon icon}))

;; Convert custom nav links to auto-menu format
(defn ^:private format-custom-nav-link
  [link]
  (parse-custom-menu-item link))

;; Convert all custom nav links to auto-menu format
(defn ^:private format-custom-nav-links
  [links]
  (map format-custom-nav-link links))

;; Combine and sort nav links by order (lowest first)
(defn ^:private combine-and-sort-nav-links
  [auto-links custom-links]
  (sort-by :order (concat auto-links custom-links)))

;; Convert custom dropdown items to auto-menu format
;; Format: ["/path" "Label" "Rights" order icon]
;; Examples:
;;   ["/reports" "Reportes" "U" 10]
;;   ["/users" "Usuarios" "A" 20 "bi bi-people"]
(defn ^:private format-custom-dropdown-item
  [item]
  (parse-custom-menu-item item))

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

(defn ^:private parse-meta-args
  "Parse optional rights/icon metadata from a menu vector.
   Supports [href label rights icon] and [href label icon] forms."
  [[href label & rest]]
  (let [[rights icon] rest
        [rights icon] (if (and rights (string? rights) (str/starts-with? rights "bi "))
                        [nil rights]
                        [rights icon])]
    {:href href
     :label label
     :rights (when rights (vector rights))
     :icon (when (string? icon) icon)
     :order 999}))

;; Convert a menu-item map back to the vector format layout.clj expects
(defn ^:private map->vec
  [{:keys [href label rights icon]}]
  (cond
    (and (seq rights) icon) [href label (first rights) icon]
    icon [href label icon]
    (seq rights) [href label (first rights)]
    :else [href label]))

;; Main function to get the complete menu configuration
(defn get-menu-config
  "Returns the complete menu configuration with custom overrides"
  []
  (let [auto-config (auto-menu/get-menu-config)
        ; Process custom nav links
        formatted-custom-nav-links (format-custom-nav-links custom-nav-links)
        ; Convert auto-generated nav-links from vectors to maps for consistent sorting
        auto-nav-links-as-maps (map parse-meta-args (:nav-links auto-config))
        ; Combine and sort all nav links by order
        sorted-nav-links (combine-and-sort-nav-links auto-nav-links-as-maps formatted-custom-nav-links)
        ; Process custom dropdowns
        formatted-custom-dropdowns (format-custom-dropdowns custom-dropdowns)
        ; Convert auto-generated dropdown items to maps for consistent sorting
        auto-dropdown-items-as-maps (fn [dropdown-config]
                                      (update dropdown-config :items
                                              (fn [items]
                                                (map parse-meta-args items))))
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
