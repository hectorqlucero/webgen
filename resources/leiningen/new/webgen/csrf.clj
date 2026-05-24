(ns {{sanitized}}.web.csrf
  (:require
   [ring.util.anti-forgery :refer [anti-forgery-field]]))

(def ^:private token-name-pattern #"name=\"([^\"]+)\"")
(def ^:private token-value-pattern #"value=\"([^\"]*)\"")

(defn csrf-field
  "Hiccup2-safe anti-forgery hidden input.
	 Avoids relying on hiccup.util.RawString rendering from legacy helpers."
  []
  (let [field-html (str (anti-forgery-field))
        token-name (or (second (re-find token-name-pattern field-html))
                       "__anti-forgery-token")
        token-value (or (second (re-find token-value-pattern field-html))
                        "")]
    [:input {:type "hidden"
             :name token-name
             :value token-value}]))