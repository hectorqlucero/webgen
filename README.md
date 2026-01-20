# WebGen - Parameter-Driven Web Application Framework

**Build enterprise web applications through declarative configuration, not code generation.**

## **Highlights:** Why WebGen?

- **Parameter-Driven**: Define entities in EDN files - no code generation needed
- **Hot Reload**: Change entity configs and hooks, refresh browser - no server restart
- **Database-Agnostic**: MySQL, PostgreSQL, SQLite with automatic migrations
- **Auto-Scaffolding**: `lein scaffold products` creates entity config, migrations, and hooks
- **Built for Enterprise**: MRP, Accounting, Inventory, POS-capable
- **Hook System**: Customize behavior without modifying core framework code

## **Performance:** Quick Start

### Installation

```bash
# Create new project from Clojars
lein new org.clojars.hector/webgen myapp
cd myapp
```

**Note:** The template is published to Clojars, so no manual installation needed!

### First Run

```bash
# 1. Configure database
# Edit config.clj with your database credentials
# Default uses SQLite - just update passwords for MySQL/PostgreSQL if needed
nano resources/private/config.clj

# 2. Run migrations
lein migrate

# 3. Seed database with default users
lein database

# 4. Start server
lein with-profile dev run
# Visit: http://localhost:8080
# Default credentials: admin@example.com / admin
```

### Included Example Entities

The generated project includes four pre-configured entities to help you get started:

- **Users** - User management with authentication and roles
- **Contactos** - Main entity demonstrating file uploads (images)
- **Cars** - Subgrid example (child of Contactos) with file uploads
- **Siblings** - Another subgrid example (child of Contactos)

These demonstrate:
- Master-detail relationships (subgrids in modal windows)
- File upload handling via hooks (before-save/after-load)
- Menu organization and visibility (Cars and Siblings hidden from main menu)
- Form validation and various field types

**You can explore these examples or remove them to start fresh.**

### Create Additional Entities

```bash
lein scaffold products
```

This creates:
- `resources/entities/products.edn` - Entity configuration
- `resources/migrations/XXX-products.*.sql` - Database migrations
- `src/myapp/hooks/products.clj` - Hook file for customization

## **Important:** What's Different?

### Traditional Code Generation (v1)
```
lein grid users → Generates 3 files (225 lines)
Customize → Regenerate → LOSE CHANGES
```

### Parameter-Driven (WebGen)
```
Create users.edn (80 lines) → Refresh browser → Done
Modify config → Never lose changes
```

## **Features:** Key Features

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

## Entity Configuration

Entity configs define everything about a CRUD interface. Located in `resources/entities/*.edn`:

