(ns fresh_test2.routes.test
  "Test route for enhanced TabGrid"
  (:require [compojure.core :refer [defroutes GET]]
            [fresh_test2.enhanced.tabgrid :as tabgrid]
            [ring.util.response :as response]))

(defroutes test-routes
  (GET "/test-enhanced" []
    (let [enhanced-html (tabgrid/render-enhanced-tabgrid nil :propiedades [])]
      (response/content-type
        (response/response enhanced-html)
        "text/html"))))
