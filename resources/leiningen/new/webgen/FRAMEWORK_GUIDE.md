# RS Framework - Parameter-Driven Enterprise Application Framework

A revolutionary Clojure framework for building enterprise business applications without code generation. Build complete CRUD interfaces, dashboards, and complex business logic through declarative EDN configurations.

---

## **Quick Start:** **What Makes This Different?**

### **Traditional Approach (Code Generation) ❌**
```
Generate Code → Customize → Regenerate → LOSE CHANGES → Spaghetti Code
```

### **RS Framework (Parameter-Driven) ✅**
```
Edit EDN Config → Refresh Browser → Changes Applied → Professional Code
```

**No code generation. No lost modifications. No 81+ repetitive files.**

---

## **Important:** **Philosophy**

Built for **enterprise business solutions** (MRP, Accounting, Inventory, POS), not SPAs. Focuses on:

1. **Parameter-driven by default** (90% of cases)
2. **Escape hatches for complexity** (10% custom needs)
3. **Beginner-friendly** (edit simple EDN files)
4. **Expert-extensible** (hooks, custom functions, plugins)
5. **Professional codebase** (DRY, maintainable, clean)

---

## **Package:** **Quick Start (5 Minutes)**

### **1. Clone and Setup**
```bash
git clone <your-repo-url>
cd {{name}}
cp resources/private/config_example.clj resources/private/config.clj
# Edit config.clj with your database settings
```

### **2. Run Migrations**
```bash
lein migrate      # Create database schema
lein database     # Seed default users
```

### **3. Start Server**
```bash
lein with-profile dev run
# Open http://localhost:3000
```

### **4. Create Your First Entity**

Create `resources/entities/products.edn`:

```clojure
{:entity :products
 :title "Products"
 :table "products"
 :rights ["U" "A" "S"]
 
 :fields [{:id :name :label "Product Name" :type :text :required? true}
          {:id :price :label "Price" :type :decimal :required? true}
          {:id :stock :label "Stock" :type :number}]
 
 :queries {:list "SELECT * FROM products ORDER BY name"
           :get "SELECT * FROM products WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}}
```

**That's it!** Visit `/admin/products` - Full CRUD interface is live.

---

## **Architecture** **Architecture**

```
┌──────────────────────────────────────────────────────┐
│   EDN Configuration Files (resources/entities/)      │
│   - users.edn, products.edn, customers.edn, etc.    │
└──────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────┐
│   Engine Layer (src/{{name}}/engine/)                │
│   ├── config.clj   - Configuration registry         │
│   ├── query.clj    - Query execution                │
│   ├── crud.clj     - CRUD operations                │
│   ├── render.clj   - UI rendering                   │
│   └── router.clj   - Dynamic routing                │
└──────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────┐
│   Generic Routes                                     │
│   /admin/:entity                     (list/grid)     │
│   /admin/:entity/add-form            (create form)   │
│   /admin/:entity/edit-form/:id       (edit form)    │
│   /admin/:entity/save                (save)          │
│   /admin/:entity/delete/:id          (delete)        │
└──────────────────────────────────────────────────────┘
```

---

## **Note:** **Entity Configuration Reference**

### **Complete Example**

```clojure
{:entity :customers                    ; Keyword identifier
 :title "Customer Management"          ; Display title
 :table "customers"                    ; Database table
 :connection :default                  ; DB connection (:default, :pg, :localdb, etc.)
 :rights ["U" "A" "S"]                 ; User levels allowed (User/Admin/System)
 :mode :parameter-driven               ; :parameter-driven or :hybrid
 :audit? true                          ; Enable audit trail
 
 ;; Field definitions
 :fields [{:id :company_name           ; Field ID (matches DB column)
           :label "Company Name"       ; Display label
           :type :text                 ; Field type
           :required? true             ; Required validation
           :placeholder "ABC Corp"     ; Form placeholder
           :validation :validators/check-company}  ; Custom validator
          
          {:id :email
           :label "Email"
           :type :email
           :required? true}
          
          {:id :credit_limit
           :label "Credit Limit"
           :type :decimal
           :compute-fn :customers/calc-limit}  ; Computed field
          
          {:id :status
           :label "Status"
           :type :select
           :options [{:value "active" :label "Active"}
                     {:value "inactive" :label "Inactive"}]}]
 
 ;; Database queries
 :queries {:list "SELECT * FROM customers ORDER BY company_name"
           :get "SELECT * FROM customers WHERE id = ?"
           :active "SELECT * FROM customers WHERE status = 'active'"}
 
 ;; Available actions
 :actions {:new true :edit true :delete true}
 
 ;; Business logic hooks
 :hooks {:before-save :customers/validate-credit
         :after-save :customers/notify-sales
         :before-delete :customers/check-orders
         :after-delete :customers/cleanup}
 
 ;; Custom UI renderers
 :ui {:grid-fn :customers/custom-grid
      :form-fn :customers/custom-form}
 
 ;; Subgrids (parent-child relationships)
 :subgrids [{:entity :orders
             :foreign-key :customer_id
             :title "Customer Orders"
             :href "/admin/orders"
             :icon "bi bi-cart"}]}
```

