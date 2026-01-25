# Quick Reference - Command Cheat Sheet

## **Quick Start:** Running the App

**Note:** Port and database are configured in `resources/private/config.clj`

### Development (Auto-Reload) - RECOMMENDED 
```bash
lein with-profile dev run
```
- Code changes reload automatically
- Entity configs hot-reload
- No restarts needed during development
- Default port: http://localhost:8080 (configurable)

### Production
```bash
lein run
```
- Faster startup (no reload middleware)
- Use for production deployments

### Build for Deployment
```bash
lein uberjar
java -jar target/uberjar/rs.jar
```

---

## **Configuration:** Configuration

**Location:** `resources/private/config.clj`

The config file is included with your project and uses your project name automatically.
Just update passwords and connection settings as needed.

### Key Settings
```clojure
{:port 8080                    ; Server port
 :connections 
 {:sqlite {:db-type "sqlite"   ; SQLite database
           :db-class "org.sqlite.JDBC"
           :db-name "db/{{name}}.sqlite"}
  :mysql {:db-type "mysql"     ; MySQL database
          :db-class "com.mysql.cj.jdbc.Driver"
          :db-name "//localhost:3306/{{name}}"
          :db-user "root"
          :db-pwd "your_password"}  ; ← Update this
  :postgres {:db-type "postgresql"  ; PostgreSQL
             :db-class "org.postgresql.Driver"
             :db-name "//localhost:5432/{{name}}"
             :db-user "postgres"
             :db-pwd "your_password"}  ; ← Update this
  :default :sqlite              ; Which connection to use
  :main :sqlite}                ; For migrations
 
 :theme "sketchy"               ; Bootstrap theme
 :uploads "./uploads/{{name}}/" ; Upload directory
 :max-upload-mb 5}              ; Max file size
```

### Switch Database
Edit `:default` and `:main` keys to point to `:mysql`, `:postgres`, or `:sqlite`

---

## **Database** Database Commands

### Run Migrations
```bash
lein migrate
```

### Rollback Last Migration
```bash
lein rollback
```

### Database Setup (Seeding)
```bash
# MySQL (default)
lein database

# PostgreSQL
lein database pg

# SQLite
lein database localdb
```

---

## **Database** Database Commands

### Migrations
```bash
lein migrate              # Apply pending migrations
lein rollback             # Rollback last migration
```

### Migration Conversion (SQLite → MySQL/PostgreSQL)
```bash
lein convert-migrations mysql        # Convert to MySQL
lein convert-migrations postgresql   # Convert to PostgreSQL
```

### Data Migration
```bash
lein copy-data mysql         # Copy SQLite data to MySQL
lein copy-data postgresql    # Copy to PostgreSQL
lein copy-data mysql --clear # Clear target tables first
```

See [DATABASE_MIGRATION_GUIDE.md](DATABASE_MIGRATION_GUIDE.md) for details.

---

## **Architecture** Scaffolding Commands

### Scaffold Single Entity
```bash
lein scaffold table_name
```

### Scaffold All Tables
```bash
lein scaffold --all
```

### Interactive Mode
```bash
lein scaffold --interactive
```

### Force Overwrite
```bash
lein scaffold table_name --force
```

---

## **Setup:** Development Commands

### Clean Build
```bash
lein clean
```

### Compile Only
```bash
lein compile
```

### REPL
```bash
lein repl
```

### Check Dependencies
```bash
lein ancient
```

---

## URLs (After Login)

### Authentication
- **Login:** http://localhost:8080/home/login
- **Logout:** http://localhost:8080/home/logoff
- **Change Password:** http://localhost:8080/change/password

### Admin Entities

#### Clientes (Clients)
- **Agentes:** http://localhost:8080/admin/agentes
- **Clientes:** http://localhost:8080/admin/clientes
- **Customers:** http://localhost:8080/admin/customers

#### Propiedades (Properties)
- **Propiedades:** http://localhost:8080/admin/propiedades

#### Transacciones (Transactions)
- **Alquileres:** http://localhost:8080/admin/alquileres
- **Ventas:** http://localhost:8080/admin/ventas
- **Contratos:** http://localhost:8080/admin/contratos
- **Tramites:** http://localhost:8080/admin/tramites

#### Finanzas (Financial)
- **Pagos Renta:** http://localhost:8080/admin/pagos_renta
- **Pagos Ventas:** http://localhost:8080/admin/pagos_ventas
- **Comisiones:** http://localhost:8080/admin/comisiones
- **Avaluos:** http://localhost:8080/admin/avaluos

#### Documentos (Documents)
- **Documentos:** http://localhost:8080/admin/documentos
- **Fiadores:** http://localhost:8080/admin/fiadores

#### Sistema (System)
- **Users:** http://localhost:8080/admin/users
- **Bitacora:** http://localhost:8080/admin/bitacora

---

## Key File Locations

### Entity Configurations
```
resources/entities/*.edn
```

### Engine Files
```
src/{{name}}/engine/
├── config.clj    - Config loader
├── crud.clj      - CRUD operations
├── query.clj     - Query execution
├── render.clj    - UI rendering
├── router.clj    - Dynamic routes
├── scaffold.clj  - Scaffolding engine
└── menu.clj      - Menu generation
```

### Core Files
```
src/{{name}}/
├── core.clj      - Main app
├── menu.clj      - Menu config
├── layout.clj    - Page layout
└── routes/
    └── routes.clj - Auth routes
```

### Migrations
```
resources/migrations/*.up.sql
resources/migrations/*.down.sql
```

---

## **Search:** REPL Commands

### Load Menu System
```clojure
(require '[{{name}}.engine.menu :as menu])
(menu/discover-entities)        ; List all entities
(menu/generate-menu-items)      ; See menu structure
(menu/get-menu-config)          ; Full menu config
```

