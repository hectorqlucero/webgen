(ns leiningen.new.webgen
  "Generate a new LST/WebGen parameter-driven web application"
  (:require [leiningen.new.templates :refer [renderer ->files]]
            [leiningen.core.main :as main]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.util.jar JarFile]
           [java.net URL URLDecoder]))

(def render (renderer "webgen"))

(defn get-resource-paths
  "Get all resource paths under a given prefix, works with both JARs and filesystem"
  [prefix]
  (let [url (io/resource prefix)]
    (when url
      (let [protocol (.getProtocol url)]
        (cond
          ;; JAR resource
          (= protocol "jar")
          (let [jar-path (-> (.getPath url)
                           (str/split #"!")
                           first
                           (subs 5)
                           (URLDecoder/decode "UTF-8"))
                jar-file (JarFile. jar-path)
                entries (enumeration-seq (.entries jar-file))
                prefix-normalized (if (.endsWith prefix "/") prefix (str prefix "/"))]
            (->> entries
                 (map #(.getName %))
                 (filter #(and (.startsWith % prefix-normalized)
                             (not (.endsWith % "/"))))))
          
          ;; Filesystem resource
          (= protocol "file")
          (let [file (io/file url)
                base-path (.getPath file)]
            (->> (file-seq file)
                 (filter #(.isFile %))
                 (map #(str prefix "/" (subs (.getPath %) (inc (count base-path)))))))
          
          :else nil)))))

(defn copy-resource
  "Copy a single resource from JAR or filesystem to destination"
  [resource-path dest-base]
  (when-let [resource (io/resource resource-path)]
    (let [rel-path (if (.startsWith resource-path "leiningen/new/webgen/")
                     (subs resource-path (count "leiningen/new/webgen/"))
                     resource-path)
          dest-file (io/file dest-base rel-path)]
      (io/make-parents dest-file)
      (with-open [in (io/input-stream resource)]
        (io/copy in dest-file)))))

(defn should-skip-resource?
  "Check if a resource should be skipped (handled separately by ->files)"
  [resource-path]
  (let [skip-files #{"leiningen/new/webgen/project.clj" 
                     "leiningen/new/webgen/README.md"}]
    (skip-files resource-path)))

(defn webgen
  "Create a new LST/WebGen web application.
   
   Usage: lein new org.clojars.hector/webgen myapp"
  [name]
  (let [data {:name name}
        gitignore-content "target/\n.lein-*\npom.xml\n*.jar\n*.class\n.nrepl-port\n.DS_Store\nresources/private/config.clj\ndb/*.db\nuploads/*\n!uploads/.gitkeep\n"]
    (main/info "")
    (main/info "╔════════════════════════════════════════════╗")
    (main/info (format "║  Creating LST/WebGen Project: %-12s║" name))
    (main/info "╚════════════════════════════════════════════╝")
    (main/info "")
    
    ;; Create project structure with mustache-rendered files
    (->files data
             ["project.clj" (render "project.clj" data)]
             ["README.md" (render "README.md" data)]
             [".gitignore" gitignore-content]
             ["db/.gitkeep" ""]
             ["uploads/.gitkeep" ""])
    
    ;; Copy all source directories and documentation
    (main/info "Copying project files...")
    (let [base-prefix "leiningen/new/webgen"
          all-resources (get-resource-paths base-prefix)]
      (doseq [resource-path all-resources]
        (when-not (should-skip-resource? resource-path)
          (copy-resource resource-path name))))
    
    (main/info "")
    (main/info "✓ Project created successfully!")
    (main/info "")
    (main/info "Next steps:")
    (main/info "  cd" name)
    (main/info "  cp resources/private/config.clj.example resources/private/config.clj")
    (main/info "  # Edit config.clj with your database credentials")
    (main/info "  lein database")
    (main/info "  lein scaffold users")
    (main/info "  lein with-profile dev run")
    (main/info "")
    (main/info "See QUICKSTART.md for more information.")
    (main/info "")))