---

## **Design:** **Field Types**

WebGen supports comprehensive field types for building enterprise forms:

### Text Input Types

| Type | HTML Input | Description | Common Options |
|------|------------|-------------|----------------|
| `:text` | text | Single-line text input | `placeholder`, `required`, `maxlength` |
| `:textarea` | textarea | Multi-line text input | `rows`, `placeholder`, `required` |
| `:email` | email | Email with validation | `placeholder`, `required` |
| `:password` | password | Password (masked) | `placeholder`, `required` |

### Numeric Input Types

| Type | HTML Input | Description | Common Options |
|------|------------|-------------|----------------|
| `:number` | number | Integer input | `min`, `max`, `placeholder` |
| `:decimal` | number (step=0.01) | Decimal/float for currency | `min`, `max`, `step`, `placeholder` |

### Date/Time Input Types

| Type | HTML Input | Description | Common Options |
|------|------------|-------------|----------------|
| `:date` | date | Date picker | `min`, `max` |
| `:datetime` | datetime-local | Date and time picker | `min`, `max` |
| `:time` | time | Time picker | `min`, `max` |

### Selection Input Types

| Type | HTML Input | Description | Common Options |
|------|------------|-------------|----------------|
| `:select` | select dropdown | Single choice from list | `options` (array of `{:value :label}`) |
| `:radio` | radio buttons | Single choice (visible) | `options` (array with `:id`, `:label`, `:value`), `value` |
| `:checkbox` | checkbox | Boolean on/off | `value` (default checked value) |

### Special Input Types

| Type | HTML Input | Description | Common Options |
|------|------------|-------------|----------------|
| `:file` | file upload | File/image upload | Handled via hooks (before-save/after-load) |
| `:hidden` | hidden | Hidden field (not visible) | `value` |
| `:computed` | read-only | Calculated/display only | Computed via hooks |

### Field Type Examples

```clojure
;; Text inputs
{:id :name :label "Name" :type :text :placeholder "Enter name..." :required true}
{:id :notes :label "Notes" :type :textarea :rows 5}
{:id :email :label "Email" :type :email :placeholder "user@example.com"}
{:id :password :label "Password" :type :password}

;; Numeric inputs
{:id :quantity :label "Quantity" :type :number :min 0 :max 1000}
{:id :price :label "Price" :type :decimal :min 0 :step 0.01 :placeholder "0.00"}

;; Date/time inputs
{:id :birthdate :label "Birth Date" :type :date}
{:id :created_at :label "Created" :type :datetime}
{:id :opening_time :label "Opens At" :type :time}

;; Selection inputs
{:id :category :label "Category" :type :select 
 :options [{:value "electronics" :label "Electronics"}
           {:value "clothing" :label "Clothing"}]}

{:id :status :label "Status" :type :radio :value "active"
 :options [{:id "statusActive" :label "Active" :value "active"}
           {:id "statusInactive" :label "Inactive" :value "inactive"}]}

{:id :featured :label "Featured" :type :checkbox :value "T"}

;; Special types
{:id :imagen :label "Image" :type :file}
{:id :property_id :label "Property" :type :fk :fk :property :fk-field [:titulo :estado :contacto]}
{:id :id :label "ID" :type :hidden}
```

---

## **Configuration** **Field Visibility Flags**

Control where fields appear in your application:

| Flag | Effect | Use Case |
|------|--------|----------|
| `:hidden-in-grid? true` | Hide from grid/list view | Sensitive data, long text, fields only needed in forms |
| `:hidden-in-form? true` | Hide from entry/edit forms | Calculated values, read-only display data shown in grids |
| `:grid-only? true` | Show in grid only | Display-only values (e.g., computed totals, joined names) |
| `:type :hidden` | Completely hidden | ID fields, internal references |

### **Examples**