### Load Entity Config
```clojure
(require '[{{name}}.engine.config :as config])
(config/load-entity-config :clientes)  ; Load specific entity
```

### Test Scaffold
```clojure
(require '[{{name}}.engine.scaffold :as scaffold])
(scaffold/get-tables)           ; List database tables
```

---

## Troubleshooting

### Clear Everything and Rebuild
```bash
lein clean
rm -rf target/
lein compile
lein with-profile dev run
```

### Check Syntax Errors
```bash
lein check
```

### View Logs (if using detached mode)
```bash
tail -f logs/app.log  # If logging configured
```

### Port Already in Use
```bash
# Find process using port 8080
lsof -i :8080

# Kill it
kill -9 <PID>
```

---

## **Note:** Quick Customizations

### Add New Entity
```bash
# 1. Create migration
touch resources/migrations/XXX-create-my-table.up.sql

# 2. Run migration
lein migrate

# 3. Scaffold entity
lein scaffold my_table

# 4. Refresh browser - new entity appears in menu!
```

### Change Menu Category
Edit `resources/entities/my_entity.edn`:
```clojure
:menu-category :transactions  ; Change category
:menu-order 1                 ; Change order
```

### Add Validation
Edit entity config:
```clojure
:fields [{:id :email
          :type :email
          :required true
          :validator :{{name}}.validators/check-email}]
```

---

## 📋 Complete Field Types Reference

WebGen supports these field types in entity configurations:

### Text Inputs
```clojure
;; Single-line text
{:id :name :label "Name" :type :text :placeholder "Enter name..." :required true}

;; Multi-line text
{:id :notes :label "Notes" :type :textarea :rows 5 :placeholder "Enter notes..."}

;; Email with validation
{:id :email :label "Email" :type :email :placeholder "user@example.com"}

;; Password (masked)
{:id :password :label "Password" :type :password}
```

### Numeric Inputs
```clojure
;; Integer
{:id :quantity :label "Quantity" :type :number :min 0 :max 1000}

;; Decimal/Float
{:id :price :label "Price" :type :decimal :min 0 :step 0.01 :placeholder "0.00"}
```

### Date/Time Inputs
```clojure
;; Date only
{:id :birthdate :label "Birth Date" :type :date}

;; Date and time
{:id :created_at :label "Created" :type :datetime}

;; Time only
{:id :opening_time :label "Opens At" :type :time}
```

### Selection Inputs
```clojure
;; Dropdown select
{:id :category :label "Category" :type :select 
 :options [{:value "A" :label "Category A"} 
           {:value "B" :label "Category B"}]}

;; Radio buttons
{:id :status :label "Status" :type :radio :value "active"
 :options [{:id "statusActive" :label "Active" :value "active"}
           {:id "statusInactive" :label "Inactive" :value "inactive"}]}

;; FK fields - does not have to exist an fkey in the db
{:id :property_id 
 :label "Property"
 :type :fk
 :fk :property
 :fk-field [:titulo :estado :contacto]
 :fk-sort [:titulo :estado]
 :fk-filter [:activo "T"]}

 ;; Dropdown with Database Values
 ;; Create a query in model
 (ns inv.models.lookups
  (:require [inv.models.crud :as crud]))

 (defn get-categories []
   (crud/Query "SELECT id as value, name AS label FROM categories ORDER BY name" :conn :default)) ; :conn :default if not used defaults to default connection

 ;; In entity config
 {:id :category_id
  :label "Category"
  :type :select
  :options :inv.models.lookups/get-categories}

;; Single checkbox
{:id :featured :label "Featured" :type :checkbox :value "T"}
```

### Special Types
```clojure
;; File upload (requires hooks)
{:id :imagen :label "Image" :type :file}

;; Hidden field
{:id :user_id :label "User ID" :type :hidden}

;; Computed/read-only (via hooks)
{:id :total :label "Total" :type :computed :compute-fn :inv.hooks.products/calculate-total}
```

### Common Field Options
- `required` - Mark field as required (boolean)
- `placeholder` - Placeholder text
- `min` / `max` - Min/max values (numbers, dates)
- `step` - Step value for decimals (e.g., 0.01 for currency)
- `rows` - Number of rows for textarea
- `options` - Array of options for select/radio
- `value` - Default value

---

### Add Hook
Edit entity config:
```clojure
:hooks {:before-save :{{name}}.hooks/calculate-total
        :after-save :{{name}}.hooks/send-email}
```

### Add Subgrids (TabGrid)
Edit parent entity config:
```clojure
:subgrids [{:entity :order_items
            :foreign-key :order_id
            :title "Order Items"
            :icon "bi bi-box-seam"}]
```
**Result**: Automatically switches to TabGrid interface with tabs for each subgrid.

---

## **Tip:** Development Workflow

### Typical Session
```bash
# 1. Start with auto-reload
lein with-profile dev run

# 2. Open browser
# http://localhost:8080/home/login

# 3. Edit entity configs
nano resources/entities/clientes.edn

# 4. Refresh browser - changes applied!

# 5. Add new entity
lein scaffold new_table

# 6. Refresh browser - new entity in menu!
```

---

## **Documentation:** Documentation Quick Links

- **QUICKSTART.md** - 5-minute tutorial
- **FRAMEWORK_GUIDE.md** - Complete framework API
- **HOOKS_GUIDE.md** - Business logic hooks
- **RUN_APP.md** - Deployment guide

---

## **Performance:** Most Used Commands

```bash
# Start development server
lein with-profile dev run

# Scaffold all tables
lein scaffold --all

# Run migrations
lein migrate

# Build for production
lein uberjar
```

---

**Keep this file handy for quick reference during development!**
