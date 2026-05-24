(ns {{sanitized}}.engine.menu
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   [clojure.string :as str]))

(def default-category-key :admin)

(defn discover-entities
  "Discovers all entity configuration files in resources/entities/
   Works when resources are on the filesystem or packaged inside a JAR."
  []
  (let [res (io/resource "entities")]
    (when res
      (case (.getProtocol res)
        "file"
        (->> (file-seq (io/file res))
             (filter #(.isFile %))
             (filter #(str/ends-with? (.getName %) ".edn"))
             (map #(.getName %))
             (map #(str/replace % #"\.edn$" ""))
             (sort))

        "jar"
        (let [url-str (.toString res)
              m (re-find #"jar:file:(.+?)!/" url-str)
              jar-path (when m (second m))]
          (when jar-path
            (with-open [jf (java.util.jar.JarFile. jar-path)]
              (->> (.entries jf)
                   enumeration-seq
                   (map #(.getName ^java.util.jar.JarEntry %))
                   (filter #(and (str/starts-with? % "entities/") (str/ends-with? % ".edn")))
                   (map #(-> % (subs (count "entities/")) (str/replace #"\.edn$" "")))
                   sort))))

        ;; fallback: return nil
        nil))))

(defn load-entity-config
  "Loads a single entity config file"
  [entity-name]
  (try
    (when-let [resource (io/resource (str "entities/" entity-name ".edn"))]
      (edn/read-string (slurp resource)))
    (catch Exception _
      (println (str "Warning: Could not load entity config: " entity-name))
      nil)))

(defn get-entity-info
  "Extracts menu-relevant info from entity config"
  [entity-name]
  (when-let [config (load-entity-config entity-name)]
    ;; Skip hidden entities
    (when-not (or (:menu-hidden config) (:menu-hidden? config))
      (let [entity-kw (:entity config)
            title (:title config)
            rights (:rights config)
            category (:menu-category config)
            category-order (:menu-category-order config)
            category-label (:menu-category-label config)
            category-icon (:menu-category-icon config)
            order (or (:menu-order config) 999)
            menu-icon (:menu-icon config)
            dropdown-icon (:dropdown-icon config)]
        {:entity entity-kw
         :title title
         :href (str "/admin/" (name entity-kw))
         :rights rights
         :category category
         :category-order category-order
         :category-label category-label
         :category-icon category-icon
         :order order
         :menu-icon menu-icon
         :dropdown-icon dropdown-icon}))))

(defn normalize-category-key
  "Normalizes menu category values to keyword keys. Returns nil when category is absent."
  [category]
  (cond
    (nil? category) nil
    (keyword? category) category
    (string? category) (-> category
                           str/trim
                           str/lower-case
                           (str/replace #"[^a-z0-9]+" "-")
                           (str/replace #"(^-+|-+$)" "")
                           keyword)
    :else (keyword (str category))))

(defn category->label
  "Builds a display label for a category key when no explicit label is configured."
  [category-key]
  (-> category-key
      name
      (str/replace #"[-_]+" " ")
      str/capitalize))

(defn generate-menu-items
  "Generates menu items from discovered entities.
   Entities without :menu-category are placed in the default admin category."
  []
  (let [entities (->> (discover-entities)
                      (map get-entity-info)
                      (filter some?)
                      (map #(update % :category normalize-category-key))
                      (map (fn [entity]
                             (update entity :category #(or % default-category-key)))))]
    (->> entities
         (group-by :category)
         (map (fn [[category items]]
                [category (sort-by :order items)]))
         (into {}))))

(defn format-menu-item
  "Formats a single menu item as [href title rights-or-nil order icon?].
   Always includes rights-str (may be nil) and order so parse-custom-menu-item
   can correctly recover both fields."
  [entity-info]
  (let [rights-str (when-let [rights (:rights entity-info)]
                     (first rights))
        icon (or (:dropdown-icon entity-info)
                 (:menu-icon entity-info)) ; Use dropdown-icon when present, otherwise fallback to menu-icon
        order (:order entity-info)]
    (if icon
      [(:href entity-info) (:title entity-info) rights-str order icon]
      [(:href entity-info) (:title entity-info) rights-str order])))

(defn generate-dropdown-config
  "Generates dropdown configuration for a category"
  [category-key items idx]
  (let [explicit-label (some :category-label items)
        explicit-icon (some :category-icon items)
        explicit-order (some :category-order items)
        category-icon (or explicit-icon (some :menu-icon items))]
    {:id (str "navdrop" idx)
     :data-id (name category-key)
     :label (or explicit-label
                (when (= category-key default-category-key) :menu/admin)
                (category->label category-key))
     :order (or explicit-order (apply min (map :order items)))
     :icon category-icon
     :items (map format-menu-item items)}))

(defn generate-full-menu-config
  "Generates complete menu configuration"
  []
  (let [categorized (generate-menu-items)
        sorted-categories (sort-by (fn [category]
                                     (or (some :category-order (get categorized category))
                                         (apply min (map :order (get categorized category)))))
                                   (keys categorized))
        dropdowns (into {}
                        (map-indexed
                         (fn [idx category]
                           [category
                            (generate-dropdown-config
                             category
                             (get categorized category)
                             idx)])
                         sorted-categories))]
    {:nav-links []
     :dropdowns dropdowns}))

(defn get-menu-config
  "Returns the auto-generated menu configuration"
  []
  (generate-full-menu-config))

(defn refresh-menu!
  "Forces menu refresh (useful for hot-reload)"
  []
  (get-menu-config))

;; For REPL testing
(comment
  ;; Test entity discovery
  (discover-entities)

  ;; Test single entity info
  (get-entity-info "clientes")

  ;; Generate full menu
  (pp/pprint (generate-full-menu-config))

  ;; Test menu items by category
  (generate-menu-items))
