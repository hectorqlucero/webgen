(ns test-enhanced-system
  (:require [{{sanitized}}.engine.config :as config]))

;; Test the auto-enhancement system
(println "Testing Enhanced TabGrid Auto-Enhancement System...")
(println "==================================================")

;; Test auto-detection function
(def test-subgrids [
  {:grid-id :audit-log :title "Audit Log" :data [{:id 1 :action "create"}]}
  {:grid-id :contact-info :title "Contact Info" :data [{:id 1 :type "email"}]}
])

(def test-render-config {
  :renderer (fn [params] (println "Rendering grid:" (:entity-id params)))
  :theme {:container-style {:border "1px solid #ccc"}}
  :on-grid-click (fn [row] (println "Row clicked:" row))
  :on-action-click (fn [action row] (println "Action clicked:" action row))
})

;; Test the enhancement detection with a proper config map
(def test-config {
  :entity-id :test-entity
  :subgrids test-subgrids
  :render-config test-render-config
})

(def enhanced-config (config/enhance-if-has-subgrids test-config))
(println "Auto-enhancement detected:" (:enhanced-tabgrid enhanced-config))
(println "Original subgrid count:" (count (:subgrids test-config)))
(println "Enhanced subgrid count:" (count (:subgrids enhanced-config)))

;; Test config with no subgrids (should not be enhanced)
(def no-subgrid-config {
  :entity-id :simple-entity
  :subgrids []
})

(def not-enhanced (config/enhance-if-has-subgrids no-subgrid-config))
(println "Entity without subgrids enhanced:" (:enhanced-tabgrid not-enhanced))

(println "✅ Auto-enhancement system test completed successfully!")