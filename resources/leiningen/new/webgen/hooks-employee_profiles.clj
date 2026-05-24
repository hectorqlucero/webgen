(ns {{sanitized}}.hooks.employee-profiles
  (:require [{{sanitized}}.models.util :refer [image-link]]))

(defn before-load [params]
  params)

(defn after-load [rows _params]
  (map #(-> %
            (assoc :avatar (image-link (:avatar %)))) rows))

(defn before-save [params]
  (if-let [file-data (:avatar params)]
    (if (and (map? file-data) (:tempfile file-data))
      (-> params
          (assoc :file file-data :file-column :avatar)
          (dissoc :avatar))
      params)
    params))

(defn after-save [_entity-id _params]
  {:success true})

(defn before-delete [_entity-id]
  {:success true})

(defn after-delete [_entity-id]
  {:success true})