```clojure
{:table :products                    ; Database table name
 :pk :id                             ; Primary key column
 :title "Products"                   ; Page title
 :menu-label "Products"              ; Menu display text
 :menu-category :catalog             ; Menu grouping (:catalog, :admin, etc.)
 :menu-hidden? false                 ; Hide from menu (for subgrids)
 
 ;; Field definitions
 :columns
 [{:id :id 
   :label "ID" 
   :type :hidden}                    ; Hidden field (PK)
  
  {:id :name 
   :label "Product Name" 
   :type :text                       ; Text input
   :required? true                   ; Validation
   :placeholder "Enter product name"}
  
  {:id :description 
   :label "Description" 
   :type :textarea                   ; Textarea input
   :rows 5}
  
  {:id :price 
   :label "Price" 
   :type :number                     ; Number input
   :min 0}

   {:id :price
    :label "Price"
    :type :decimal
    :min 0
    :step 0.01
    :placeholder "0.00"}            ; decimal input
  
  {:id :category 
   :label "Category" 
   :type :select                     ; Dropdown
   :options [{:value "electronics" :label "Electronics"}
             {:value "clothing" :label "Clothing"}
             {:value "food" :label "Food"}]}

  {:id :categories_id
   :label "Category"
   :type :fk
   :fk :categories
   :fk-field [:name]  ; you can have more than one [:name :email :phone]
   :required? true}                   ; FK field, does not require fk in db

   {:id :categories_id
    :label "Category"
    :type :select
    :options :myapp.models.lookups/get-categories} ; Dropdown with Database Values

  {:id :total                  
   :type :computed
   :compute-fn :myapp.hooks.products/calculate-total  ; computed

  {:id :price
   :label "Price"
   :type :decimal
   :min 0
   :step 0.01
   :placeholder "0.00"
   :required? true
   :validation :myapp.validators.products/positive-price?} ; validator

  
  {:id :active 
   :label "Active" 
   :type :checkbox}                  ; Checkbox
  
  {:id :image 
   :label "Product Image" 
   :type :file}]                     ; File upload
 
 ;; Hook registration (all optional)
 :hooks {:before-load :myapp.hooks.products/before-load
         :after-load :myapp.hooks.products/after-load
         :before-save :myapp.hooks.products/before-save
         :after-save :myapp.hooks.products/after-save
         :before-delete :myapp.hooks.products/before-delete
         :after-delete :myapp.hooks.products/after-delete}
 
 ;; Subgrids (master-detail relationships)
 :subgrids [{:entity :reviews          ; Child entity
             :fk :product_id           ; Foreign key in child table
             :label "Product Reviews"}]}
```

### Supported Field Types

| Type | Description | Example | Options |
|------|-------------|---------|---------|
| `:text` | Single-line text input | Name, SKU, title | `placeholder`, `required` |
| `:textarea` | Multi-line text input | Description, notes | `rows`, `placeholder` |
| `:number` | Numeric integer input | Quantity, age | `min`, `max`, `placeholder` |
| `:decimal` | Decimal/float input | Price, percentage | `min`, `max`, `step`, `placeholder` |
| `:email` | Email input with validation | Email address | `placeholder`, `required` |
| `:password` | Password input (masked) | Password field | `placeholder`, `required` |
| `:date` | Date picker | Birth date, expiry | `min`, `max` |
| `:datetime` | Date and time picker | Created timestamp | `min`, `max` |
| `:time` | Time picker | Event time | `min`, `max` |
| `:select` | Dropdown select | Category, status | `options` (array of `{:value :label}`) |
| `:radio` | Radio button group | Status, type | `options` (array with `:id`, `:label`, `:value`) |
| `:checkbox` | Single checkbox | Active, featured | `value` (default checked value) |
| `:file` | File upload | Image, PDF | Handled via hooks |
| `:hidden` | Hidden field | ID, foreign keys | `value` |
| `:computed` | Calculated/display only | Total, full name | Read-only, computed via hooks |

### Menu Organization

Entities are automatically grouped in menus by `:menu-group`:

- `:admin` → Administration
- `:catalog` → Catalog  
- `:sales` → Sales
- `:reports` → Reports
- `:system` → System

Or hide from menu with `:menu-hidden? true` (useful for subgrids).

## Hook System

Hooks let you customize behavior without modifying core code. All hooks are optional.

### Available Hooks

```clojure
(ns myapp.hooks.products)

(defn before-load [params]
  ;; Called before querying database
  ;; Modify query parameters, add filters
  ;; params = {:entity :products :filters {...} :user {...}}
  params)

(defn after-load [rows]
  ;; Called after loading data from database
  ;; Transform display data (e.g., format dates, create links)
  ;; rows = [{:id 1 :name "Product" :image "photo.jpg"} ...]
  (map #(assoc % :image (str "<img src='/uploads/" (:image %) "'>")) rows))

(defn before-save [row]
  ;; Called before saving to database
  ;; Validate, transform, handle file uploads
  ;; row = {:id 1 :name "Product" :image "photo.jpg"}
  (if (contains? row :image)
    (assoc row :file (:image row))  ; Trigger file upload
    row))

(defn after-save [row]
  ;; Called after saving to database
  ;; Send notifications, update related records
  ;; row = {:id 1 :name "Product" ...}
  (println "Saved product:" (:id row))
  row)

(defn before-delete [id]
  ;; Called before deleting record
  ;; Validate deletion, clean up related data
  ;; id = 123
  (println "Deleting product:" id)
  id)

(defn after-delete [id]
  ;; Called after deleting record
  ;; Clean up files, update related records
  ;; id = 123
  (println "Deleted product:" id)
  id)
```

