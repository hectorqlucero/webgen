(ns {{sanitized}}.routes.tabgrid
  "Routes for TabGrid AJAX handlers"
  (:require
   [compojure.core :refer [GET POST defroutes]]
   [{{sanitized}}.tabgrid.handlers :as handlers]))

(defroutes tabgrid-routes
  ;; Load subgrid data
  (GET "/tabgrid/load-subgrid" request
    (handlers/handle-load-subgrid request))

  ;; Get parent record
  (GET "/tabgrid/get-parent" request
    (handlers/handle-get-parent request))

  ;; Many-to-many association management
  (POST "/tabgrid/associate" request
    (handlers/handle-associate request))

  (POST "/tabgrid/dissociate" request
    (handlers/handle-dissociate request))

  (GET "/tabgrid/pivot-form" request
    (handlers/handle-pivot-form request))

  ;; Fresh M2M pane HTML fragment (used for in-place refresh without page reload)
  (GET "/tabgrid/m2m-pane" request
    (handlers/handle-m2m-pane request))

  (POST "/tabgrid/save-pivot" request
    (handlers/handle-save-pivot request)))
