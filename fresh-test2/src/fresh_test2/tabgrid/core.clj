(ns fresh_test2.tabgrid.core
  "Main TabGrid orchestration - connects data, rendering, and handlers"
  (:require
   [fresh_test2.tabgrid.data :as data]
   [fresh_test2.tabgrid.render :as render]))

;; =============================================================================
;; Main TabGrid Entry Point
;; =============================================================================

(defn render-tabgrid
  "Main entry point for rendering a tabgrid interface
  
   Usage:
   (render-tabgrid request entity parent-id)
   
   - request: Ring request map
   - entity: Entity keyword (e.g. :propiedades)
   - parent-id: Optional parent record ID (defaults to first record)"
  [request entity parent-id]
  (let [data (data/prepare-tabgrid-data entity parent-id)
        {:keys [entity-name title parent-rows all-records fields subgrids actions]} data]
    
    (render/render-tabgrid 
     request
     entity-name
     title
     fields
     parent-rows
     all-records
     actions
     (or subgrids []))))

;; =============================================================================
;; Helper: Check if entity should use TabGrid
;; =============================================================================

(defn should-use-tabgrid?
  "Determines if an entity should use tabgrid interface"
  [entity]
  (let [data (data/prepare-tabgrid-data entity nil)]
    (seq (:subgrids data))))
