(ns {{sanitized}}.models.schema-enhanced
  "Enhanced schema for professional TabGrid configuration")

;; =============================================================================
;; UI Enhancement Functions
;; =============================================================================

(defn enhance-ui-config [entity-config]
  "Enhance UI configuration with professional defaults"
  (let [default-ui {:style :enhanced-tabgrid
                   :layout {:split-view true
                           :breadcrumbs true
                           :quick-search true}
                   :behavior {:lazy-loading true
                             :cache-enabled true}}
        existing-ui (:ui entity-config)]
    (assoc entity-config 
      :ui (merge default-ui existing-ui))))

(defn enhance-domain-config [entity-config domain-type]
  "Add domain-specific enhancements"
  (let [domain-enhancements 
         (case domain-type
           :mrp {:workflow-tabs true
                   :timeline-views true
                   :resource-allocation true}
           :accounting {:reconciliation-views true
                        :audit-trail-enhanced true}
           :inventory {:stock-level-alerts true
                      :movement-tracking true}
           :real-estate {:property-photos true
                         :document-management true}
           {})]
    (update-in entity-config [:ui :domain] merge domain-enhancements)))

(defn create-professional-config [entity-config & [domain-type]]
  "Create complete professional configuration"
  (-> entity-config
      (enhance-ui-config)
      ((if domain-type 
         #(enhance-domain-config % domain-type) 
         identity))))