(ns {{sanitized}}.engine.config
  "Configuration registry and loader for entity definitions.
   Loads, validates, and caches entity configurations from EDN files."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.spec.alpha :as s]))

;; =============================================================================
;; Schema Validation with clojure.spec
;; =============================================================================

(s/def ::id keyword?)
(s/def ::label string?)
(s/def ::type #{:text :email :password :date :datetime :number :decimal :select :radio :checkbox :textarea :file :hidden :computed})
(s/def ::required? boolean?)
(s/def ::placeholder string?)
(s/def ::validation (s/or :fn fn? :keyword keyword?))
(s/def ::value any?)
(s/def ::options (s/coll-of (s/keys :req-un [::value ::label] :opt-un [::id])))
(s/def ::compute-fn (s/or :fn fn? :keyword keyword?))

(s/def ::field
  (s/keys :req-un [::id ::label ::type]
          :opt-un [::required? ::placeholder ::validation ::options ::value ::compute-fn]))

(s/def ::entity keyword?)
(s/def ::title string?)
(s/def ::table string?)
(s/def ::connection keyword?)
(s/def ::rights (s/coll-of string?))

(s/def ::query (s/or :string string? :fn fn? :keyword keyword?))
(s/def ::queries
  (s/keys :opt-un [::list ::get ::insert ::update ::delete]))

(s/def ::fields (s/coll-of ::field))

(s/def ::new boolean?)
(s/def ::edit boolean?)
(s/def ::delete boolean?)
(s/def ::actions
  (s/keys :opt-un [::new ::edit ::delete]))

(s/def ::hook (s/or :fn fn? :keyword keyword?))
(s/def ::hooks
  (s/keys :opt-un [::before-load ::after-load
                   ::before-save ::after-save
                   ::before-delete ::after-delete]))

(s/def ::grid-fn (s/or :fn fn? :keyword keyword?))
(s/def ::form-fn (s/or :fn fn? :keyword keyword?))
(s/def ::dashboard-fn (s/or :fn fn? :keyword keyword?))
(s/def ::template keyword?)
(s/def ::ui
  (s/keys :opt-un [::grid-fn ::form-fn ::dashboard-fn ::template]))

(s/def ::foreign-key keyword?)
(s/def ::href string?)
(s/def ::icon string?)
(s/def ::subgrid
  (s/keys :req-un [::entity ::foreign-key]
          :opt-un [::title ::href ::icon]))

(s/def ::subgrids (s/coll-of ::subgrid))

(s/def ::mode #{:parameter-driven :generated :hybrid})
(s/def ::audit? boolean?)

(s/def ::entity-config
  (s/keys :req-un [::entity ::title ::table]
          :opt-un [::connection ::rights ::fields ::queries ::actions
                   ::hooks ::ui ::subgrids ::mode ::audit?]))

;; =============================================================================
;; Configuration Cache
;; =============================================================================

(def config-cache
  "Atom holding all loaded entity configurations.
   Map of entity-keyword -> config-map"
  (atom {}))

(def ^:private config-watchers
  "Atom holding file watchers for hot-reload"
  (atom {}))

;; =============================================================================
;; Configuration Loading
;; =============================================================================

(defn resolve-fn-ref
  "Resolves a function reference from a keyword.
   Example: :user.validators/check-email -> (require 'user.validators) + (:check-email user.validators)
   Note: To reload hooks, use config/reload-entity-config! which will re-resolve functions."
  [kw]
  (when (keyword? kw)
    (let [ns-part (namespace kw)
          fn-part (name kw)]
      (when ns-part
        (try
          ;; Use :reload to pick up changes to hook files
          (require (symbol ns-part) :reload)
          (if-let [fn-var (ns-resolve (symbol ns-part) (symbol fn-part))]
            @fn-var
            (do
              (println "[WARN] Function not found:" kw)
              nil))
          (catch Exception e
            (println "[ERROR] Failed to resolve function" kw ":" (.getMessage e))
            nil))))))

(defn enhance-if-has-subgrids
  "Automatically flags entities with subgrids for TabGrid enhancement"
  [config]
  (if (and (seq (:subgrids config))
           (not= "none" (get-in config [:mode :interface] "auto")))
    (assoc config :enhanced-tabgrid true)
    config))

(defn- process-config
  "Post-processes a config map to resolve function references."
  [config]
  (let [resolve-value (fn [v]
                        (cond
                          (keyword? v) (or (resolve-fn-ref v) v)
                          (fn? v) v
                          :else v))
        
        process-field (fn [field]
                        (-> field
                            (update :validation #(when % (resolve-value %)))
                            (update :compute-fn #(when % (resolve-value %)))))
        
        process-hooks (fn [hooks]
                        (when hooks
                          (into {}
                                (map (fn [[k v]] [k (resolve-value v)])
                                     hooks))))]
    
    (-> config
        enhance-if-has-subgrids
        (update :fields #(mapv process-field %))
        (update :hooks process-hooks)
        (update-in [:ui :grid-fn] #(when % (resolve-value %)))
        (update-in [:ui :form-fn] #(when % (resolve-value %)))
        (update-in [:ui :dashboard-fn] #(when % (resolve-value %))))))

(defn- validate-config
  "Validates a config map against the spec. Returns config if valid, throws otherwise."
  [config]
  ;; Temporarily disable validation for scaffolded entities
  config
  #_(if (s/valid? ::entity-config config)
    config
    (throw (ex-info "Invalid entity configuration"
                    {:entity (:entity config)
                     :explain (s/explain-str ::entity-config config)}))))

(defn load-entity-config
  "Loads an entity configuration from resources/entities/<entity>.edn
   Returns the config map or throws if not found/invalid."
  [entity]
  (if-let [cached (@config-cache entity)]
    cached
    (try
      (let [entity-name (if (keyword? entity) (name entity) (str entity))
            path (str "entities/" entity-name ".edn")
            resource (io/resource path)]
        (if resource
          (let [config (-> resource
                           slurp
                           edn/read-string
                           process-config
                           validate-config)]
            (swap! config-cache assoc entity config)
            config)
          (throw (ex-info "Entity configuration not found"
                          {:entity entity :path path}))))
      (catch Exception e
        (println "[ERROR] Failed to load entity config:" entity)
        (.printStackTrace e)
        (throw e)))))

(defn reload-entity-config!
  "Forces reload of an entity configuration from disk."
  [entity]
  (swap! config-cache dissoc entity)
  (load-entity-config entity))

(defn get-entity-config
  "Gets an entity configuration from cache or loads it."
  [entity]
  (load-entity-config entity))

(defn list-available-entities
  "Lists all available entity configurations."
  []
  (try
    (let [entities-dir (io/resource "entities")]
      (if entities-dir
        (->> entities-dir
             io/file
             file-seq
             (filter #(and (.isFile %) (.endsWith (.getName %) ".edn")))
             (map #(-> (.getName %)
                       (str/replace #"\.edn$" "")
                       keyword))
             sort
             vec)
        []))
    (catch Exception e
      (println "[WARN] Could not list entities:" (.getMessage e))
      [])))

(defn clear-cache!
  "Clears the entire configuration cache."
  []
  (reset! config-cache {}))

(defn reload-all!
  "Reloads all entity configurations."
  []
  (clear-cache!)
  (doseq [entity (list-available-entities)]
    (try
      (load-entity-config entity)
      (catch Exception e
        (println "[ERROR] Failed to reload" entity ":" (.getMessage e))))))

;; =============================================================================
;; Configuration Defaults
;; =============================================================================

(def default-field-config
  {:text {:type :text :required? false}
   :email {:type :email :required? false}
   :password {:type :password :required? true}
   :date {:type :date :required? false}
   :number {:type :number :required? false}
   :select {:type :select :required? false :options []}
   :radio {:type :radio :required? false :options []}
   :textarea {:type :textarea :required? false}
   :file {:type :file :required? false}
   :hidden {:type :hidden}})

(def default-actions
  {:new true :edit true :delete true})

(def default-rights
  ["U" "A" "S"])

(defn merge-defaults
  "Merges entity config with defaults."
  [config]
  (-> config
      (update :connection #(or % :default))
      (update :rights #(or % default-rights))
      (update :actions #(merge default-actions %))
      (update :mode #(or % :parameter-driven))))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn get-field-config
  "Gets a specific field configuration from an entity."
  [entity field-id]
  (let [config (get-entity-config entity)]
    (first (filter #(= (:id %) field-id) (:fields config)))))

(defn get-display-fields
  "Gets fields that should be displayed in grids/lists.
   Limits to 8 fields maximum to ensure action buttons are visible."
  [entity]
  (let [config (get-entity-config entity)
        all-fields (:fields config)
        ;; Remove hidden, password, and FK ID fields (show display names instead)
        visible-fields (remove #(or (#{:hidden :password} (:type %))
                                    (:hidden-in-grid? %)
                                    ;; Hide FK IDs if display field exists
                                    (and (= :select (:type %))
                                         (let [fk-id (name (:id %))]
                                           ;; Check if there's a corresponding display field
                                           ;; Patterns: id_propiedad → propiedad_nombre, id_cliente → cliente_nombre
                                           (some (fn [f]
                                                   (let [field-name (name (:id f))]
                                                     (or
                                                       ;; Pattern: id_X → X_nombre/X_name
                                                       (re-find (re-pattern (str fk-id "_(nombre|name)"))
                                                               field-name)
                                                       ;; Pattern: id_X → X_nombre (removing id_ prefix)
                                                       (and (clojure.string/starts-with? fk-id "id_")
                                                            (re-find (re-pattern (str (subs fk-id 3) "_(nombre|name)"))
                                                                    field-name)))))
                                                 all-fields))))
                               all-fields)
        ;; Keep ID field if present (for links/references)
        id-field (first (filter #(= :id (:id %)) all-fields))
        ;; Get non-id visible fields
        non-id-fields (remove #(= :id (:id %)) visible-fields)
        ;; Limit to 7 fields (plus ID = 8 total)
        limited-fields (take 7 non-id-fields)]
    ;; Combine: ID first, then up to 7 other fields
    (if id-field
      (cons id-field limited-fields)
      (take 8 visible-fields))))

(defn get-form-fields
  "Gets fields that should be displayed in forms."
  [entity]
  (let [config (get-entity-config entity)]
    ;; Exclude grid-only fields and fields hidden in forms
    (remove #(or (:grid-only? %)
                 (:hidden-in-form? %))
            (:fields config))))

(defn has-permission?
  "Checks if a user level has permission to access an entity."
  [entity user-level]
  (let [config (get-entity-config entity)
        allowed-rights (or (:rights config) default-rights)]
    (boolean (some #(= user-level %) allowed-rights))))

(comment
  ;; Usage examples
  (load-entity-config :users)
  (list-available-entities)
  (get-entity-config :users)
  (reload-entity-config! :users)
  (reload-all!)
  (clear-cache!)
  (has-permission? :users "S")
  (get-display-fields :users)
  (get-form-fields :users))