### Hook Registration

Register hooks in entity config `resources/entities/products.edn`:

```clojure
{:table :products
 :pk :id
 :title "Products"
 
 :hooks {:before-load :myapp.hooks.products/before-load
         :after-load :myapp.hooks.products/after-load
         :before-save :myapp.hooks.products/before-save
         :after-save :myapp.hooks.products/after-save
         :before-delete :myapp.hooks.products/before-delete
         :after-delete :myapp.hooks.products/after-delete}}
```

### Common Use Cases

**File Uploads:**
```clojure
(defn before-save [row]
  (if (contains? row :imagen)
    (assoc row :file (:imagen row))  ; Framework handles upload
    row))

(defn after-load [rows]
  (map #(assoc % :imagen (image-link (:imagen %))) rows))
```

**Data Validation:**
```clojure
(defn before-save [row]
  (when (< (:price row) 0)
    (throw (ex-info "Price cannot be negative" {:price (:price row)})))
  row)
```

**Automatic Timestamps:**
```clojure
(defn before-save [row]
  (assoc row :updated_at (java.time.LocalDateTime/now)))
```

## **Database** Database Support

WebGen generates vendor-specific migrations automatically.

### Multi-Database Architecture

```bash
lein scaffold products
```

Creates migrations for all supported databases:
- `001-products.mysql.up.sql` / `.down.sql`
- `001-products.postgresql.up.sql` / `.down.sql`  
- `001-products.sqlite.up.sql` / `.down.sql`

Switch databases without code changes - just update config!

### Configuration

Edit `resources/private/config.clj`:

```clojure
{:connections
 {;; --- Mysql database ---
  :mysql {:db-type   "mysql"                                 ;; "mysql", "postgresql", "sqlite", etc.
          :db-class  "com.mysql.cj.jdbc.Driver"              ;; JDBC driver class
          :db-name   "//localhost:3306/your_dbname"          ;; JDBC subname (host:port/db)
          :db-user   "root"
          :db-pwd    "your_password"}

  ;; --- Local SQLite database ---
  :sqlite {:db-type   "sqlite"
           :db-class  "org.sqlite.JDBC"
           :db-name   "db/your_dbname.sqlite"}               ;; No user/pwd needed for SQLite

  ;; --- PostgreSQL database ---
  :postgres {:db-type   "postgresql"
             :db-class  "org.postgresql.Driver"
             :db-name   "//localhost:5432/your_dbname"
             :db-user   "root"
             :db-pwd    "your_password"}

  ;; --- Default connection used by the app ---
  :main :sqlite ; Used for migrations (SQLite by default for easy prototyping)
  :default :sqlite ; Used for application (switch to :mysql or :postgres for production)
  :db :mysql
  :pg :postgres
  :localdb :sqlite}

 ;; --- Other global app settings ---
 :uploads      "./uploads/your_dbname/"    ;; Path for file uploads
 :site-name    "your_site_name"            ;; App/site name
 :company-name "your_company_name"         ;; Company name
 :port         3000                        ;; App port
 :tz           "US/Pacific"                ;; Timezone
 :base-url     "http://0.0.0.0:3000/"      ;; Base URL
 :img-url      "https://0.0.0.0/uploads/"  ;; Image base URL
 :path         "/uploads/"                 ;; Uploads path (for web)
 :max-upload-mb 5                            ;; Optional: max image upload size in MB
 :allowed-image-exts ["jpg" "jpeg" "png" "gif" "bmp" "webp"] ;; Optional: allowed image extensions
 ;; --- Theme selection ---
 :theme "sketchy" ;; Options: "default" (Bootstrap), "cerulean", "slate", "minty", "lux", "cyborg", "sandstone", "superhero", "flatly", "yeti"
 ;; Optional email config
 :email-host   "smtp.example.com"
 :email-user   "user@example.com"
 :email-pwd    "emailpassword"}
```

