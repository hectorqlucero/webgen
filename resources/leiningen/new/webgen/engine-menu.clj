(ns {{sanitized}}.engine.menu
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   [clojure.string :as str]))

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
    (when-not (:menu-hidden? config)
      (let [entity-kw (:entity config)
            title (:title config)
            rights (:rights config)
            category (or (:menu-category config) :admin)
            order (or (:menu-order config) 999)
            menu-icon (:menu-icon config)
            dropdown-icon (:dropdown-icon config)]
        {:entity entity-kw
         :title title
         :href (str "/admin/" (name entity-kw))
         :rights rights
         :category category
         :order order
         :menu-icon menu-icon
         :dropdown-icon dropdown-icon}))))

(def default-categories
  "Default menu categories with Spanish labels for real estate system"
  {:clients {:label "Clientes" :order 1 :icon "👥"}
   :properties {:label "Propiedades" :order 2 :icon "🏠"}
   :transactions {:label "Transacciones" :order 3 :icon "📋"}
   :financial {:label "Finanzas" :order 4 :icon "💰"}
   :documents {:label "Documentos" :order 5 :icon "📄"}
   :system {:label "Sistema" :order 6 :icon "⚙️"}
   :admin {:label "Administración" :order 7 :icon "🔧"}
   :reports {:label "Reportes" :order 8 :icon "📊"}})

(defn categorize-entity
  "Auto-categorizes entity based on name patterns"
  [entity-name]
  (let [name-lower (str/lower-case (name entity-name))]
    (cond
      (re-find #"cliente|client|customer" name-lower) :clients
      (re-find #"propiedad|property|inmueble" name-lower) :properties
      (re-find #"agente|agent|broker" name-lower) :clients
      (re-find #"alquiler|rental|renta" name-lower) :transactions
      (re-find #"venta|sale|sell" name-lower) :transactions
      (re-find #"contrato|contract" name-lower) :transactions
      (re-find #"tramite|process|transaction" name-lower) :transactions
      (re-find #"pago|payment|pay" name-lower) :financial
      (re-find #"comision|commission" name-lower) :financial
      (re-find #"avaluo|appraisal|valuation" name-lower) :financial
      (re-find #"documento|document|file" name-lower) :documents
      (re-find #"fiador|guarantor|aval" name-lower) :documents
      (re-find #"user|usuario" name-lower) :system
      (re-find #"bitacora|log|audit" name-lower) :system
      (re-find #"product" name-lower) :admin
      :else :admin)))

(defn enhance-entity-info
  "Adds auto-categorization to entity info"
  [entity-info]
  (let [auto-category (categorize-entity (:entity entity-info))
        final-category (if (= (:category entity-info) :admin)
                         auto-category
                         (:category entity-info))]
    (assoc entity-info :category final-category)))

(defn generate-menu-items
  "Generates menu items from all discovered entities"
  []
  (->> (discover-entities)
       (map get-entity-info)
       (filter some?)
       (map enhance-entity-info)
       (group-by :category)
       (map (fn [[category items]]
              [category (sort-by :order items)]))
       (into {})))

(defn format-menu-item
  "Formats a single menu item"
  [entity-info]
  (let [rights-str (when-let [rights (:rights entity-info)]
                     (first rights))
        icon (or (:dropdown-icon entity-info)
                 (:menu-icon entity-info))] ; Use dropdown-icon when present, otherwise fallback to menu-icon
    (cond-> [(:href entity-info) (:title entity-info)]
      rights-str (conj rights-str)
      icon (conj icon))))

(defn generate-dropdown-config
  "Generates dropdown configuration for a category"
  [category-key items idx]
  (let [category-info (get default-categories category-key)
        label (or (:label category-info) (str/capitalize (name category-key)))
        category-icon (some :menu-icon items)]
    {:id (str "navdrop" idx)
     :data-id (name category-key)
     :label label
     :order (apply min (map :order items))
     :icon category-icon
     :items (map format-menu-item items)}))

(defn generate-full-menu-config
  "Generates complete menu configuration"
  []
  (let [menu-items (generate-menu-items)
        sorted-categories (sort-by #(get-in default-categories [% :order] 999)
                                   (keys menu-items))
        dropdowns (into {}
                        (map-indexed
                         (fn [idx category]
                           [category
                            (generate-dropdown-config
                             category
                             (get menu-items category)
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

  ;; Test categorization
  (categorize-entity :clientes)
  (categorize-entity :propiedades)
  (categorize-entity :pagos_renta)

  ;; Generate full menu
  (clojure.pprint/pprint (generate-full-menu-config))

  ;; Test menu items by category
  (generate-menu-items))
