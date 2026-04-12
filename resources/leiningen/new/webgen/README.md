# {{name}}

This project was generated with [WebGen](https://clojars.org/org.clojars.hector/webgen), a parameter-driven Clojure web framework. Application behavior is controlled entirely by EDN configuration files in `resources/entities/`. Editing a config file and refreshing the browser is all that is needed to change the application — no server restart required during development.

---

## Quick Start

### 1. Configure the Database

Edit `resources/config/app-config.edn`. The default configuration uses SQLite and works without changes for local development.

For MySQL or PostgreSQL, update the credentials and change `:default` and `:main` to the appropriate connection key.

### 2. Run Migrations and Seed Users

```bash
lein migrate
lein database
```

Default users created:

| Email | Password | Level |
|---|---|---|
| `user@example.com` | `user` | U (regular user) |
| `admin@example.com` | `admin` | A (administrator) |
| `system@example.com` | `system` | S (system) |

Change all passwords before deploying to production.

### 3. Start the Development Server

```bash
lein with-profile dev run
```

Visit `http://localhost:8080` and log in with `admin@example.com` / `admin`.

The dev server hot-reloads entity configurations every two seconds. Hook files reload on change. No restart needed.

---

## Project Structure

```
resources/
  entities/        Entity EDN configuration files (one per entity)
  migrations/      Database migration SQL files
  config/
    app-config.edn Application and database configuration
  i18n/
    en.edn         English translations
    es.edn         Spanish translations
  public/          Static assets (CSS, JS, images)

src/{{sanitized}}/
  core.clj         Application entry point and middleware
  layout.clj       Page layout template
  menu.clj         Menu customization
  engine/          Framework core
  hooks/           Business logic hooks (one file per entity)
  routes/
    routes.clj     Public routes (no authentication required)
    proutes.clj    Protected routes (authentication required)
  handlers/        Custom MVC handlers
```

---

## Adding an Entity

### Scaffold from an existing database table

```bash
lein scaffold products
```

This creates `resources/entities/products.edn`, migration files, and `src/{{sanitized}}/hooks/products.clj`. Refresh the browser and the entity appears in the menu.

### Scaffold all tables at once

```bash
lein scaffold --all
```

### Create manually

1. Write a migration in `resources/migrations/`:

```sql
-- 001-products.sqlite.up.sql
CREATE TABLE IF NOT EXISTS products (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  name    TEXT NOT NULL,
  price   REAL DEFAULT 0.0,
  active  TEXT DEFAULT 'T'
);
```

2. Run it:

```bash
lein migrate
```

3. Create `resources/entities/products.edn`:

```clojure
{:entity        :products
 :title         "Products"
 :table         "products"
 :connection    :default
 :rights        ["U" "A" "S"]
 :menu-category :catalog
 :menu-order    10

 :fields [{:id :id     :type :hidden}
          {:id :name   :label "Name"   :type :text    :required? true}
          {:id :price  :label "Price"  :type :decimal :min 0 :step 0.01}
          {:id :active :label "Active" :type :radio   :value "T"
           :options [{:id "aT" :value "T" :label "Yes"}
                     {:id "aF" :value "F" :label "No"}]}]

 :queries {:list "SELECT * FROM products ORDER BY name"
           :get  "SELECT * FROM products WHERE id = ?"}

 :actions {:new true :edit true :delete true}}
```

4. Refresh the browser — the entity is live.

---

## Database Commands

```bash
lein migrate                          # Apply pending migrations
lein rollback                         # Rollback the last migration
lein database                         # Seed default users
lein convert-migrations mysql         # Convert SQLite migrations to MySQL
lein convert-migrations postgresql    # Convert SQLite migrations to PostgreSQL
lein copy-data mysql                  # Copy SQLite data to MySQL
lein copy-data postgresql             # Copy SQLite data to PostgreSQL
```

---

## Development Commands

```bash
lein with-profile dev run    # Start dev server with hot-reload
lein run                     # Start production server
lein compile                 # Compile
lein test                    # Run tests
lein uberjar                 # Build standalone JAR
lein repl                    # Start REPL
```

### Production

```bash
lein uberjar
java -jar target/uberjar/{{name}}-0.1.0-standalone.jar
```

---

## Configuration Reference

`resources/config/app-config.edn`:

```clojure
{:connections
 {:sqlite   {:db-type  "sqlite"
             :db-class "org.sqlite.JDBC"
             :db-name  "db/{{name}}.sqlite"}

  :mysql    {:db-type  "mysql"
             :db-class "com.mysql.cj.jdbc.Driver"
             :db-name  "//localhost:3306/{{name}}"
             :db-user  "root"
             :db-pwd   "password"}

  :postgres {:db-type  "postgresql"
             :db-class "org.postgresql.Driver"
             :db-name  "//localhost:5432/{{name}}"
             :db-user  "postgres"
             :db-pwd   "password"}

  :default :sqlite   ; Connection used by entities
  :main    :sqlite}  ; Connection used by migrations

 :port          8080
 :site-name     "{{name}}"
 :uploads       "./uploads/{{name}}/"
 :max-upload-mb 5
 :theme         "sketchy"}
```

To switch to MySQL or PostgreSQL, change `:default` and `:main` to `:mysql` or `:postgres`.

---

## Included Example Entities

The generated project includes four pre-configured entities demonstrating common patterns:

- **Users** — User management with authentication and roles
- **Contactos** — File upload handling via hooks (`before-save` / `after-load`)
- **Cars** — Subgrid child of Contactos; hidden from main menu
- **Siblings** — Another subgrid child of Contactos; hidden from main menu

These can be removed or used as a starting point.

---

## Troubleshooting

**Entity not showing in menu**
Verify `resources/entities/your_entity.edn` exists, is valid EDN, and that `:menu-hidden?` is not set to `true`.

**Database table not found**
Run `lein migrate` to apply pending migrations.

**Permission denied**
Update `:rights` in the entity configuration, or check the user's access level.

**Port already in use**

```bash
lsof -i :8080
kill -9 <PID>
```

**Force config reload in REPL**

```clojure
(require '[{{sanitized}}.engine.config :as config])
(config/reload-all!)
```

---

## License

MIT License. See LICENSE for details.
