# Quick Start Template

## New Project Setup (5 Minutes)

### 1. Create Project
```bash
lein new org.clojars.hector/webgen my-project
cd my-project
```

### 2. Configure Database

Edit `resources/private/config.clj` with your settings:

```bash
nano resources/private/config.clj
# or
vim resources/private/config.clj
```

The file comes pre-configured with template values. Just update:
```clojure
{:connections
 {:sqlite {:db-type "sqlite"
           :db-class "org.sqlite.JDBC"
           :db-name "db/my-project.sqlite"}  ; ← Already set with your project name
  
  :mysql {:db-type "mysql"
          :db-class "com.mysql.cj.jdbc.Driver"
          :db-name "//localhost:3306/my-project"  ; ← Already set
          :db-user "root"
          :db-pwd "your_password"}  ; ← Update this
  
  :main :sqlite      ; Used for migrations
  :default :sqlite}  ; Used by application - SQLite by default
 
 :port 8080
 :uploads "./uploads/my-project/"  ; ← Already set with your project name
 :site-name "my-project"           ; ← Already set
 :theme "sketchy"}
```

### 3. Run Migrations
```bash
lein migrate      # Creates database schema
lein database     # Seeds default users (user/admin/system)
```

### 4. Start Development Server
```bash
lein with-profile dev run
```

Visit http://localhost:8080 (or your configured port)

**Note:** Port and database settings are configured in `resources/private/config.clj`

**Default Login:**
- Username: `system@system.com`
- Password: `password`

---

## Your First Entity (2 Minutes)

### Option 1: Use Scaffold Command (Recommended)

The `lein scaffold` command creates everything for you:

```bash
lein scaffold products
```

This creates:
- Entity config: `resources/entities/products.edn`
- Migrations for all databases: `resources/migrations/XXX-products.{mysql,postgresql,sqlite}.{up,down}.sql`
- Hook file: `src/{{name}}/hooks/products.clj`

Then run:
```bash
lein migrate
```

Visit: http://localhost:8080/admin/products

### Option 2: Manual Creation

If you prefer manual setup:

#### 1. Create Migration

`resources/migrations/002-products.sqlite.up.sql`:
```sql
CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  price REAL DEFAULT 0.0,
  stock INTEGER DEFAULT 0,
  active TEXT DEFAULT 'T'
);
```

`resources/migrations/002-products.sqlite.down.sql`:
```sql
DROP TABLE IF EXISTS products;
```

#### 2. Run Migration
```bash
lein migrate
```

#### 3. Create Entity Config

`resources/entities/products.edn`:
```clojure
{:entity :products
 :title "Products"
 :table "products"
 :menu-category :catalog
 
 :fields [{:id :id
           :label "ID"
           :type :hidden}
          
          {:id :code
           :label "Product Code"
           :type :text
           :required true
           :placeholder "SKU-001"}
          
          {:id :name
           :label "Product Name"
           :type :text
           :required true
           :placeholder "Enter product name..."}
          
          {:id :description
           :label "Description"
           :type :textarea
           :rows 5}
          
          {:id :price
           :label "Price"
           :type :decimal
           :required true
           :min 0
           :step 0.01
           :placeholder "0.00"}
          
          {:id :stock
           :label "Stock Quantity"
           :type :number
           :min 0
           :placeholder "0"}
          
          {:id :active
           :label "Status"
           :type :radio
           :value "T"
           :options [{:id "activeT" :label "Active" :value "T"}
                     {:id "activeF" :label "Inactive" :value "F"}]}]
 
 :queries {:list "SELECT * FROM products ORDER BY name"
           :get "SELECT * FROM products WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}}
```

#### 4. Access Your New Grid

Visit: http://localhost:8080/admin/products

**Done!** Full CRUD interface is ready.

---

## Adding to Navigation Menu

Edit `src/{{name}}/menu.clj`:

```clojure
(def menu-items
  [{:label "Products" :href "/admin/products" :icon "bi-box-seam" :level "U"}
   {:label "Customers" :href "/admin/customers" :icon "bi-people" :level "U"}
   ;; Add more menu items...
   ])
```

---

## Common Patterns

### All Available Field Types

WebGen supports the following field types:

```clojure
;; Text inputs
{:id :name :label "Name" :type :text :placeholder "Enter name..." :required true}
{:id :notes :label "Notes" :type :textarea :rows 5 :placeholder "Enter notes..."}

;; Numeric inputs
{:id :quantity :label "Quantity" :type :number :min 0 :max 1000}
{:id :price :label "Price" :type :decimal :min 0 :step 0.01 :placeholder "0.00"}

;; Date/time inputs
{:id :birthdate :label "Birth Date" :type :date}
{:id :created_at :label "Created" :type :datetime}
{:id :opening_time :label "Opens At" :type :time}

;; Selection inputs
{:id :category :label "Category" :type :select 
 :options [{:value "A" :label "Category A"} {:value "B" :label "Category B"}]}

{:id :status :label "Status" :type :radio :value "active"
 :options [{:id "statusActive" :label "Active" :value "active"}
           {:id "statusInactive" :label "Inactive" :value "inactive"}]}

{:id :featured :label "Featured" :type :checkbox :value "T"}

;; Special inputs
{:id :email :label "Email" :type :email :placeholder "user@example.com"}
{:id :password :label "Password" :type :password}
{:id :imagen :label "Image" :type :file}
{:id :user_id :label "User ID" :type :hidden}
{:id :property_id :label "Property" :type :fk :fk :property :fk-field [:titulo :estado :contacto]}
```