```clojure
;; Show in grid but not in forms (read-only calculated field)
{:id :total_amount 
 :label "Total" 
 :type :decimal
 :hidden-in-form? true}  ; ← Shown in grid, hidden in forms

;; Show in forms but not in grid (sensitive or verbose data)
{:id :comments 
 :label "Internal Notes" 
 :type :textarea
 :hidden-in-grid? true}  ; ← Hidden from grid, shown in forms

;; Show only in grid (display-only joined data)
{:id :customer_name 
 :label "Customer" 
 :type :text
 :grid-only? true}  ; ← Grid only, not in forms

;; Completely hidden (ID fields)
{:id :id 
 :label "ID" 
 :type :hidden}  ; ← Never shown to user
```

### **Common Patterns**

**Pattern 1: Foreign Key + Display Name**
```clojure
;; Hide the FK ID in grid, show the name instead
{:id :customer_id :type :select :hidden-in-grid? true}
{:id :customer_name :type :text :grid-only? true}
```

**Pattern 2: Calculated Read-Only Values**
```clojure
;; Calculated in hooks, shown in grid only
{:id :days_overdue :type :number :hidden-in-form? true}
{:id :total_with_tax :type :decimal :hidden-in-form? true}
```

**Pattern 3: Verbose Fields**
```clojure
;; Keep grids clean, show details in forms
{:id :description :type :textarea :hidden-in-grid? true}
{:id :internal_notes :type :textarea :hidden-in-grid? true}
```

---

## 🔐 **Access Control**

### **User Levels**
- `"U"` - User (basic access)
- `"A"` - Administrator
- `"S"` - System (full access)

### **Entity-Level Permissions**
```clojure
{:entity :payroll
 :rights ["A" "S"]  ; Only admins and system
 ...}
```

### **Row-Level Security** (Advanced)
```clojure
{:entity :documents
 :hooks {:before-load :security/filter-by-department}}
```

---

## 🪝 **Hooks & Business Logic**

Hooks allow custom business logic without modifying core code.

### **Available Hooks**

```clojure
:hooks {
  :before-load fn     ; Modify query params before loading
  :after-load fn      ; Transform data after loading
  :before-save fn     ; Validate/transform before saving
  :after-save fn      ; Side effects after save (email, etc.)
  :before-delete fn   ; Check constraints before delete
  :after-delete fn    ; Cleanup after delete
}
```

### **Example: Password Hashing**

Create `src/{{name}}/hooks/users.clj`:

```clojure
(ns {{name}}.hooks.users
  (:require [buddy.hashers :as hashers]))

(defn hash-password
  "Hashes password before saving"
  [data]
  (if-let [password (:password data)]
    (if (empty? password)
      (dissoc data :password)  ; Don't update if blank
      (assoc data :password (hashers/derive password)))
    data))

(defn send-welcome-email
  "Sends welcome email after user creation"
  [data result]
  (when-not (:id data)  ; New user (no ID yet)
    (send-email (:email data) "Welcome!"
                "Your account has been created."))
  data)
```

Reference in config:

```clojure
{:entity :users
 :hooks {:before-save :{{name}}.hooks.users/hash-password
         :after-save :{{name}}.hooks.users/send-welcome-email}}
```

---

## **Search:** **Custom Queries**

### **Simple SQL**
```clojure
:queries {:active "SELECT * FROM products WHERE active = 'T'"}
```

### **Function-Based Queries**
```clojure
;; In src/{{name}}/queries/products.clj
(ns {{name}}.queries.products
  (:require [{{name}}.models.crud :as crud]))

(defn low-stock [params conn]
  (let [threshold (or (:threshold params) 10)]
    (crud/Query ["SELECT * FROM products WHERE stock < ?" threshold]
                :conn conn)))

;; In config
:queries {:low-stock :{{name}}.queries.products/low-stock}
```

Execute:
```clojure
(require '[{{name}}.engine.query :as query])
(query/custom-query :products :low-stock {:threshold 5})
```

---

## **Important:** **Complex Scenarios**

### **MRP (Material Requirements Planning)**

```clojure
{:entity :production-orders
 :title "Production Orders"
 :table "production_orders"
 
 :fields [...]
 
 :hooks {:before-save :mrp/validate-materials
         :after-save :mrp/update-inventory
         :before-delete :mrp/check-dependencies}
 
 :ui {:grid-fn :mrp/gantt-chart}  ; Custom Gantt chart renderer
 
 :actions {:new true :edit true :delete true
           :custom {:calculate {:label "Calculate MRP"
                                :fn :mrp/calculate-requirements
                                :async? true}}}}
```

