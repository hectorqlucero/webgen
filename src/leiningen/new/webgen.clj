(ns leiningen.new.webgen
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files]]
            [clojure.java.io :as io]
            [leiningen.core.main :as main])
  (:import [java.util.jar JarFile]
           [java.io FileOutputStream]
           [java.net URLDecoder]))

(def render (renderer "webgen"))

(defn get-resource-paths
  "Gets all resource paths under a given prefix."
  [prefix]
  (let [resource-url (io/resource prefix)
        url-str (.toString resource-url)]
    (cond
      ;; Handle resources in jar files
      (.startsWith url-str "jar:")
      (let [jar-path (-> url-str
                         (.substring 9) ; remove "jar:file:"
                         (.split "!") first)
            jar-path (URLDecoder/decode jar-path "UTF-8")
            jar (JarFile. jar-path)
            entries (enumeration-seq (.entries jar))
            prefix-path (str prefix "/")
            matching-entries (filter #(and
                                       (.startsWith (.getName %) prefix-path)
                                       (not (.isDirectory %)))
                                     entries)]
        (doall (map #(.getName %) matching-entries)))

      ;; Handle resources in filesystem
      (.startsWith url-str "file:")
      (let [file (io/file resource-url)
            base-path-len (count (.getPath file))
            files (filter #(.isFile %) (file-seq file))]
        (map #(str prefix "/" (subs (.getPath %) (inc base-path-len))) files))

      :else
      (throw (IllegalArgumentException. (str "Unsupported resource URL: " resource-url))))))

(defn copy-resource
  "Copy a resource to a file."
  [resource-path dest-file]
  (io/make-parents dest-file)
  (with-open [in (io/input-stream (io/resource resource-path))
              out (FileOutputStream. dest-file)]
    (io/copy in out)))

(defn copy-resources
  "Copy resources from the classpath to destination directory."
  [resource-prefix dest-dir]
  (let [paths (get-resource-paths resource-prefix)]
    (doseq [path paths]
      (let [rel-path (subs path (inc (count resource-prefix)))
            dest-file (io/file dest-dir rel-path)]
        (copy-resource path dest-file)))))

(defn webgen
  "WebGen/LST web app template"
  [name & _args]
  (let [data {:name name
              :sanitized (name-to-path name)}]
    (main/info "")
    (main/info "╔════════════════════════════════════════════╗")
    (main/info (format "║  Creating LST/WebGen Project: %-12s║" name))
    (main/info "╚════════════════════════════════════════════╝")
    (main/info "")
    
    (->files data
             ;; Core App
             ["src/{{sanitized}}/core.clj" (render "core.clj" data)]
             ["src/{{sanitized}}/menu.clj" (render "menu.clj" data)]
             ["src/{{sanitized}}/layout.clj" (render "layout.clj" data)]
             ["src/{{sanitized}}/migrations.clj" (render "migrations.clj" data)]

             ;; Handlers
             ["src/{{sanitized}}/handlers/home/controller.clj" (render "handlers-home-controller.clj" data)]
             ["src/{{sanitized}}/handlers/home/model.clj" (render "handlers-home-model.clj" data)]
             ["src/{{sanitized}}/handlers/home/view.clj" (render "handlers-home-view.clj" data)]

             ;; Hooks
             ["src/{{sanitized}}/hooks/users.clj" (render "hooks-users.clj" data)]
             ["src/{{sanitized}}/hooks/contactos.clj" (render "hooks-contactos.clj" data)]
             ["src/{{sanitized}}/hooks/cars.clj" (render "hooks-cars.clj" data)]
             ["src/{{sanitized}}/hooks/siblings.clj" (render "hooks-siblings.clj" data)]

             ;; Engine
             ["src/{{sanitized}}/engine/config.clj" (render "engine-config.clj" data)]
             ["src/{{sanitized}}/engine/crud.clj" (render "engine-crud.clj" data)]
             ["src/{{sanitized}}/engine/menu.clj" (render "engine-menu.clj" data)]
             ["src/{{sanitized}}/engine/query.clj" (render "engine-query.clj" data)]
             ["src/{{sanitized}}/engine/render.clj" (render "engine-render.clj" data)]
             ["src/{{sanitized}}/engine/router.clj" (render "engine-router.clj" data)]
             ["src/{{sanitized}}/engine/scaffold.clj" (render "engine-scaffold.clj" data)]

             ;; Models
             ["src/{{sanitized}}/models/db.clj" (render "models-db.clj" data)]
             ["src/{{sanitized}}/models/cdb.clj" (render "models-cdb.clj" data)]
             ["src/{{sanitized}}/models/crud.clj" (render "models-crud.clj" data)]
             ["src/{{sanitized}}/models/form.clj" (render "models-form.clj" data)]
             ["src/{{sanitized}}/models/grid.clj" (render "models-grid.clj" data)]
             ["src/{{sanitized}}/models/routes.clj" (render "models-routes.clj" data)]
             ["src/{{sanitized}}/models/email.clj" (render "models-email.clj" data)]
             ["src/{{sanitized}}/models/util.clj" (render "models-util.clj" data)]
             ["src/{{sanitized}}/models/tabgrid.clj" (render "models-tabgrid.clj" data)]
             ["src/{{sanitized}}/models/schema_enhanced.clj" (render "models-schema_enhanced.clj" data)]

             ;; Database adapters
             ["src/{{sanitized}}/models/db/sqlite.clj" (render "models-db-sqlite.clj" data)]
             ["src/{{sanitized}}/models/db/mysql.clj" (render "models-db-mysql.clj" data)]
             ["src/{{sanitized}}/models/db/postgres.clj" (render "models-db-postgres.clj" data)]

             ;; Routes
             ["src/{{sanitized}}/routes/proutes.clj" (render "routes-proutes.clj" data)]
             ["src/{{sanitized}}/routes/routes.clj" (render "routes-routes.clj" data)]
             ["src/{{sanitized}}/routes/i18n.clj" (render "routes-i18n.clj" data)]
             ["src/{{sanitized}}/routes/tabgrid.clj" (render "routes-tabgrid.clj" data)]
             ["src/{{sanitized}}/routes/test.clj" (render "routes-test.clj" data)]

             ;; I18n
             ["src/{{sanitized}}/i18n/core.clj" (render "i18n-core.clj" data)]

             ;; Tabgrid
             ["src/{{sanitized}}/tabgrid/core.clj" (render "tabgrid-core.clj" data)]
             ["src/{{sanitized}}/tabgrid/data.clj" (render "tabgrid-data.clj" data)]
             ["src/{{sanitized}}/tabgrid/handlers.clj" (render "tabgrid-handlers.clj" data)]
             ["src/{{sanitized}}/tabgrid/render.clj" (render "tabgrid-render.clj" data)]

             ;; Database utilities
             ["src/{{sanitized}}/db/converter.clj" (render "db-converter.clj" data)]
             ["src/{{sanitized}}/db/migrator.clj" (render "db-migrator.clj" data)]

             ;; Scaffold enhancement
             ["src/{{sanitized}}/scaffold/enhancement.clj" (render "scaffold-enhancement.clj" data)]

             ;; Test
             ["test/{{sanitized}}/core_test.clj" (render "core_test.clj" data)]
             ["test/{{sanitized}}/db_test.clj" (render "db_test.clj" data)]
             ["test/{{sanitized}}/db_vendor_test.clj" (render "db_vendor_test.clj" data)]

             ;; Dev
             ["dev/{{sanitized}}/dev.clj" (render "dev.clj" data)]

             ;; Data directory for SQLite databases
             ["db/.gitkeep" ""]

             ;; Directory for uploads
             ["uploads/.gitkeep" ""]

             ;; Project files
             ["project.clj" (render "project.clj" data)]
             ["README.md" (render "README.md" data)]
             [".gitignore" (render "gitignore" data)]
             
             ;; Documentation
             ["QUICKSTART.md" (render "QUICKSTART.md" data)]
             ["HOOKS_GUIDE.md" (render "HOOKS_GUIDE.md" data)]
             ["FRAMEWORK_GUIDE.md" (render "FRAMEWORK_GUIDE.md" data)]
             ["DATABASE_MIGRATION_GUIDE.md" (render "DATABASE_MIGRATION_GUIDE.md" data)]
             ["COLLABORATION_GUIDE.md" (render "COLLABORATION_GUIDE.md" data)]
             ["QUICK_REFERENCE.md" (render "QUICK_REFERENCE.md" data)]
             ["RUN_APP.md" (render "RUN_APP.md" data)]
             
             ;; Entity configurations (must be rendered to replace {{sanitized}})
             ["resources/entities/users.edn" (render "users-entity.edn" data)]
             ["resources/entities/contactos.edn" (render "contactos-entity.edn" data)]
             ["resources/entities/cars.edn" (render "cars-entity.edn" data)]
             ["resources/entities/siblings.edn" (render "siblings-entity.edn" data)])

    ;; Copy static resources (migrations, i18n, public files)
    (main/info "Copying resources...")
    (copy-resources "leiningen/new/webgen/resources" (str name "/resources"))
    
    (main/info "")
    (main/info "✓ Project created successfully!")
    (main/info "")
    (main/info "Next steps:")
    (main/info "  cd" name)
    (main/info "  cp resources/private/config.clj.example resources/private/config.clj")
    (main/info "  # Edit config.clj with your database credentials")
    (main/info "  lein database")
    (main/info "  lein with-profile dev run")
    (main/info "")
    (main/info "See QUICKSTART.md for more information.")
    (main/info "")))