### Migration Commands

```bash
lein migrate              # Apply pending migrations
lein rollback             # Rollback last migration
lein database             # Seed default users
                          # (admin@example.com/admin, user@example.com/user, system@example.com/system)
```

## **Documentation:** Documentation

Generated projects include comprehensive documentation:

- **QUICKSTART.md** - Get started quickly
- **FRAMEWORK_GUIDE.md** - Complete framework documentation
- **HOOKS_GUIDE.md** - Hook system and customization
- **DATABASE_MIGRATION_GUIDE.md** - Migration management
- **QUICK_REFERENCE.md** - Command reference
- **RUN_APP.md** - Running and deployment

## **Tools:** Common Commands

```bash
# Project Creation
lein new webgen myapp                # Create new project
cd myapp

# Database Setup
lein migrate                         # Run migrations
lein rollback                        # Rollback last migration
lein database                        # Seed default users
                                     # (admin@example.com/admin, user@example.com/user, system@example.com/system)

# Development
lein with-profile dev run            # Start dev server (port 8080)
                                     # Auto-reloads on config/hook changes
lein compile                         # Compile project

# Scaffolding
lein scaffold products               # Create entity, migrations, hooks
                                     # - resources/entities/products.edn
                                     # - resources/migrations/XXX-products.*.sql
                                     # - src/myapp/hooks/products.clj

# Testing
lein test                            # Run tests

# Production
lein uberjar                         # Build standalone JAR
java -jar target/myapp.jar           # Run production server
```

### Auto-Reload Feature

The dev server watches for changes and reloads automatically:

- **Entity configs** (`resources/entities/*.edn`) - Reloads every 2 seconds
- **Hook files** (`src/myapp/hooks/*.clj`) - Reloads on file change
- **No server restart needed** - Just refresh your browser!

## Security

Built-in authentication and role-based access control.

### Default Users

After running `lein database`:

| Username | Password | Role | Level | Access |
|----------|----------|------|-------|--------|
| user@example.com | user | Regular User | U | Standard access |
| admin@example.com | admin | Administrator | A | Full access |
| system@example.com | system | System | S | System-level access |

****WARNING** Change default passwords in production!**

**Login with:** `admin@example.com` / `admin`

### Authentication Features

- **Password Hashing** - buddy-hashers with bcrypt
- **Session Management** - Secure cookie-based sessions
- **Login/Logout** - Built-in authentication pages
- **Protected Routes** - Middleware guards private pages
- **Role-Based Access** - Control menu visibility by role

### Customizing Access

Edit `src/myapp/core.clj` to modify authentication logic:

```clojure
(defn wrap-login [handler]
  ;; Customize authentication check
  ;; Redirect unauthorized users
  ...)
```

Configure in entity configs:
```clojure
{:table :admin_only_entity
 :menu-group :admin
 :required-role :administrator}  ; Restrict by role
```

---

## **Design:** Advanced Customization (The 20%)

While 80% of your application is configuration-driven, the framework provides full control for the remaining 20% through manual customization.

### Manual Menu Customization

Edit `src/myapp/menu.clj` to add custom menu items that don't come from entities.

#### Custom Navigation Links

```clojure
(def custom-nav-links
  "Custom navigation links (non-dropdown)"
  [{:href "/dashboard" :label "Dashboard"}
   {:href "/reports" :label "Reports"}
   {:href "/analytics" :label "Analytics"}])
```