### **Accounting Double-Entry**

```clojure
{:entity :journal-entries
 :hooks {:before-save :accounting/validate-balanced
         :after-save :accounting/post-to-ledger}
 
 :subgrids [{:entity :journal-lines
             :foreign-key :journal_id}]}
```

### **Point of Sale**

```clojure
{:entity :sales
 :hooks {:before-save :pos/calculate-totals
         :after-save [:pos/print-receipt
                      :pos/update-inventory
                      :pos/record-payment]}}
```

---

## 📊 **Dashboards & Reports**

### **Dashboard (Read-Only Grid)**

```clojure
{:entity :sales-dashboard
 :title "Sales Overview"
 :table "sales_view"
 :rights ["U" "A" "S"]
 
 :fields [{:id :date :label "Date" :type :date}
          {:id :total :label "Total Sales" :type :decimal}
          {:id :items :label "Items Sold" :type :number}]
 
 :queries {:list "SELECT * FROM daily_sales_summary ORDER BY date DESC"}
 
 :actions {:new false :edit false :delete false}}  ; Read-only
```

Access at: `/dashboard/sales-dashboard`

---

## 🔗 **Subgrids (Parent-Child) - TabGrid System**

When an entity has subgrids defined, the system automatically uses the **TabGrid interface** instead of a simple grid.

### **Configuration**

```clojure
{:entity :propiedades
 :title "Propiedades"
 :subgrids [{:entity :alquileres
             :foreign-key :id_propiedad
             :title "Alquileres"
             :icon "bi bi-file-text"}
            {:entity :avaluos
             :foreign-key :id_propiedad
             :title "Avalúos"
             :icon "bi bi-graph-up"}
            {:entity :documentos
             :foreign-key :id_propiedad
             :title "Documentos"
             :icon "bi bi-paperclip"}]}
```

### **How TabGrid Works**

1. **Single Parent View**: Displays ONE parent record at a time (detail view)
2. **Multiple Subgrid Tabs**: Each subgrid appears as a tab
3. **Parent Selector**: "Seleccionar" button opens modal to switch parents
4. **AJAX Loading**: Subgrid data loads dynamically when clicking tabs
5. **Full CRUD**: Each subgrid tab has New/Edit/Delete actions

### **User Experience**

```
┌─────────────────────────────────────────┐
│  Propiedad #6                           │
│  [Seleccionar] [Editar] [Eliminar]     │
├─────────────────────────────────────────┤
│  Título: Casa en Venta                  │
│  Precio: $2,500,000                     │
│  Dirección: Av. Principal 123          │
└─────────────────────────────────────────┘

[Propiedad] [Alquileres] [Avalúos] [Documentos] [Ventas]

┌─────────────────────────────────────────┐
│  Alquileres for Propiedad #6     [Nuevo]│
├─────────────────────────────────────────┤
│  ID  Inquilino    Monto    Fecha        │
│  1   Juan Pérez   $5,000   2025-01-01  │
│  2   Ana Gómez    $4,500   2025-02-01  │
└─────────────────────────────────────────┘
```

### **Automatic Scaffolding**

When scaffolding entities with foreign keys, subgrids are **automatically detected**:

```bash
lein scaffold avaluos
# Detects id_propiedad foreign key
# Automatically adds to propiedades.edn subgrids
```

---

## **Database** **Multi-Database Support**

### **Configuration**

**Location:** `resources/private/config.clj`

```clojure
{:port 8080                    ; Server port (configurable)
 
 :connections
 {:sqlite {:db-type "sqlite"
           :db-class "org.sqlite.JDBC"
           :db-name "db/{{name}}.sqlite"}
   
  :mysql {:db-type "mysql"
          :db-host "localhost"
          :db-port 3306
          :db-name "mydb"
          :db-user "root"
          :db-pwd "password"}
   
  :postgres {:db-type "postgresql"
             :db-host "localhost"
             :db-port 5432
             :db-name "pgdb"
             :db-user "postgres"
             :db-pwd "password"}
   
  :default :sqlite              ; Default connection for entities
  :main :sqlite                 ; Connection for migrations
  :localdb :sqlite}             ; Alias for local development
 
 :theme "sketchy"               ; Bootstrap theme
 :uploads "./uploads/{{name}}/" ; Upload directory
 :max-upload-mb 5}              ; Max file size (MB)
```

