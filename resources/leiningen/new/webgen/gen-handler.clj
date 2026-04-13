(ns {{sanitized}}.gen.handler
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- ns-name->path
  "Convert a namespace segment (kebab-case) to a file path segment (snake_case)."
  [s]
  (str/replace s "-" "_"))

(defn- gen-controller
  [handler-name]
  (str "(ns {{sanitized}}.handlers." handler-name ".controller\n"
       "  (:require\n"
       "   [{{sanitized}}.handlers." handler-name ".model :as model]\n"
       "   [{{sanitized}}.handlers." handler-name ".view :as view]\n"
       "   [{{sanitized}}.layout :refer [application]]\n"
       "   [{{sanitized}}.models.util :refer [get-session-id]]))\n"
       "\n"
       "(defn main\n"
       "  [request]\n"
       "  (let [title \"" (str/upper-case handler-name) "\"\n"
       "        ok (get-session-id request)\n"
       "        js nil\n"
       "        content (view/main title)]\n"
       "    (application request title ok js content)))\n"))

(defn- gen-model
  [handler-name]
  (str "(ns {{sanitized}}.handlers." handler-name ".model\n"
       "  (:require\n"
       "   [{{sanitized}}.models.crud :refer [Query]]))\n"))

(defn- gen-view
  [handler-name]
  (str "(ns {{sanitized}}.handlers." handler-name ".view)\n"))

(defn- write-file!
  [path content]
  (let [f (io/file path)]
    (io/make-parents f)
    (spit f content)
    (println "  Created:" path)))

(def proutes-path "src/{{sanitized}}/routes/proutes.clj")

(defn- require-entry
  "Build the require vector string for a handler controller."
  [handler-name]
  (str "[{{sanitized}}.handlers." handler-name ".controller :as " handler-name "]"))

(defn- add-require-to-proutes!
  "Add the controller require to proutes.clj idempotently."
  [handler-name]
  (let [content (slurp proutes-path)
        entry (require-entry handler-name)]
    (if (str/includes? content entry)
      (println "  Skipped: proutes.clj already requires" handler-name)
      (let [;; Insert new require before the closing paren of (:require ...)
            ;; Find last ] before the closing )) of the ns form
            updated (str/replace content
                                 #"(\(:require\n(?:\s+\[.+\]\n?)+)"
                                 (fn [[match]]
                                   (let [trimmed (str/trimr match)]
                                     (str trimmed "\n   " entry))))]
        (spit proutes-path updated)
        (println "  Updated:" proutes-path "- added require for" handler-name)))))

(defn- remove-require-from-proutes!
  "Remove the controller require from proutes.clj idempotently."
  [handler-name]
  (let [content (slurp proutes-path)
        entry (require-entry handler-name)]
    (if-not (str/includes? content entry)
      (println "  Skipped: proutes.clj does not require" handler-name)
      (let [;; Remove the line containing the require entry
            updated (str/replace content
                                 (re-pattern (str "\n   " (java.util.regex.Pattern/quote entry)))
                                 "")]
        (spit proutes-path updated)
        (println "  Updated:" proutes-path "- removed require for" handler-name)))))

(defn- delete-dir!
  "Recursively delete a directory."
  [dir]
  (let [f (io/file dir)]
    (when (.exists f)
      (doseq [child (reverse (file-seq f))]
        (.delete child))
      (println "  Removed:" dir))))

(defn- remove-handler!
  [handler-name]
  (let [dir-name (ns-name->path handler-name)
        base-dir (str "src/{{sanitized}}/handlers/" dir-name)]
    (if-not (.exists (io/file base-dir))
      (do
        (println (str "Error: Directory does not exist: " base-dir))
        (System/exit 1))
      (do
        (println (str "Removing handler: " handler-name))
        (delete-dir! base-dir)
        (remove-require-from-proutes! handler-name)
        (println "Done!")))))

(defn- create-handler!
  [handler-name]
  (let [dir-name (ns-name->path handler-name)
        base-dir (str "src/{{sanitized}}/handlers/" dir-name)]
    (if (.exists (io/file base-dir))
      (do
        (println (str "Error: Directory already exists: " base-dir))
        (System/exit 1))
      (do
        (println (str "Generating handler: " handler-name))
        (write-file! (str base-dir "/controller.clj") (gen-controller handler-name))
        (write-file! (str base-dir "/model.clj") (gen-model handler-name))
        (write-file! (str base-dir "/view.clj") (gen-view handler-name))
        (add-require-to-proutes! handler-name)
        (println "Done!")))))

(defn -main
  [& args]
  (let [handler-name (first args)
        action (second args)]
    (when (str/blank? handler-name)
      (println "Usage: lein gen-handler <name>")
      (println "       lein gen-handler <name> remove")
      (System/exit 1))
    (if (= action "remove")
      (remove-handler! handler-name)
      (create-handler! handler-name))))
