(ns fresh_test2.i18n.core
  "Internationalization (i18n) system for the application.
   Supports multiple languages with easy translation management."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

;; =============================================================================
;; Configuration
;; =============================================================================

(def default-locale :es)  ;; Spanish by default

(def supported-locales
  "Map of supported locales with their display names"
  {:es {:name "Español" :flag "🇲🇽"}
   :en {:name "English" :flag "🇺🇸"}})

(def translations-cache
  "Atom holding all loaded translations by locale"
  (atom {}))

;; =============================================================================
;; Translation Loading
;; =============================================================================

(defn load-translations
  "Loads translations for a specific locale from EDN file"
  [locale]
  (let [filename (str "i18n/" (name locale) ".edn")
        resource (io/resource filename)]
    (if resource
      (try
        (edn/read-string (slurp resource))
        (catch Exception e
          (println "[ERROR] Failed to load translations for" locale ":" (.getMessage e))
          {}))
      (do
        (println "[WARN] Translation file not found:" filename)
        {}))))

(defn ensure-translations-loaded
  "Ensures translations for a locale are loaded in cache"
  [locale]
  (when-not (get @translations-cache locale)
    (swap! translations-cache assoc locale (load-translations locale))))

(defn reload-translations!
  "Reloads all translations from disk"
  []
  (reset! translations-cache {})
  (doseq [locale (keys supported-locales)]
    (ensure-translations-loaded locale)))

;; =============================================================================
;; Translation Function
;; =============================================================================

(defn t
  "Translates a key to the current locale.
   
   Usage:
     (t :common/save) => \"Guardar\"
     (t :common/save :en) => \"Save\"
     (t :errors/required {:field \"Email\"}) => \"Email es requerido\"
     (t :missing-key) => \"missing-key\" (returns key if not found)"
  ([key]
   (t key default-locale))
  
  ([key locale]
   (t key locale {}))
  
  ([key locale params]
   (ensure-translations-loaded locale)
   (let [translations (get @translations-cache locale {})
         translation (get translations key)]
     (cond
       ;; Translation found - interpolate params
       (string? translation)
       (reduce (fn [result [k v]]
                 (clojure.string/replace result 
                                        (str "{" (name k) "}")
                                        (str v)))
               translation
               params)
       
       ;; Translation not found - return key as string
       :else
       (name key)))))

(defn translate
  "Alias for t function"
  [& args]
  (apply t args))

;; =============================================================================
;; Locale Management
;; =============================================================================

(defn get-locale-from-session
  "Gets the current locale from session, defaults to :es"
  [session]
  (or (:locale session) default-locale))

(defn set-locale!
  "Sets the locale in session"
  [session locale]
  (if (contains? supported-locales locale)
    (assoc session :locale locale)
    session))

(defn get-locale-name
  "Gets the display name for a locale"
  [locale]
  (get-in supported-locales [locale :name] (name locale)))

(defn get-locale-flag
  "Gets the flag emoji for a locale"
  [locale]
  (get-in supported-locales [locale :flag] ""))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn tr
  "Translates using request's session locale.
   
   Usage:
     (tr request :common/save)
     (tr request :errors/required {:field \"Email\"})"
  ([request key]
   (let [locale (get-locale-from-session (:session request))]
     (t key locale)))
  
  ([request key params]
   (let [locale (get-locale-from-session (:session request))]
     (t key locale params))))

;; =============================================================================
;; Initialization
;; =============================================================================

(defn init!
  "Initializes the i18n system by loading all translations"
  []
  (println "[i18n] Initializing internationalization system...")
  (println "[i18n] Default locale:" default-locale)
  (println "[i18n] Supported locales:" (keys supported-locales))
  (reload-translations!)
  (println "[i18n] Loaded translations for:" (keys @translations-cache)))

;; Auto-initialize on namespace load
(init!)