**Note:** Change `:default` and `:main` to switch databases (e.g., `:mysql`, `:postgres`)

### **Per-Entity Connection**

Override the default connection for specific entities:

```clojure
{:entity :analytics
 :connection :postgres  ; Use PostgreSQL for this entity
 ...}

{:entity :cache
 :connection :sqlite    ; Use SQLite for this entity
 ...}
```

---

## **Tools:** **Development Workflow**

### **1. Create Database Schema**

`resources/migrations/001-products.sqlite.up.sql`:

```sql
CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  price REAL DEFAULT 0.0,
  stock INTEGER DEFAULT 0,
  active TEXT DEFAULT 'T'
);
```

Run: `lein migrate`

### **2. Create Entity Config**

`resources/entities/products.edn`:

```clojure
{:entity :products
 :title "Products"
 :table "products"
 :fields [...]}
```

### **3. Refresh Browser**

Visit `/admin/products` - Done!

### **4. Customize (if needed)**

Add hooks, custom queries, validators - all in EDN or separate namespace.

---

## 🧪 **REPL Development**

```clojure
;; Load entity config
(require '[{{name}}.engine.config :as config])
(config/load-entity-config :users)

;; List all entities
(config/list-available-entities)

;; Query data
(require '[{{name}}.engine.query :as query])
(query/list-records :users)
(query/get-record :users 1)

;; CRUD operations
(require '[{{name}}.engine.crud :as crud])
(crud/save-record :users {:lastname "Doe" :firstname "John"})
(crud/delete-record :users 5)

;; Reload config (hot-reload)
(config/reload-entity-config! :users)
(config/reload-all!)
```

---

## 🎓 **Tutorial: Building an Inventory System**

### **Step 1: Database Schema**

```sql
-- migrations/001-inventory.sqlite.up.sql
CREATE TABLE products (
  id INTEGER PRIMARY KEY,
  code TEXT UNIQUE,
  name TEXT,
  stock INTEGER DEFAULT 0
);

CREATE TABLE stock_movements (
  id INTEGER PRIMARY KEY,
  product_id INTEGER,
  quantity INTEGER,
  type TEXT,
  date TEXT,
  FOREIGN KEY (product_id) REFERENCES products(id)
);
```

Run: `lein migrate`

### **Step 2: Products Entity**

`entities/products.edn`:

```clojure
{:entity :products
 :title "Products"
 :table "products"
 :fields [{:id :code :label "Code" :type :text :required? true}
          {:id :name :label "Name" :type :text :required? true}
          {:id :stock :label "Stock" :type :number :required? true}]
 :queries {:list "SELECT * FROM products ORDER BY name"
           :get "SELECT * FROM products WHERE id = ?"}
 :actions {:new true :edit true :delete true}
 :subgrids [{:entity :stock-movements :foreign-key :product_id}]}
```

### **Step 3: Stock Movements Entity**

`entities/stock-movements.edn`:

```clojure
{:entity :stock-movements
 :title "Stock Movements"
 :table "stock_movements"
 :fields [{:id :product_id :label "Product" :type :hidden}
          {:id :quantity :label "Quantity" :type :number :required? true}
          {:id :type :label "Type" :type :select
           :options [{:value "in" :label "Stock In"}
                     {:value "out" :label "Stock Out"}]}
          {:id :date :label "Date" :type :date :required? true}]
 :queries {:list "SELECT * FROM stock_movements WHERE product_id = ? ORDER BY date DESC"
           :get "SELECT * FROM stock_movements WHERE id = ?"}
 :hooks {:after-save :inventory/update-stock}
 :actions {:new true :edit true :delete true}}
```

### **Step 4: Business Logic**

`src/{{name}}/hooks/inventory.clj`:

```clojure
(ns {{name}}.hooks.inventory
  (:require [{{name}}.models.crud :as crud]))

(defn update-stock
  "Updates product stock after movement"
  [data result]
  (let [product-id (:product_id data)
        quantity (:quantity data)
        type (:type data)
        adjustment (if (= type "in") quantity (- quantity))
        sql "UPDATE products SET stock = stock + ? WHERE id = ?"]
    (crud/Query [sql adjustment product-id] :conn :default))
  data)
```

### **Step 5: Test**

1. Visit `/admin/products`
2. Add product "Widget" with code "WID-001"
3. Click "Stock Movements" button
4. Add movement: +100 units
5. Check product - stock updated to 100!

**Complete inventory system in 5 minutes.**

---

## **Quick Start:** **Migrating from Code Generation**