#### Custom Dropdown Menus

```clojure
(def custom-dropdowns
  "Custom dropdown menus"
  {:reports {:label "Reports"
             :items [{:href "/reports/sales" :label "Sales Report"}
                     {:href "/reports/inventory" :label "Inventory Report"}
                     {:href "/reports/customers" :label "Customer Report"}]}
   
   :tools {:label "Tools"
           :items [{:href "/tools/import" :label "Import Data"}
                   {:href "/tools/export" :label "Export Data"}
                   {:href "/tools/backup" :label "Backup Database"}]}})
```

#### Merging Custom and Auto-Generated Menus

```clojure
(defn get-menu-config
  "Returns the complete menu configuration with custom overrides"
  []
  (let [auto-generated (auto-menu/get-menu-config)]
    {:nav-links (concat (:nav-links auto-generated) custom-nav-links)
     :dropdowns (merge (:dropdowns auto-generated) custom-dropdowns)}))
```

**Result:** Custom items appear alongside entity-based menu items.

---

### **Routes** Custom Routes

WebGen separates routes into two categories:

#### 1. Open Routes (Public Access)

Edit `src/myapp/routes/routes.clj` for public pages:

```clojure
(ns myapp.routes.routes
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [myapp.handlers.home.controller :as home]
   [myapp.handlers.reports.controller :as reports]))

(defroutes open-routes
  ;; Built-in authentication routes
  (GET "/" [] (home/main))
  (GET "/home/login" [] (home/login))
  (POST "/home/login" [] (home/login-user))
  (GET "/home/logoff" [] (home/logoff-user))
  
  ;; Your custom public routes
  (GET "/about" [] (home/about-page))
  (GET "/contact" [] (home/contact-page))
  (POST "/contact" [] (home/process-contact))
  (GET "/api/public/data" [] (reports/public-api)))
```

#### 2. Protected Routes (Authenticated Access)

Edit `src/myapp/routes/proutes.clj` for authenticated pages:

```clojure
(ns myapp.routes.proutes
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [myapp.handlers.dashboard.controller :as dashboard]
   [myapp.handlers.reports.controller :as reports]
   [myapp.handlers.api.controller :as api]))

(defroutes proutes
  ;; Custom dashboards
  (GET "/dashboard" [] (dashboard/main))
  (GET "/dashboard/sales" [] (dashboard/sales))
  (GET "/dashboard/analytics" [] (dashboard/analytics))
  
  ;; Custom reports
  (GET "/reports/sales" [] (reports/sales-report))
  (GET "/reports/inventory" [] (reports/inventory-report))
  (POST "/reports/generate" [] (reports/generate-custom-report))
  
  ;; RESTful API endpoints
  (GET "/api/customers/:id" [id] (api/get-customer id))
  (POST "/api/orders" [] (api/create-order))
  (PUT "/api/products/:id" [id] (api/update-product id))
  
  ;; Background jobs
  (POST "/admin/sync-data" [] (api/sync-external-data))
  (GET "/admin/clear-cache" [] (api/clear-cache)))
```

**Note:** All `proutes` require authentication automatically via middleware.

---

### Advanced Hook Patterns

Hooks provide deep customization without modifying framework code.

#### Complex Data Transformations

```clojure
(ns myapp.hooks.orders)

(defn after-load [rows]
  ;; Calculate derived fields
  (map (fn [row]
         (let [subtotal (* (:quantity row) (:price row))
               tax (* subtotal 0.08)
               total (+ subtotal tax)]
           (assoc row
                  :subtotal subtotal
                  :tax tax
                  :total total
                  :status-badge (status-badge (:status row)))))
       rows))

(defn status-badge [status]
  (case status
    "pending" "<span class='badge bg-warning'>Pending</span>"
    "completed" "<span class='badge bg-success'>Completed</span>"
    "cancelled" "<span class='badge bg-danger'>Cancelled</span>"
    "<span class='badge bg-secondary'>Unknown</span>"))
```

