#!/bin/bash

# Get list of all .clj files in the template
find resources/leiningen/new/lst/src -name "*.clj" -type f | sed 's|resources/leiningen/new/lst/||' > /tmp/template_files.txt

# Create the new generator
cat > src/leiningen/new/lst.clj << 'EOF'
(ns leiningen.new.lst
  "Generate a new LST/WebGen web application project"
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files]]
            [leiningen.core.main :as main]
            [clojure.java.io :as io]))

(def render (renderer "lst"))

(defn lst
  "Create a new LST/WebGen web application with parameter-driven CRUD system.
   
   Usage:
     lein new webgen myapp
     lein new lst myapp"
  [name]
  (let [data {:name name
              :sanitized (name-to-path name)}]
    (main/info "Generating fresh LST/WebGen project:" name)
    (main/info "")
    
    ;; Create basic structure - just copy project.clj and setup structure
    ;; The actual files will be copied as-is from resources
    (->files data
             ["project.clj" (render "project.clj" data)]
             ["README.md" (render "README.md" data)]
             [".gitignore" (render "gitignore")]
             ["db/.gitkeep" ""]
             ["uploads/.gitkeep" ""]
             ["shared/.gitkeep" ""])
    
    ;; Copy all source files as-is
    (let [template-dir "resources/leiningen/new/lst"
          src-files ["src" "resources" "dev" "test" "docs"]]
      (doseq [dir src-files]
        (let [source (io/file template-dir dir)
              dest (io/file name dir)]
          (when (.exists source)
            (.mkdirs dest)
            (doseq [f (file-seq source)]
              (when (.isFile f)
                (let [rel-path (.substring (.getPath f) (count (.getPath source)))
                      dest-file (io/file dest rel-path)]
                  (io/make-parents dest-file)
                  (io/copy f dest-file))))))))
    
    ;; Copy documentation
    (doseq [doc ["QUICKSTART.md" "HOOKS_GUIDE.md" "FRAMEWORK_GUIDE.md" 
                 "DATABASE_MIGRATION_GUIDE.md" "COLLABORATION_GUIDE.md" 
                 "QUICK_REFERENCE.md" "RUN_APP.md"]]
      (when-let [src (io/resource (str "leiningen/new/lst/" doc))]
        (io/copy (io/input-stream src) (io/file name doc))))
    
    (main/info "")
    (main/info "✓ Project created successfully!")
    (main/info "")
    (main/info "Next steps:")
    (main/info "  cd" name)
    (main/info "  cp resources/private/config.clj.example resources/private/config.clj")
    (main/info "  # Edit config.clj with your database settings")
    (main/info "  lein database")
    (main/info "  lein scaffold users")
    (main/info "  lein with-profile dev run")
    (main/info "")
    (main/info "See QUICKSTART.md for detailed instructions.")))
EOF

echo "✓ Template generator updated"

