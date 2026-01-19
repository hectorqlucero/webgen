(ns {{sanitized}}.menu
  "Menu configuration - auto-generated from entity configs with manual overrides"
  (:require
   [{{sanitized}}.engine.menu :as auto-menu]))

;; You can add custom menu items here that aren't entity-based:
(def custom-nav-links
  "Custom navigation links (non-dropdown)"
  [])

(def custom-dropdowns
  "Custom dropdown menus"
  {})

;; Merge custom items with auto-generated (if needed)
(defn get-menu-config
  "Returns the complete menu configuration with custom overrides"
  []
  (let [auto-config (auto-menu/get-menu-config)]  ; Call function each time instead of using cached def
    (-> auto-config
        (update :nav-links concat custom-nav-links)
        (update :dropdowns merge custom-dropdowns))))

(comment
  ;; Test menu generation
  (clojure.pprint/pprint menu-config)
  (clojure.pprint/pprint (get-menu-config))
  
  ;; Force menu refresh (useful during development)
  (auto-menu/refresh-menu!))
