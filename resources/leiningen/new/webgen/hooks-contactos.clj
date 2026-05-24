(ns {{sanitized}}.hooks.contactos
  (:require [{{sanitized}}.models.util :refer [image-link]]))

(defn before-load [params]
  params)

(defn after-load [rows _params]
  (map #(-> %
            (assoc :imagen (image-link (:imagen %)))) rows))

(defn before-save [params]
  (if-let [file-data (:imagen params)]
    (if (and (map? file-data) (:tempfile file-data))
      (-> params
          (assoc :file file-data :file-column :imagen)
          (dissoc :imagen))
      params)
    params))

(defn after-save [_entity-id _params]
  {:success true})

(defn before-delete [_entity-id]
  {:success true})

(defn after-delete [_entity-id]
  {:success true})
