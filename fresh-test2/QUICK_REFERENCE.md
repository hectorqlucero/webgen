# Quick Reference - Command Cheat Sheet

## 🚀 Running the App

**Note:** Port and database are configured in `resources/private/config.clj`

### Development (Auto-Reload) - RECOMMENDED ⭐
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

## ⚙️ Configuration

**Location:** `resources/private/config.clj`

### Key Settings
```clojure
{:port 8080                    ; Server port
 :connections 
 {:sqlite {:db-type "sqlite"   ; SQLite database
           :db-name "db/rs.sqlite"}
  :mysql {:db-type "mysql"     ; MySQL database
          :db-host "localhost"
          :db-port 3306
          :db-name "mydb"
          :db-user "root"
          :db-pwd "password"}
  :postgres {:db-type "postgresql"  ; PostgreSQL
             :db-host "localhost"
             :db-port 5432
             :db-name "mydb"
             :db-user "postgres"
             :db-pwd "password"}
  :default :sqlite              ; Which connection to use
  :main :sqlite}                ; For migrations
 
 :theme "sketchy"               ; Bootstrap theme
 :uploads "./uploads/rs/"       ; Upload directory
 :max-upload-mb 5}              ; Max file size
```

### Switch Database
Edit `:default` and `:main` keys to point to `:mysql`, `:postgres`, or `:sqlite`

---

## 🗄️ Database Commands

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

## 🗄️ Database Commands

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

## 🏗️ Scaffolding Commands

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

## 🔧 Development Commands

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

## 🌐 URLs (After Login)

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

## 📂 Key File Locations

### Entity Configurations
```
resources/entities/*.edn
```

### Engine Files
```
src/rs/engine/
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
src/rs/
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

## 🔍 REPL Commands

### Load Menu System
```clojure
(require '[rs.engine.menu :as menu])
(menu/discover-entities)        ; List all entities
(menu/generate-menu-items)      ; See menu structure
(menu/get-menu-config)          ; Full menu config
```

### Load Entity Config
```clojure
(require '[rs.engine.config :as config])
(config/load-entity-config :clientes)  ; Load specific entity
```

### Test Scaffold
```clojure
(require '[rs.engine.scaffold :as scaffold])
(scaffold/get-tables)           ; List database tables
```

---

## 🐛 Troubleshooting

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

## 📝 Quick Customizations

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
          :required? true
          :validator :rs.validators/check-email}]
```

### Add Hook
Edit entity config:
```clojure
:hooks {:before-save :rs.hooks/calculate-total
        :after-save :rs.hooks/send-email}
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

## 💡 Development Workflow

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

## 📚 Documentation Quick Links

- **QUICKSTART.md** - 5-minute tutorial
- **FRAMEWORK_GUIDE.md** - Complete framework API
- **HOOKS_GUIDE.md** - Business logic hooks
- **RUN_APP.md** - Deployment guide

---

## ⚡ Most Used Commands

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

**Keep this file handy for quick reference during development!** 📌