#### Multi-File Upload Handling

```clojure
(ns myapp.hooks.products)

(defn before-save [row]
  ;; Handle multiple file uploads
  (cond-> row
    ;; Main product image
    (contains? row :image)
    (assoc :file (:image row))
    
    ;; Product thumbnail
    (contains? row :thumbnail)
    (assoc :file_thumb (:thumbnail row))
    
    ;; Product PDF datasheet
    (contains? row :datasheet)
    (assoc :file_pdf (:datasheet row))))

(defn after-save [row]
  ;; Generate thumbnails after save
  (when (:image row)
    (generate-thumbnail (:image row)))
  
  ;; Update search index
  (update-search-index row)
  
  ;; Notify warehouse
  (notify-inventory-system row)
  
  row)
```

#### Dynamic Query Filtering

```clojure
(ns myapp.hooks.orders)

(defn before-load [params]
  ;; Add user-specific filters
  (let [user (:user params)
        role (:role user)]
    (cond
      ;; Admins see everything
      (= role :admin)
      params
      
      ;; Salespeople see only their orders
      (= role :salesperson)
      (assoc-in params [:filters :salesperson_id] (:id user))
      
      ;; Customers see only their orders
      (= role :customer)
      (assoc-in params [:filters :customer_id] (:id user))
      
      ;; Default: no access
      :else
      (assoc params :filters {:id -1}))))  ; Returns no results
```

#### Complex Validation

```clojure
(ns myapp.hooks.invoices)

(defn before-save [row]
  ;; Business rule validation
  (validate-invoice row)
  
  ;; Auto-calculate fields
  (let [items (fetch-invoice-items (:id row))
        subtotal (reduce + (map :total items))
        tax (* subtotal (:tax_rate row))
        total (+ subtotal tax)]
    (assoc row
           :subtotal subtotal
           :tax tax
           :total total
           :updated_at (java.time.LocalDateTime/now))))

(defn validate-invoice [row]
  (when (< (:total row) 0)
    (throw (ex-info "Invoice total cannot be negative" {:row row})))
  
  (when (empty? (:customer_id row))
    (throw (ex-info "Customer is required" {:row row})))
  
  (when (< (count (:items row)) 1)
    (throw (ex-info "Invoice must have at least one item" {:row row}))))
```

#### Cascade Operations

```clojure
(ns myapp.hooks.customers)

(defn after-delete [id]
  ;; Cascade delete related records
  (delete-customer-addresses id)
  (delete-customer-orders id)
  (delete-customer-notes id)
  
  ;; Update analytics
  (update-customer-count)
  
  ;; Audit log
  (log-customer-deletion id)
  
  id)
```

---

### **Architecture** Custom Handlers (MVC Pattern)

Create custom handlers for non-CRUD functionality.

#### Directory Structure

```
src/myapp/handlers/
├── home/
│   ├── controller.clj  (Route handlers)
│   ├── model.clj       (Business logic)
│   └── view.clj        (HTML rendering)
├── dashboard/
│   ├── controller.clj
│   ├── model.clj
│   └── view.clj
└── reports/
    ├── controller.clj
    ├── model.clj
    └── view.clj
```

#### Example: Custom Dashboard

**Controller** (`src/myapp/handlers/dashboard/controller.clj`):

```clojure
(ns myapp.handlers.dashboard.controller
  (:require
   [myapp.handlers.dashboard.model :as model]
   [myapp.handlers.dashboard.view :as view]))

(defn main [request]
  (let [user (get-in request [:session :user])
        stats (model/get-dashboard-stats user)
        recent-orders (model/get-recent-orders user 10)
        alerts (model/get-alerts user)]
    (view/dashboard-page stats recent-orders alerts)))
```

**Model** (`src/myapp/handlers/dashboard/model.clj`):

