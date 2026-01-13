(ns fresh_test2.menu
  "Menu configuration - auto-generated from entity configs with manual overrides"
  (:require
   [fresh_test2.engine.menu :as auto-menu]))

;; =============================================================================
;; Auto-Generated Menu from Entity Configs
;; =============================================================================

;; The menu is now auto-generated from resources/entities/*.edn files
;; Entities are automatically categorized into logical groups:
;;   - Clientes: clients, customers, agents
;;   - Propiedades: properties, real estate
;;   - Transacciones: rentals, sales, contracts, processes
;;   - Finanzas: payments, commissions, appraisals
;;   - Documentos: documents, guarantors
;;   - Sistema: users, audit logs
;;   - Administración: other admin entities

;; =============================================================================
;; Manual Menu Items (Optional)
;; =============================================================================

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

;; =============================================================================
;; Customization Guide
;; =============================================================================

;; To customize menu for a specific entity, edit its config file:
;;   resources/entities/clientes.edn
;;
;; Add these optional keys:
;;   :menu-category :clients     ; Category: :clients, :properties, :transactions, etc.
;;   :menu-order 1               ; Order within category (lower = first)
;;   :menu-icon "👥"             ; Icon to display (optional)
;;
;; Example:
;;   {:entity :clientes
;;    :title "Clientes"
;;    :table "clientes"
;;    :menu-category :clients
;;    :menu-order 1
;;    ...}

;; To add a custom menu item (non-entity):
;;   1. Add to custom-nav-links for top-level link
;;   2. Add to custom-dropdowns for dropdown menu
;;
;; Example custom dropdown:
;;   (def custom-dropdowns
;;     {:reports {:id "navdrop99"
;;                :data-id "reports"
;;                :label "Custom Reports"
;;                :items [["/reports/custom" "My Report"]]}})

(comment
  ;; Test menu generation
  (clojure.pprint/pprint menu-config)
  (clojure.pprint/pprint (get-menu-config))
  
  ;; Force menu refresh (useful during development)
  (auto-menu/refresh-menu!))