### Dropdown with Database Values

```clojure
;; Create a query in model
(ns {{name}}.models.lookups
  (:require [{{name}}.models.crud :as crud]))

(defn get-categories []
  (crud/Query "SELECT id AS value, name AS label FROM categories ORDER BY name"
              :conn :default))

;; In entity config
{:id :category_id
 :label "Category"
 :type :select
 :options :{{name}}.models.lookups/get-categories}
```

### Parent-Child Relationship

```clojure
;; Parent entity
{:entity :orders
 :subgrids [{:entity :order-items
             :foreign-key :order_id}]}

;; Child entity
{:entity :order-items
 :fields [{:id :order_id :type :hidden}
          {:id :product_id :label "Product" :type :select}
          {:id :quantity :label "Quantity" :type :number}]}
```

### Computed Field

```clojure
;; Create compute function
(ns {{name}}.hooks.orders)

(defn calculate-total [row]
  (* (:quantity row) (:price row)))

;; In entity config
{:id :total
 :label "Total"
 :type :computed
 :compute-fn :{{name}}.hooks.orders/calculate-total}
```

### Validation

```clojure
;; Create validator
(ns {{name}}.validators.products)

(defn positive-price?
  "Always return a boolean - value is a string - coerce to BigDecimal (bigdec value)"
  [value data]
  (let [n (bigdec value)]
    (and n (> n 0))))

;; In entity config
{:id :price
 :validation :{{name}}.validators.products/positive-price?}
```

---

## Project Structure

```
my-project/
├── project.clj                      # Leiningen config
├── resources/
│   ├── entities/                    # Entity configurations (EDN)
│   │   ├── users.edn
│   │   ├── products.edn
│   │   └── customers.edn
│   ├── migrations/                  # Database migrations (SQL)
│   │   ├── 001-users.sqlite.up.sql
│   │   └── 002-products.sqlite.up.sql
│   └── private/
│       └── config.clj               # Database config
├── src/{{name}}/
│   ├── engine/                      # Framework core (DON'T MODIFY)
│   │   ├── config.clj
│   │   ├── query.clj
│   │   ├── crud.clj
│   │   ├── render.clj
│   │   └── router.clj
│   ├── hooks/                       # Business logic hooks
│   │   ├── users.clj
│   │   └── products.clj
│   ├── validators/                  # Custom validators
│   ├── queries/                     # Complex queries
│   ├── views/                       # Custom UI renderers
│   ├── models/                      # Shared models
│   ├── core.clj                     # Main entry point
│   ├── layout.clj                   # Page layout
│   └── menu.clj                     # Navigation menu
└── uploads/                         # File uploads
```

---

## Development Workflow

### 1. Design Database Schema
Create migration files in `resources/migrations/`

### 2. Run Migration
```bash
lein migrate
```

### 3. Create Entity Config
Add EDN file to `resources/entities/`

### 4. Refresh Browser
Changes are live immediately!

### 5. Add Business Logic (if needed)
Create hooks in `src/{{name}}/hooks/`

### 6. Customize UI (if needed)
Create custom renderers in `src/{{name}}/views/`

---

## Testing

```bash
# Run tests
lein test

# REPL testing
lein repl

# In REPL
(require '[{{name}}.engine.config :as config])
(config/list-available-entities)
(config/load-entity-config :products)

(require '[{{name}}.engine.query :as query])
(query/list-records :products)
```

---

## Deployment

### Build Standalone JAR
```bash
lein uberjar
```

### Run in Production
```bash
java -jar target/uberjar/rs.jar
```

### Moving from SQLite to MySQL/PostgreSQL

When moving from development (SQLite) to production (MySQL/PostgreSQL):

```bash
# 1. Convert migrations
lein convert-migrations mysql

# 2. Review and refine generated files
vim resources/migrations/*.mysql.up.sql

# 3. Update config.clj to use MySQL
vim resources/private/config.clj

# 4. Run migrations on production database
lein migrate

# 5. Copy data from SQLite (optional)
lein copy-data mysql
```

See [DATABASE_MIGRATION_GUIDE.md](DATABASE_MIGRATION_GUIDE.md) for complete details.

### Environment Variables
Set via config.clj or environment:
```bash
export DB_HOST=production-db.example.com
export DB_USER=appuser
export DB_PASSWORD=secret
java -jar rs.jar
```

---

## Next Steps

1. **Read the Full Guide**: `FRAMEWORK_GUIDE.md`
2. **Database Migration**: `DATABASE_MIGRATION_GUIDE.md`
3. **Explore Examples**: Check `resources/entities/` for samples
4. **Join Community**: GitHub Discussions
5. **Build Your App**: Start with simple entities, add complexity as needed

---

## Need Help?

- 📖 Full Documentation: `FRAMEWORK_GUIDE.md`
- 🐛 Report Issues: GitHub Issues
- 💬 Ask Questions: GitHub Discussions
- 📧 Email: support@example.com

**Happy Building! **