```clojure
(ns myapp.handlers.dashboard.model
  (:require
   [myapp.models.db :as db]))

(defn get-dashboard-stats [user]
  {:total-orders (db/query-one 
                   "SELECT COUNT(*) as count FROM orders WHERE user_id = ?" 
                   [(:id user)])
   :revenue (db/query-one 
              "SELECT SUM(total) as sum FROM orders WHERE user_id = ?" 
              [(:id user)])
   :pending-orders (db/query-one 
                     "SELECT COUNT(*) as count FROM orders 
                      WHERE user_id = ? AND status = 'pending'" 
                     [(:id user)])})

(defn get-recent-orders [user limit]
  (db/query 
    "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT ?" 
    [(:id user) limit]))

(defn get-alerts [user]
  (db/query 
    "SELECT * FROM alerts WHERE user_id = ? AND dismissed = false" 
    [(:id user)]))
```

**View** (`src/myapp/handlers/dashboard/view.clj`):

```clojure
(ns myapp.handlers.dashboard.view
  (:require
   [myapp.layout :as layout]
   [hiccup.core :refer [html]]))

(defn dashboard-page [stats recent-orders alerts]
  (layout/application
    "Dashboard"
    (html
      [:div.container.mt-4
       [:h1 "Dashboard"]
       
       ;; Stats cards
       [:div.row
        [:div.col-md-4
         [:div.card
          [:div.card-body
           [:h5.card-title "Total Orders"]
           [:p.card-text.display-4 (:count (:total-orders stats))]]]]
        [:div.col-md-4
         [:div.card
          [:div.card-body
           [:h5.card-title "Revenue"]
           [:p.card-text.display-4 "$" (:sum (:revenue stats))]]]]
        [:div.col-md-4
         [:div.card
          [:div.card-body
           [:h5.card-title "Pending Orders"]
           [:p.card-text.display-4 (:count (:pending-orders stats))]]]]]
       
       ;; Recent orders table
       [:div.mt-4
        [:h3 "Recent Orders"]
        [:table.table
         [:thead
          [:tr
           [:th "Order ID"]
           [:th "Date"]
           [:th "Total"]
           [:th "Status"]]]
         [:tbody
          (for [order recent-orders]
            [:tr
             [:td (:id order)]
             [:td (:created_at order)]
             [:td "$" (:total order)]
             [:td (:status order)]])]]]])))
```

---

### **Setup:** Direct Database Access

For complex queries beyond CRUD, use direct database access:

```clojure
(ns myapp.handlers.reports.model
  (:require
   [myapp.models.db :as db]))

;; Simple query
(defn get-top-customers [limit]
  (db/query 
    "SELECT c.*, SUM(o.total) as total_spent
     FROM customers c
     JOIN orders o ON c.id = o.customer_id
     GROUP BY c.id
     ORDER BY total_spent DESC
     LIMIT ?" 
    [limit]))

;; Complex aggregation
(defn get-sales-by-month [year]
  (db/query
    "SELECT 
       DATE_TRUNC('month', created_at) as month,
       COUNT(*) as order_count,
       SUM(total) as revenue,
       AVG(total) as avg_order_value
     FROM orders
     WHERE EXTRACT(YEAR FROM created_at) = ?
     GROUP BY month
     ORDER BY month"
    [year]))

;; With parameters
(defn search-products [search-term category]
  (db/query
    "SELECT * FROM products 
     WHERE (name ILIKE ? OR description ILIKE ?)
     AND category = ?
     ORDER BY name"
    [(str "%" search-term "%") (str "%" search-term "%") category]))
```

---

### **Configuration** Middleware Customization

Add custom middleware in `src/myapp/core.clj`:

