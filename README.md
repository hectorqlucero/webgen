# WebGen - Parameter-Driven Web Application Framework

**Build enterprise web applications through declarative configuration, not code generation.**

## 🌟 Why WebGen?

- **Parameter-Driven**: Define entities in EDN files - no code generation needed
- **Hot Reload**: Change entity configs and hooks, refresh browser - no server restart
- **Database-Agnostic**: MySQL, PostgreSQL, SQLite with automatic migrations
- **Auto-Scaffolding**: `lein scaffold products` creates entity config, migrations, and hooks
- **Built for Enterprise**: MRP, Accounting, Inventory, POS-capable
- **Hook System**: Customize behavior without modifying core framework code

## ⚡ Quick Start

### Installation

```bash
# Local installation (for testing)
cd ~/Downloads/webgen
lein install

# Create new project
lein new webgen myapp
cd myapp
```

### First Run

```bash
# 1. Configure database
cp resources/private/config.clj.example resources/private/config.clj
# Edit config.clj with your database credentials

# 2. Initialize database
lein database

# 3. Start server
lein with-profile dev run
# Visit: http://localhost:8080
# Default credentials: admin/admin
```

### Create Your First Entity

```bash
lein scaffold products
```

This creates:
- `resources/entities/products.edn` - Entity configuration
- `resources/migrations/XXX-products.*.sql` - Database migrations
- `src/myapp/hooks/products.clj` - Hook file for customization

## 🎯 What's Different?

### Traditional Code Generation (v1)
```
lein grid users → Generates 3 files (225 lines)
Customize → Regenerate → LOSE CHANGES ❌
```

### Parameter-Driven (WebGen)
```
Create users.edn (80 lines) → Refresh browser → Done ✅
Modify config → Never lose changes
```

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| **No Code Generation** | Pure configuration-driven - edit EDN files, not generated code |
| **Hot Reload** | Change configs/hooks → refresh browser (no restart needed) |
| **Auto-Scaffolding** | `lein scaffold entity` creates everything |
| **Multi-Database** | MySQL, PostgreSQL, SQLite with vendor-specific migrations |
| **Subgrids** | Master-detail relationships with modal interfaces |
| **File Uploads** | Automatic handling via hooks (before-save/after-load) |
| **Hook System** | Customize without touching framework code |
| **Auto-Menu** | Menu generated from entity configs |
| **Modern UI** | Bootstrap 5 + DataTables |

## 📁 Entity Configuration

Entity configs live in `resources/entities/*.edn`:

```clojure
{:table :products
 :pk :id
 :title "Products"
 :menu-label "Products"
 :menu-group :catalog
 
 :columns
 [{:id :id :label "ID" :type :hidden}
  {:id :name :label "Name" :type :text :required true}
  {:id :price :label "Price" :type :number}
  {:id :image :label "Image" :type :file}]
 
 :hooks {:before-save :myapp.hooks.products/before-save
         :after-load :myapp.hooks.products/after-load}}
```

## 🪝 Hook System

Hooks let you customize behavior without modifying core code:

```clojure
(ns myapp.hooks.products)

(defn before-save [row]
  ;; Transform data before saving
  ;; E.g., handle file uploads, validate data
  (if (contains? row :image)
    (assoc row :file (:image row))  ; Trigger upload
    row))

(defn after-load [rows]
  ;; Transform data after loading
  ;; E.g., create image links
  (map #(assoc % :image (str "<img src='/uploads/" (:image %) "'>")) rows))
```

## 🗄️ Database Support

WebGen generates vendor-specific migrations automatically:

```bash
lein scaffold products
```

Creates:
- `001-products.mysql.up.sql` / `.down.sql`
- `001-products.postgresql.up.sql` / `.down.sql`
- `001-products.sqlite.up.sql` / `.down.sql`

Configure in `resources/private/config.clj`:

```clojure
{:connections
 {:mysql {:db-type "mysql"
          :db-name "//localhost:3306/mydb"
          :db-user "root"
          :db-pwd "password"}
  :postgres {:db-type "postgresql"
             :db-name "//localhost:5432/mydb"
             :db-user "postgres"
             :db-pwd "password"}
  :sqlite {:db-type "sqlite"
           :db-name "db/myapp.sqlite"}}
 :db :mysql}  ; Active connection
```

## 📸 Screenshots

| Screenshot | Description |
|------------|-------------|
| ![Screenshot 1](./images/screenshot-1.png) | Public Page |
| ![Screenshot 2](./images/screenshot-2.png) | Login Form |
| ![Screenshot 6](./images/screenshot-6.png) | Contactos Dashboard |
| ![Screenshot 10](./images/screenshot-10.png) | Contactos Grid |
| ![Screenshot 12](./images/screenshot-12.png) | Siblings Subgrid |
| ![Screenshot 15](./images/screenshot-15.png) | Grid Edit with thumbnail |
| ![Screenshot 17](./images/screenshot-17.png) | Grid showing thumbnails |

*All screenshots from default WebGen application. Fully customizable.*

## 📚 Documentation

Generated projects include comprehensive documentation:

- **QUICKSTART.md** - Get started quickly
- **FRAMEWORK_GUIDE.md** - Complete framework documentation
- **HOOKS_GUIDE.md** - Hook system and customization
- **DATABASE_MIGRATION_GUIDE.md** - Migration management
- **QUICK_REFERENCE.md** - Command reference
- **RUN_APP.md** - Running and deployment

## 🛠️ Common Commands

```bash
# Database
lein migrate              # Run migrations
lein rollback            # Rollback last migration
lein database            # Seed default users

# Development
lein with-profile dev run # Start dev server with auto-reload
lein compile              # Compile project

# Scaffolding
lein scaffold products    # Create new entity
```

## 🔒 Security

Built-in authentication and role-based access control:

- Login/logout functionality
- Password hashing (buddy-hashers)
- Session management
- Role-based menu visibility
- Protected routes

Default users (from `lein database`):
- **admin/admin** - Administrator
- **guest/guest** - Read-only

## 📦 Publishing to Clojars

```bash
cd ~/Downloads/webgen
# Update version in project.clj
lein deploy clojars
```

Then users can:
```bash
lein new webgen myapp  # Once published
```

## 📝 License

MIT License - see LICENSE file for details.

## 🤝 Contributing

Issues and pull requests welcome! This is an active project used in production environments.

## 🔗 Resources

- [Clojure](https://clojure.org/)
- [Leiningen](https://leiningen.org/)
- [Ring](https://github.com/ring-clojure/ring)
- [Compojure](https://github.com/weavejester/compojure)
- [Hiccup](https://github.com/weavejester/hiccup)
- [Bootstrap 5](https://getbootstrap.com/)
- [DataTables](https://datatables.net/)