### **Before (Generated)**

```
src/{{name}}/handlers/admin/users/
├── controller.clj    (50 lines)
├── model.clj        (45 lines)
└── view.clj         (130 lines)
```

### **After (Parameter-Driven)**

```
resources/entities/users.edn    (80 lines)
```

**Savings: 145 lines → 80 lines (45% reduction)**

Multiply by 27 entities = **1,755 lines saved!**

---

## **Documentation:** **API Reference**

### **Configuration (`{{name}}.engine.config`)**

```clojure
(load-entity-config :users)           ; Load config
(reload-entity-config! :users)        ; Reload from disk
(list-available-entities)             ; List all entities
(has-permission? :users "S")          ; Check access
(get-display-fields :users)           ; Get grid fields
```

### **Queries (`{{name}}.engine.query`)**

```clojure
(list-records :users)                 ; List all
(get-record :users 1)                 ; Get by ID
(custom-query :users :active-users)   ; Named query
(list-with-hooks :users)              ; With hooks
```

### **CRUD (`{{name}}.engine.crud`)**

```clojure
(save-record :users {...})            ; Create/update
(delete-record :users 1)              ; Delete
(save-with-audit :users {...} user-id); With audit trail
(soft-delete :users 1)                ; Soft delete
```

---

## 🐛 **Troubleshooting**

### **Entity Not Found**

```
Error: Entity configuration not found :products
```

**Fix**: Ensure `resources/entities/products.edn` exists and is valid EDN.

### **Permission Denied**

```
Not authorized to access Users! Required level(s): S
```

**Fix**: Update `:rights` in entity config or change user level.

### **Query Failed**

```
ERROR: relation "products" does not exist
```

**Fix**: Run migrations: `lein migrate`

### **Hot-Reload Not Working**

```clojure
;; Force reload
(require '[{{name}}.engine.config :as config])
(config/reload-all!)
```

---

## **Important:** **Best Practices**

### **1. Start Simple**

```clojure
{:entity :products
 :title "Products"
 :table "products"
 :fields [{:id :name :label "Name" :type :text}]
 :queries {:list "SELECT * FROM products"
           :get "SELECT * FROM products WHERE id = ?"}
 :actions {:new true :edit true :delete true}}
```

Add complexity only when needed.

### **2. Use Migrations as Source of Truth**

Define schema in migrations, reference in entities:

```clojure
:table "products"  ; References migration-created table
```

### **3. Separate Business Logic**

```
src/{{name}}/hooks/          - Business logic hooks
src/{{name}}/queries/        - Complex queries
src/{{name}}/validators/     - Custom validators
src/{{name}}/views/          - Custom UI renderers
```

### **4. Leverage Subgrids**

```clojure
:subgrids [{:entity :child-table :foreign-key :parent_id}]
```

Better than creating separate grids.

### **5. Use Computed Fields**

```clojure
{:id :total
 :label "Total"
 :type :computed
 :compute-fn :orders/calculate-total}
```

---

## 📈 **Performance**

### **Caching**

Configs are cached in memory. Reload only on changes:

```clojure
(config/reload-entity-config! :products)
```

### **Query Optimization**

Use database views for complex queries:

```sql
CREATE VIEW users_view AS
SELECT id, lastname, firstname,
       CASE level WHEN 'S' THEN 'System' ELSE 'User' END AS level_formatted
FROM users;
```

```clojure
:queries {:list "SELECT * FROM users_view"}
```

---

## 🎁 **Bonus Features**

### **Audit Trail**

```clojure
{:entity :sensitive-data
 :audit? true}  ; Auto-logs all changes
```

### **Soft Delete**

```clojure
(crud/soft-delete :users 1)  ; Sets deleted_at instead of removing
```

### **Batch Operations**

```clojure
(crud/save-batch :products [{:name "A"} {:name "B"}])
(crud/delete-batch :products [1 2 3])
```

---

## 🏆 **Success Stories**

### **Before RS Framework**

- 81 generated files
- Lost modifications on regeneration
- 4 hours to add new entity
- Beginner-unfriendly

### **After RS Framework**

- 27 EDN config files
- Never lose modifications
- 10 minutes to add new entity
- Non-programmers can create entities

---

## 📞 **Support & Contributing**

- Issues: [GitHub Issues](#)
- Discussions: [GitHub Discussions](#)
- PRs welcome!

---

## **File:** **License**

MIT License - Build anything, commercially or personally.

---

**Built with care for enterprise developers who value clean, maintainable code.**
