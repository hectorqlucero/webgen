(ns {{sanitized}}.hooks.departments)

(defn before-load [params]
  params)

(defn after-load [rows _params]
  rows)

(defn before-save [params]
  params)

(defn after-save [_entity-id _params]
  {:success true})

(defn before-delete [_entity-id]
  {:success true})

(defn after-delete [_entity-id]
  {:success true})
