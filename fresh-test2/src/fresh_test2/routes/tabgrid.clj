(ns fresh_test2.routes.tabgrid
  "Routes for TabGrid AJAX handlers"
  (:require
   [compojure.core :refer [GET POST defroutes]]
   [fresh_test2.tabgrid.handlers :as handlers]))

(defroutes tabgrid-routes
  ;; Load subgrid data
  (GET "/tabgrid/load-subgrid" request
    (handlers/handle-load-subgrid request))
  
  ;; Get parent record
  (GET "/tabgrid/get-parent" request
    (handlers/handle-get-parent request)))