```clojure
(ns myapp.core
  (:require
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.session :refer [wrap-session]]))

;; Custom middleware
(defn wrap-request-logging [handler]
  (fn [request]
    (println "Request:" (:request-method request) (:uri request))
    (let [response (handler request)]
      (println "Response:" (:status response))
      response)))

(defn wrap-api-authentication [handler]
  (fn [request]
    (let [api-key (get-in request [:headers "x-api-key"])]
      (if (valid-api-key? api-key)
        (handler request)
        {:status 401
         :headers {"Content-Type" "application/json"}
         :body "{\"error\": \"Invalid API key\"}"}))))

;; Apply middleware stack
(defn app []
  (-> (routes open-routes proutes)
      wrap-request-logging
      wrap-api-authentication
      wrap-session
      (wrap-defaults site-defaults)))
```

---

### Custom Layout Components

Override or extend the default layout in `src/myapp/layout.clj`:

```clojure
(ns myapp.layout
  (:require
   [hiccup.page :refer [html5]]))

(defn custom-header [title user]
  [:header.navbar.navbar-expand-lg.navbar-dark.bg-primary
   [:div.container-fluid
    [:a.navbar-brand {:href "/"} title]
    [:div.ms-auto
     [:span.text-white "Welcome, " (:username user)]
     [:a.btn.btn-sm.btn-outline-light.ms-2 {:href "/home/logoff"} "Logout"]]]])

(defn custom-footer []
  [:footer.bg-dark.text-white.py-3.mt-5
   [:div.container
    [:div.row
     [:div.col-md-6
      [:p "© 2025 Your Company"]]
     [:div.col-md-6.text-end
      [:a.text-white {:href "/about"} "About"]
      " | "
      [:a.text-white {:href "/contact"} "Contact"]]]]])

(defn application [title content & [user]]
  (html5
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title title]
     [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}]
     [:link {:rel "stylesheet" :href "/css/custom.css"}]]
    [:body
     (custom-header title user)
     [:main.container.my-4 content]
     (custom-footer)
     [:script {:src "/js/bootstrap.bundle.min.js"}]
     [:script {:src "/js/custom.js"}]]))
```

---

### API Integration

Create RESTful APIs alongside your CRUD interface:

```clojure
(ns myapp.handlers.api.controller
  (:require
   [cheshire.core :as json]
   [myapp.models.db :as db]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn get-customer [id]
  (if-let [customer (db/query-one "SELECT * FROM customers WHERE id = ?" [id])]
    (json-response customer)
    (json-response {:error "Customer not found"} 404)))

(defn create-order [request]
  (let [order-data (json/parse-string (slurp (:body request)) true)
        result (db/insert! :orders order-data)]
    (json-response result 201)))

(defn update-product [id request]
  (let [product-data (json/parse-string (slurp (:body request)) true)
        result (db/update! :products {:id id} product-data)]
    (if (pos? result)
      (json-response {:success true})
      (json-response {:error "Product not found"} 404))))
```

Add to routes:

```clojure
(defroutes api-routes
  (GET "/api/customers/:id" [id] (api/get-customer id))
  (POST "/api/orders" request (api/create-order request))
  (PUT "/api/products/:id" [id :as request] (api/update-product id request)))
```

---

## **Package:** Publishing to Clojars

The template is already published to Clojars at `org.clojars.hector/webgen`.

For template maintainers to publish updates:

```bash
cd webgen  # Your cloned repository
# Update version in project.clj
lein deploy clojars
```

Users can now install directly:

```bash
lein new org.clojars.hector/webgen myapp  # No git clone needed!
```

## **Note:** License

MIT License - see LICENSE file for details.

## Contributing

Issues and pull requests welcome! This is an active project used in production environments.

## Resources

- [Clojure](https://clojure.org/)
- [Leiningen](https://leiningen.org/)
- [Ring](https://github.com/ring-clojure/ring)
- [Compojure](https://github.com/weavejester/compojure)
- [Hiccup](https://github.com/weavejester/hiccup)
- [Bootstrap 5](https://getbootstrap.com/)
- [DataTables](https://datatables.net/)
