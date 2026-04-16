# WebGen

WebGen is a Leiningen template that generates parameter-driven Clojure web applications. Instead of generating code that you then modify, WebGen applications are driven entirely by EDN configuration files. Add an entity, refresh the browser, and the CRUD interface, menu entry, routes, and data grid are all live with no server restart.

The framework targets enterprise data applications: inventory, accounting, MRP, POS, CRM, and similar systems that revolve around structured data and business rules.

---

## Table of Contents

1. [Installation and Quick Start](#1-installation-and-quick-start)
2. [Project Structure](#2-project-structure)
3. [Configuration](#3-configuration)
4. [Entity Configuration](#4-entity-configuration)
5. [Field Types and Options](#5-field-types-and-options)
6. [Hook System](#6-hook-system)
7. [Validators](#7-validators)
8. [Computed Fields](#8-computed-fields)
9. [Subgrids and TabGrid](#9-subgrids-and-tabgrid)
10. [Custom Queries](#10-custom-queries)
11. [Actions and Access Control](#11-actions-and-access-control)
12. [Audit Trail](#12-audit-trail)
13. [Multi-Database Support](#13-multi-database-support)
14. [Migrations](#14-migrations)
15. [Scaffolding](#15-scaffolding)
16. [Custom Routes and Handlers](#16-custom-routes-and-handlers)
17. [Menu Customization](#17-menu-customization)
18. [Internationalization](#18-internationalization)
19. [Authentication and Security](#19-authentication-and-security)
20. [Themes](#20-themes)
21. [Deployment](#21-deployment)
22. [Publishing the Template](#22-publishing-the-template)
23. [Custom Dashboards and Reports](#23-custom-dashboards-and-reports)

---

## 1. Installation and Quick Start

### Create a New Project

```bash
lein new org.clojars.hector/webgen myapp
cd myapp
```

### Set Up the Database

Edit `resources/config/app-config.edn` with your database credentials. The default configuration uses SQLite and requires no changes for local development.

```bash
lein migrate      # Create database schema
lein database     # Seed default users
```

### Start the Server

```bash
lein with-profile dev run
```

Visit `http://localhost:3000` and log in with `admin@example.com` / `admin`.

---

## 2. Project Structure

```
myapp/
├── resources/
│   ├── entities/               Entity EDN configuration files
│   ├── migrations/             Database migration SQL files
│   ├── config/
│   │   └── app-config.edn      Application and database configuration
│   ├── i18n/
│   │   ├── en.edn              English translations
│   │   └── es.edn              Spanish translations
│   └── public/                 Static assets (CSS, JS, images)
│
├── src/myapp/
│   ├── core.clj                Application entry point and middleware
│   ├── layout.clj              Page layout template
│   ├── menu.clj                Menu customization
│   ├── engine/                 Framework core (do not modify)
│   ├── hooks/                  Business logic hooks (one file per entity)
│   ├── routes/
│   │   ├── routes.clj          Public (unauthenticated) routes
│   │   └── proutes.clj         Protected (authenticated) routes
│   └── handlers/               Custom MVC handlers
│
└── project.clj
```

The `engine/` directory is the framework itself. All customization happens in `entities/`, `hooks/`, `routes/`, and `handlers/`.

---

## 3. Configuration

Edit `resources/config/app-config.edn`:

```clojure
{:connections
 {:sqlite   {:db-type  "sqlite"
             :db-class "org.sqlite.JDBC"
             :db-name  "db/myapp.sqlite"}

  :mysql    {:db-type  "mysql"
             :db-class "com.mysql.cj.jdbc.Driver"
             :db-name  "//localhost:3306/myapp"
             :db-user  "root"
             :db-pwd   "password"}

  :postgres {:db-type  "postgresql"
             :db-class "org.postgresql.Driver"
             :db-name  "//localhost:5432/myapp"
             :db-user  "postgres"
             :db-pwd   "password"}

  :default :sqlite   ; Connection used by entities
  :main    :sqlite}  ; Connection used by migrations

 :port            8080
 :site-name       "My Application"
 :company-name    "Acme Corp"
 :base-url        "http://0.0.0.0:8080/"
 :uploads         "./uploads/myapp/"
 :max-upload-mb   5
 :allowed-image-exts ["jpg" "jpeg" "png" "gif" "webp"]
 :theme           "sketchy"
 :tz              "US/Pacific"

 ;; Optional email
 :email-host      "smtp.example.com"
 :email-user      "user@example.com"
 :email-pwd       "password"}
```

To switch databases, change `:default` and `:main` to `:mysql` or `:postgres`. No other changes are required.

---

## 4. Entity Configuration

Every entity is a single EDN file in `resources/entities/`. The file name determines the entity key.

### Complete Entity Options

```clojure
{;; ── Core ──────────────────────────────────────────────────────────────
 :entity     :products          ; Unique keyword identifier
 :title      "Products"         ; Display title in UI and page headers
 :table      "products"         ; Database table name
 :connection :default           ; DB connection key from app-config.edn

 ;; ── Access Control ────────────────────────────────────────────────────
 :rights     ["U" "A" "S"]      ; Who can access: User / Admin / System
 :audit?     true               ; Enable audit trail (created_by, modified_by, etc.)
 :mode       :parameter-driven  ; Always :parameter-driven

 ;; ── Menu ──────────────────────────────────────────────────────────────
 :menu-category :catalog        ; Menu group (see categories below)
 :menu-order    10              ; Sort position within the group (lower = first)
 :menu-hidden?  false           ; true hides the entity from the menu (use for subgrids)
 :menu-icon     "bi bi-box"     ; Bootstrap Icons class

 ;; ── Fields ────────────────────────────────────────────────────────────
 :fields [...]                  ; Field definitions (see Section 5)

 ;; ── Queries ───────────────────────────────────────────────────────────
 :queries {:list "SELECT * FROM products ORDER BY name"
           :get  "SELECT * FROM products WHERE id = ?"}

 ;; ── Actions ───────────────────────────────────────────────────────────
 :actions {:new    true
           :edit   true
           :delete true}

 ;; ── Hooks ─────────────────────────────────────────────────────────────
 :hooks {:before-load   :myapp.hooks.products/before-load
         :after-load    :myapp.hooks.products/after-load
         :before-save   :myapp.hooks.products/before-save
         :after-save    :myapp.hooks.products/after-save
         :before-delete :myapp.hooks.products/before-delete
         :after-delete  :myapp.hooks.products/after-delete}

 ;; ── Subgrids ──────────────────────────────────────────────────────────
 :subgrids [{:entity      :reviews
             :foreign-key :product_id
             :title       "Reviews"
             :icon        "bi bi-chat"}]

 ;; ── Custom UI Renderers (advanced) ────────────────────────────────────
 :ui {:grid-fn :myapp.views.products/custom-grid
      :form-fn :myapp.views.products/custom-form}}
```

### Menu Categories

| Value | Menu Label | Typical Entities |
|---|---|---|
| `:clients` | Clients | Customers, contacts, agents |
| `:properties` | Properties | Real estate, buildings, units |
| `:financial` | Financial | Payments, invoices, commissions |
| `:transactions` | Transactions | Orders, sales, rentals |
| `:documents` | Documents | Contracts, attachments |
| `:system` | System | Users, roles, settings |
| `:admin` | Administration | Audit logs, backups |
| `:reports` | Reports | Dashboards, analytics |

### Minimal Entity

```clojure
{:entity     :products
 :title      "Products"
 :table      "products"
 :connection :default
 :rights     ["U" "A" "S"]
 :menu-category :catalog
 :fields [{:id :id   :type :hidden}
          {:id :name :label "Name" :type :text :required? true}]
 :queries {:list "SELECT * FROM products ORDER BY name"
           :get  "SELECT * FROM products WHERE id = ?"}
 :actions {:new true :edit true :delete true}}
```

---

## 5. Field Types and Options

### All Field Options

Every field map supports any combination of these keys:

```clojure
{:id              :field_name      ; Required. Database column name (keyword)
 :label           "Display Label"  ; Required. UI label shown in forms and grid headers
 :type            :text            ; Required. Field type (see types below)

 ;; Validation
 :required?       true             ; Mark field as mandatory
 :validation      :myapp.validators/check-value  ; Custom validator function (namespace-qualified)

 ;; Input behavior
 :placeholder     "hint text"      ; Placeholder shown inside the input
 :value           "default"        ; Default value when creating a new record
 :maxlength       100              ; Maximum character length (text inputs)
 :min             0                ; Minimum value (numbers) or date (date inputs)
 :max             100              ; Maximum value (numbers) or date (date inputs)
 :step            0.01             ; Increment step (decimal inputs)
 :rows            5                ; Number of visible rows (textarea only)

 ;; Select / radio options
 :options         [{:value "a" :label "Option A"}]  ; Static list or function reference

 ;; Computed fields
 :compute-fn      :myapp.hooks.products/compute-total  ; Function that computes the field value

 ;; Foreign key fields
 :fk              :categories       ; Target entity keyword
 :fk-field        [:name]           ; Columns from the FK table to display (vector, can be multiple)
 :fk-sort         [:name]           ; Columns to sort the FK dropdown by
 :fk-filter       [:active "T"]     ; Filter FK options: [column value]
 :fk-parent       :estado_id        ; Parent FK field for cascading dependents
 :fk-can-create?  true              ; Allow creating new FK records via modal popup

 ;; Visibility
 :hidden-in-form? false             ; Hide from create/edit forms; still shows in grid
 :hidden-in-grid? false             ; Hide from data grid; still shows in forms
 :grid-only?      false}            ; Show only in the grid, never in forms
```

### Text Inputs

```clojure
;; Single-line text
{:id :name :label "Product Name" :type :text :required? true :maxlength 200 :placeholder "Enter name"}

;; Multi-line text
{:id :description :label "Description" :type :textarea :rows 5 :hidden-in-grid? true}

;; Email
{:id :email :label "Email" :type :email :required? true :placeholder "user@example.com"}

;; Password (value is masked)
{:id :password :label "Password" :type :password :required? true}
```

### Numeric Inputs

```clojure
;; Integer
{:id :quantity :label "Quantity" :type :number :min 0 :max 9999 :value 1}

;; Decimal / currency
{:id :price :label "Price" :type :decimal :min 0 :step 0.01 :placeholder "0.00" :required? true}
```

### Date and Time Inputs

```clojure
;; Date picker
{:id :birth_date  :label "Birth Date"    :type :date     :required? true}

;; Date and time picker
{:id :created_at  :label "Created At"    :type :datetime}

;; Time picker
{:id :opens_at    :label "Opening Time"  :type :time}
```

### Selection Inputs

#### Static Dropdown

```clojure
{:id      :status
 :label   "Status"
 :type    :select
 :value   "active"
 :options [{:value "active"   :label "Active"}
           {:value "inactive" :label "Inactive"}
           {:value "pending"  :label "Pending"}]}
```

#### Dropdown from Database

Create a lookup function in your model namespace:

```clojure
(ns myapp.models.lookups
  (:require [myapp.models.crud :as crud]))

(defn get-categories []
  (crud/Query "SELECT id AS value, name AS label FROM categories ORDER BY name"
              :conn :default))
```

Reference it in the entity field:

```clojure
{:id      :category_id
 :label   "Category"
 :type    :select
 :options :myapp.models.lookups/get-categories}
```

#### Foreign Key (FK) Field

An FK field renders as a searchable dropdown populated from another entity's table. No foreign key constraint is required in the database.

```clojure
{:id        :category_id
 :label     "Category"
 :type      :fk
 :fk        :categories          ; Target entity keyword
 :fk-field  [:name]              ; Which columns to display in the dropdown
 :fk-sort   [:name]              ; Sort order in the dropdown
 :fk-filter [:active "T"]        ; Only show rows where active = 'T'
 :required? true
 :hidden-in-grid? true}          ; Hide the raw ID column in the grid

;; Add a display-only column to show the joined value in the grid
{:id :category_name :label "Category" :grid-only? true}
```

Multiple FK display columns (all joined into the dropdown label):

```clojure
{:id       :agent_id
 :label    "Agent"
 :type     :fk
 :fk       :agents
 :fk-field [:first_name :last_name :phone]  ; Shows "John Doe 555-1234"
 :required? true
 :hidden-in-grid? true}
{:id :agent_name :label "Agent" :grid-only? true}
```

#### Cascading Dependent FK Fields

Use `:fk-parent` to make one FK filter based on the value selected in another.

```clojure
;; Level 1 – State
{:id        :state_id
 :label     "State"
 :type      :fk
 :fk        :states
 :fk-field  [:name]
 :fk-can-create? true
 :required?      true
 :hidden-in-grid? true}
{:id :state_name :label "State" :grid-only? true}

;; Level 2 – Municipality (filtered by selected state)
{:id        :municipality_id
 :label     "Municipality"
 :type      :fk
 :fk        :municipalities
 :fk-field  [:name]
 :fk-parent :state_id           ; Dropdown reloads when state_id changes
 :fk-can-create? true
 :hidden-in-grid? true}
{:id :municipality_name :label "Municipality" :grid-only? true}

;; Level 3 – Neighborhood (filtered by selected municipality)
{:id        :neighborhood_id
 :label     "Neighborhood"
 :type      :fk
 :fk        :neighborhoods
 :fk-field  [:name :zip_code]
 :fk-parent :municipality_id
 :fk-can-create? true
 :hidden-in-grid? true}
{:id :neighborhood_name :label "Neighborhood" :grid-only? true}
```

#### Radio Buttons

```clojure
{:id      :gender
 :label   "Gender"
 :type    :radio
 :value   "M"
 :options [{:id "gM" :label "Male"   :value "M"}
           {:id "gF" :label "Female" :value "F"}
           {:id "gO" :label "Other"  :value "O"}]}

;; Boolean pattern (T/F stored in database)
{:id      :active
 :label   "Active"
 :type    :radio
 :value   "T"
 :options [{:id "actT" :label "Yes" :value "T"}
           {:id "actF" :label "No"  :value "F"}]}
```

#### Checkbox

```clojure
{:id    :newsletter
 :label "Subscribe to Newsletter"
 :type  :checkbox
 :value "T"}
```

`:value` is the value submitted when the checkbox is **checked**. When it is **unchecked**, an empty string `""` is submitted instead (a hidden fallback input ensures the key is always present in the request params). If your database column expects `1`/`0` or `"T"`/`"F"`, coerce the value in a `before-save` hook:

```clojure
(defn before-save [params]
  (update params :newsletter #(if (= % "T") "T" "F")))
```

### File Upload

```clojure
{:id :image :label "Product Image" :type :file}
```

File uploads are handled through hooks. See the `before-save` and `after-load` hook examples in Section 6.

### Hidden Field

```clojure
{:id :id          :label "ID"          :type :hidden}
{:id :property_id :label "Property ID" :type :hidden}  ; FK value for subgrid child
```

### Computed Field

A computed field displays a value calculated at runtime. It is read-only and never submitted to the database.

```clojure
;; Method 1: via :compute-fn — called per row
{:id         :total
 :label      "Total"
 :type       :decimal
 :compute-fn :myapp.hooks.products/calculate-total
 :hidden-in-form? true}

;; Method 2: computed inside after-load hook — see Section 8
{:id    :days_overdue
 :label "Days Overdue"
 :type  :number
 :hidden-in-form? true}
```

### Visibility Summary

| Option | Grid | Form |
|---|---|---|
| Default | Shown | Shown |
| `:hidden-in-grid? true` | Hidden | Shown |
| `:hidden-in-form? true` | Shown | Hidden |
| `:grid-only? true` | Shown | Never |
| `:type :hidden` | Never | Never |

---

## 6. Hook System

Hooks are functions called at specific points in the CRUD lifecycle. Register them in the entity configuration under `:hooks`. All hooks are optional.

### Execution Flow

```
LIST / GET
  before-load → [SQL query] → after-load → render

CREATE / UPDATE
  before-save → [SQL insert/update] → after-save → redirect

DELETE
  before-delete → [SQL delete] → after-delete → redirect
```

### Hook Signatures

| Hook | Parameters | Return on success | Return on error |
|---|---|---|---|
| `before-load` | `[params]` | Modified params map | N/A — always returns params |
| `after-load` | `[rows params]` | Modified rows vector | N/A — always returns rows |
| `before-save` | `[params]` | Modified params map | `{:errors {:field "message"}}` |
| `after-save` | `[entity-id params]` | `{:success true}` | `{:error "message"}` |
| `before-delete` | `[entity-id]` | `{:success true}` | `{:errors {:general "message"}}` |
| `after-delete` | `[entity-id]` | `{:success true}` | `{:error "message"}` |

### before-load

Called before the database query executes. Use it to add filters, restrict access by user role, or modify query parameters.

```clojure
(ns myapp.hooks.orders)

(defn before-load [params]
  (let [user  (:user params)
        level (:level user)]
    (cond
      (= level "S") params                                          ; System sees everything
      (= level "A") params                                          ; Admin sees everything
      :else (assoc-in params [:filters :salesperson_id] (:id user))))) ; Users see only their own
```

### after-load

Called after the query returns rows. Use it to transform data, format values, add computed columns, or build HTML display values.

```clojure
(defn after-load [rows params]
  (mapv (fn [row]
          (let [qty   (or (:quantity row) 0)
                price (or (:price row) 0.0)
                total (* qty price)]
            (assoc row
                   :total       total
                   :status_badge (cond
                                   (> total 1000) "High Value"
                                   (> total 500)  "Medium"
                                   :else          "Standard"))))
        rows))
```

File upload: convert stored filename to an HTML image tag for display in the grid.

```clojure
(defn after-load [rows params]
  (mapv (fn [row]
          (if (:image row)
            (assoc row :image (str "<img src='/uploads/" (:image row) "' height='60'>"))
            row))
        rows))
```

### before-save

Called before the insert or update. Validate data, set defaults, and transform values. Return `{:errors {...}}` to abort the save and display error messages to the user.

```clojure
(defn before-save [params]
  (let [start (:start_date params)
        end   (:end_date params)
        price (:price params)]
    (cond
      (and start end (neg? (.compareTo end start)))
      {:errors {:end_date "End date must be after start date"}}

      (and price (<= price 0))
      {:errors {:price "Price must be greater than zero"}}

      :else
      (-> params
          (assoc :updated_at (java.time.LocalDateTime/now))
          (assoc :status (or (:status params) "pending"))))))
```

File upload: signal the framework to move the uploaded file by setting `:file` to the field containing the upload.

```clojure
(defn before-save [params]
  (if (contains? params :image)
    (assoc params :file (:image params))
    params))
```

### after-save

Called after a successful save. Use it for notifications, updating related records, or triggering background operations. Return `{:success true}` on completion.

```clojure
(defn after-save [entity-id params]
  (try
    (when (= "processing" (:status params))
      (send-order-confirmation-email (:customer_email params) entity-id))
    (update-inventory! (:product_id params) (- (:quantity params)))
    {:success true}
    (catch Exception e
      {:error (str "Post-save error: " (.getMessage e))})))
```

### before-delete

Called before the delete. Return `{:success true}` to allow the deletion or `{:errors {...}}` to prevent it.

```clojure
(defn before-delete [entity-id]
  (let [order-count (count-related-orders entity-id)]
    (if (pos? order-count)
      {:errors {:general "Cannot delete customer with existing orders"}}
      {:success true})))
```

### after-delete

Called after a successful delete. Use it to clean up related files, cascade deletes, or update denormalized data.

```clojure
(defn after-delete [entity-id]
  (delete-related-addresses! entity-id)
  (delete-uploaded-files! entity-id)
  {:success true})
```

### Hook Registration

```clojure
{:entity :orders
 :hooks  {:before-load   :myapp.hooks.orders/before-load
          :after-load    :myapp.hooks.orders/after-load
          :before-save   :myapp.hooks.orders/before-save
          :after-save    :myapp.hooks.orders/after-save
          :before-delete :myapp.hooks.orders/before-delete
          :after-delete  :myapp.hooks.orders/after-delete}}
```

---

## 7. Validators

### Field-Level Validator

Reference a validator function with `:validation` on the field:

```clojure
{:id        :price
 :label     "Price"
 :type      :decimal
 :required? true
 :validation :myapp.validators/positive-price?}
```

The function receives `[value data]` where `value` is the field value and `data` is the full form map. Return `true` for valid, `false` or a string error message for invalid.

```clojure
(ns myapp.validators)

(defn positive-price? [value _data]
  (and value (> value 0)))

(defn valid-zip-code? [value _data]
  (re-matches #"\d{5}" (str value)))

(defn valid-email? [value _data]
  (re-matches #".+@.+\..+" (str value)))

(defn future-date? [value _data]
  (when value
    (.isAfter value (java.time.LocalDate/now))))
```

Return a custom message:

```clojure
(defn reasonable-price? [value _data]
  (cond
    (nil? value)  "Price is required"
    (<= value 0)  "Price must be greater than zero"
    (> value 1e7) "Price seems unreasonably high"
    :else         true))
```

### Multi-Field Validation

For rules that span multiple fields, validate in `before-save`:

```clojure
(defn before-save [params]
  (let [errors (cond-> {}
                 (and (:end_date params)
                      (:start_date params)
                      (.isBefore (:end_date params) (:start_date params)))
                 (assoc :end_date "End date must be after start date")

                 (and (:deposit params)
                      (< (:deposit params) 0))
                 (assoc :deposit "Deposit cannot be negative"))]
    (if (seq errors)
      {:errors errors}
      params)))
```

---

## 8. Computed Fields

### Method 1: compute-fn (per-row function)

Define a function that receives a row map and returns the computed value:

```clojure
(ns myapp.hooks.order-items)

(defn line-total [row]
  (* (or (:quantity row) 0)
     (or (:unit_price row) 0.0)))

(defn days-since-created [row]
  (when-let [d (:created_date row)]
    (.between java.time.temporal.ChronoUnit/DAYS
              d (java.time.LocalDate/now))))
```

Reference it in the field:

```clojure
{:id         :line_total
 :label      "Line Total"
 :type       :decimal
 :compute-fn :myapp.hooks.order-items/line-total
 :hidden-in-form? true}
```

### Method 2: after-load (batch computation)

Compute multiple fields for all rows in a single pass:

```clojure
(defn after-load [rows params]
  (mapv (fn [row]
          (let [qty      (or (:quantity row) 0)
                price    (or (:unit_price row) 0.0)
                subtotal (* qty price)
                tax      (* subtotal 0.08)]
            (assoc row
                   :subtotal subtotal
                   :tax      tax
                   :total    (+ subtotal tax)
                   :full_name (str (:first_name row) " " (:last_name row)))))
        rows))
```

The corresponding fields in the entity must exist and be configured as display-only:

```clojure
{:id :subtotal  :label "Subtotal" :type :decimal :hidden-in-form? true}
{:id :tax       :label "Tax"      :type :decimal :hidden-in-form? true}
{:id :total     :label "Total"    :type :decimal :hidden-in-form? true}
{:id :full_name :label "Name"     :type :text    :grid-only? true}
```

---

## 9. Subgrids and TabGrid

When an entity defines `:subgrids`, the UI automatically uses the **TabGrid** layout. The parent record is displayed as a header with tabs for each child entity. Each tab provides a full CRUD grid filtered to the selected parent record.

### Subgrid Configuration

```clojure
{:entity  :customers
 :title   "Customers"
 :table   "customers"
 :fields  [...]

 :subgrids [{:entity      :orders
             :foreign-key :customer_id
             :title       "Orders"
             :icon        "bi bi-cart"}

            {:entity      :addresses
             :foreign-key :customer_id
             :title       "Addresses"
             :icon        "bi bi-house"}

            {:entity      :support_tickets
             :foreign-key :customer_id
             :title       "Support"
             :icon        "bi bi-chat"}]}
```

### Subgrid Options

| Option | Description |
|---|---|
| `:entity` | Keyword of the child entity |
| `:foreign-key` | Column in the child table that references the parent |
| `:title` | Tab label |
| `:icon` | Bootstrap Icons class for the tab |

### Child Entity Configuration

The child entity's `:queries` must accept the parent ID as a parameter. The framework automatically passes it.

```clojure
;; resources/entities/orders.edn
{:entity   :orders
 :table    "orders"
 :menu-hidden? true            ; Hide from main menu; appears only as a subgrid tab

 :fields [{:id :id          :type :hidden}
          {:id :customer_id :type :hidden}   ; The FK column — always hidden
          {:id :order_date  :label "Date"   :type :date}
          {:id :total       :label "Total"  :type :decimal}
          {:id :status      :label "Status" :type :select
           :options [{:value "pending"   :label "Pending"}
                     {:value "shipped"   :label "Shipped"}
                     {:value "delivered" :label "Delivered"}]}]

 :queries {:list "SELECT * FROM orders WHERE customer_id = ? ORDER BY order_date DESC"
           :get  "SELECT * FROM orders WHERE id = ?"}

 :actions {:new true :edit true :delete true}}
```

---

## 10. Custom Queries

### Named SQL Queries

```clojure
:queries {:list    "SELECT * FROM products ORDER BY name"
          :get     "SELECT * FROM products WHERE id = ?"
          :active  "SELECT * FROM products WHERE status = 'active' ORDER BY name"
          :by-cat  "SELECT * FROM products WHERE category_id = ? ORDER BY name"}
```

### Queries with Joins

```clojure
:queries
{:list "SELECT o.*,
               c.name       AS customer_name,
               c.email      AS customer_email,
               COUNT(oi.id) AS item_count
        FROM orders o
        LEFT JOIN customers  c  ON o.customer_id = c.id
        LEFT JOIN order_items oi ON o.id = oi.order_id
        GROUP BY o.id
        ORDER BY o.created_at DESC"

 :get  "SELECT o.*, c.name AS customer_name
        FROM orders o
        LEFT JOIN customers c ON o.customer_id = c.id
        WHERE o.id = ?"}
```

### Function-Based Queries

For queries that need runtime logic, reference a function by its fully-qualified keyword:

```clojure
:queries {:low-stock    :myapp.queries.products/low-stock
          :sales-report :myapp.queries.reports/sales-by-month}
```

```clojure
(ns myapp.queries.products
  (:require [myapp.models.crud :as crud]))

(defn low-stock [params conn]
  (let [threshold (or (:threshold params) 10)]
    (crud/Query ["SELECT * FROM products WHERE stock < ? ORDER BY stock ASC"
                 threshold]
                :conn conn)))
```

---

## 11. Actions and Access Control

### CRUD Actions

```clojure
;; Full CRUD
:actions {:new true :edit true :delete true}

;; Read-only
:actions {:new false :edit false :delete false}

;; Append-only log
:actions {:new true :edit false :delete false}
```

### User Access Levels

```clojure
"U"  ; Regular user
"A"  ; Administrator
"S"  ; System
```

Restrict an entity to administrators and system only:

```clojure
{:entity :payroll
 :rights ["A" "S"]}
```

### Row-Level Security

Use `before-load` to filter rows based on the current user:

```clojure
(defn before-load [params]
  (if (= "U" (get-in params [:user :level]))
    (assoc-in params [:filters :owner_id] (get-in params [:user :id]))
    params))
```

---

## 12. Audit Trail

Enable per-entity with `:audit? true`. The framework automatically populates four columns on every save:

| Column | Content |
|---|---|
| `created_by` | User ID of the record creator |
| `created_at` | Timestamp of creation |
| `modified_by` | User ID of the last editor |
| `modified_at` | Timestamp of last modification |

These columns must exist in the database table. Include them in the migration:

```sql
created_by  INTEGER,
created_at  TEXT DEFAULT (datetime('now')),
modified_by INTEGER,
modified_at TEXT
```

To show them in the UI, include hidden fields in the entity:

```clojure
{:id :created_by  :label "Created By"  :type :hidden}
{:id :created_at  :label "Created At"  :type :hidden}
{:id :modified_by :label "Modified By" :type :hidden}
{:id :modified_at :label "Modified At" :type :hidden}
```

---

## 13. Multi-Database Support

Each entity can use a different database connection by setting `:connection`:

```clojure
{:entity :analytics :connection :postgres}
{:entity :products  :connection :default}
{:entity :archive   :connection :mysql}
```

Connection keys are defined in `app-config.edn`. The `:default` key is used if `:connection` is omitted.

---

## 14. Migrations

WebGen uses [Ragtime](https://github.com/weavejester/ragtime) for schema migrations.

### Migration File Naming

```
{number}-{name}.{database}.{direction}.sql
```

Examples:

```
001-users.sqlite.up.sql
001-users.sqlite.down.sql
001-users.mysql.up.sql
001-users.postgresql.up.sql
```

### Commands

```bash
lein migrate                        # Apply all pending migrations
lein rollback                       # Rollback the last applied migration
lein create-migration add-phone     # Create a new blank migration pair
```

### Converting Migrations Between Databases

The framework includes a converter that translates SQLite migrations to MySQL or PostgreSQL syntax:

```bash
lein convert-migrations mysql
lein convert-migrations postgresql
```

Type mappings applied automatically:

| SQLite | MySQL | PostgreSQL |
|---|---|---|
| `INTEGER PRIMARY KEY AUTOINCREMENT` | `INT AUTO_INCREMENT PRIMARY KEY` | `SERIAL PRIMARY KEY` |
| `INTEGER` | `INT` | `INTEGER` |
| `TEXT` | `TEXT` | `TEXT` |
| `REAL` | `DOUBLE` | `DOUBLE PRECISION` |
| `BLOB` | `LONGBLOB` | `BYTEA` |
| `datetime('now')` | `CURRENT_TIMESTAMP` | `CURRENT_TIMESTAMP` |

Always review generated files and add indexes, constraints, and VARCHAR lengths appropriate to your target database before applying.

### Copying Data Between Databases

```bash
lein copy-data mysql            # Copy all data from SQLite to MySQL
lein copy-data postgresql       # Copy all data from SQLite to PostgreSQL
lein copy-data mysql --clear    # Clear target tables before copying
```

Prerequisites: run `lein migrate` on the target database first.

---

## 15. Scaffolding

```bash
lein scaffold products          # Scaffold a single entity
lein scaffold --all             # Scaffold all tables in the database
lein scaffold --interactive     # Prompt for each table
lein scaffold products --force  # Overwrite existing files
```

Scaffolding creates:

- `resources/entities/products.edn` — entity configuration
- `resources/migrations/XXX-products.sqlite.up.sql` — SQLite migration
- `resources/migrations/XXX-products.sqlite.down.sql`
- `resources/migrations/XXX-products.mysql.up.sql`
- `resources/migrations/XXX-products.mysql.down.sql`
- `resources/migrations/XXX-products.postgresql.up.sql`
- `resources/migrations/XXX-products.postgresql.down.sql`
- `src/myapp/hooks/products.clj` — hook file

When scaffolding child tables that have a foreign key column, the corresponding parent entity's subgrid configuration is automatically updated.

---

## 16. Custom Routes and Handlers

### Public Routes

Edit `src/myapp/routes/routes.clj` for unauthenticated pages:

```clojure
(defroutes open-routes
  (GET  "/"             [] (home/main))
  (GET  "/home/login"   [] (home/login))
  (POST "/home/login"   [] (home/login-user))
  (GET  "/home/logoff"  [] (home/logoff-user))
  (GET  "/about"        [] (home/about-page))
  (GET  "/contact"      [] (home/contact-page))
  (POST "/contact"      [] (home/process-contact)))
```

### Protected Routes

Edit `src/myapp/routes/proutes.clj` for pages that require authentication. The framework applies authentication middleware automatically to all routes defined here.

```clojure
(defroutes proutes
  (GET  "/dashboard"         [] (dashboard/main))
  (GET  "/reports/sales"     [] (reports/sales))
  (POST "/reports/generate"  [] (reports/generate))
  (GET  "/api/products/:id"  [id] (api/get-product id))
  (POST "/api/orders"        [] (api/create-order)))
```

### Custom Handler (MVC Pattern)

```
src/myapp/handlers/
└── dashboard/
    ├── controller.clj
    ├── model.clj
    └── view.clj
```

**Controller:**

```clojure
(ns myapp.handlers.dashboard.controller
  (:require [myapp.handlers.dashboard.model :as model]
            [myapp.handlers.dashboard.view  :as view]))

(defn main [request]
  (let [user  (get-in request [:session :user])
        stats (model/get-stats user)]
    (view/dashboard-page stats)))
```

**Model:**

```clojure
(ns myapp.handlers.dashboard.model
  (:require [myapp.models.db :as db]))

(defn get-stats [user]
  {:order-count (db/query-one "SELECT COUNT(*) AS n FROM orders WHERE user_id = ?" [(:id user)])
   :revenue     (db/query-one "SELECT SUM(total) AS s FROM orders WHERE user_id = ?" [(:id user)])})
```

**View:**

```clojure
(ns myapp.handlers.dashboard.view
  (:require [myapp.layout :as layout]
            [hiccup.core  :refer [html]]))

(defn dashboard-page [stats]
  (layout/application "Dashboard"
    (html
      [:div.container.mt-4
       [:h1 "Dashboard"]
       [:p "Total orders: " (get-in stats [:order-count :n])]
       [:p "Revenue: $"     (get-in stats [:revenue :s])]])))
```

### Direct Database Access

```clojure
(ns myapp.handlers.reports.model
  (:require [myapp.models.db :as db]))

(defn top-customers [limit]
  (db/query
    "SELECT c.name, SUM(o.total) AS total_spent
     FROM customers c
     JOIN orders o ON c.id = o.customer_id
     GROUP BY c.id
     ORDER BY total_spent DESC
     LIMIT ?"
    [limit]))
```

---

## 17. Menu Customization

The menu is generated automatically from entity configurations. To add custom entries that are not entities, edit `src/myapp/menu.clj`.

### Custom Navigation Links

Each entry is a vector `["/path" "Label" "Rights" order]`. `"Rights"` is optional (`"U"` = regular users and above, `"A"` = admins only, `nil` = everyone). `order` controls position among nav links (lower = first).

```clojure
(def custom-nav-links
  [["/"          "Home"      nil  0]
   ["/dashboard" "Dashboard" "U" 10]
   ["/admin"     "Admin"     "A" 20]])
```

### Custom Dropdown Menus

Each dropdown requires `:id`, `:data-id`, `:label`, `:order`, and `:items`. Items use the same vector format as nav links.

```clojure
(def custom-dropdowns
  {:Reports
   {:id      "navdropReports"
    :data-id "Reports"
    :label   "Reports"
    :order   40
    :items   [["/reports/sales"     "Sales"     "U" 10]
              ["/reports/inventory" "Inventory" "U" 20]]}})
```

### Merging with Auto-Generated Menu

`get-menu-config` in `menu.clj` automatically merges `custom-nav-links` and `custom-dropdowns` with the auto-generated entity menus — sorted by `:order`. You only need to update those two vars; do not redefine `get-menu-config`.

---

## 18. Internationalization

Translation files are EDN maps stored in `resources/i18n/`. The framework ships with `en.edn` (English) and `es.edn` (Spanish). The default locale is `:es`; change it in `src/myapp/i18n/core.clj`.

Translation keys follow a namespace convention:

- `:common/*` — Reusable UI labels
- `:form/*` — Form field labels
- `:validation/*` — Validation messages
- `:error/*` — Error messages
- `:success/*` — Success messages
- `:menu/*` — Menu labels (replace with your project's navigation)
- `:entity/*` — Entity display names (replace with your entities)

Use the `t` function in views:

```clojure
(require '[myapp.i18n.core :refer [t]])

(t :common/save)           ; => "Save" (en) or "Guardar" (es)
(t :common/save :en)       ; => "Save" (forced locale)
(t :validation/required {:field "Email"}) ; => "Email is required"
```

---

## 19. Authentication and Security

### Default Users

After running `lein database`:

| Email | Password | Level |
|---|---|---|
| `user@example.com` | `user` | U |
| `admin@example.com` | `admin` | A |
| `system@example.com` | `system` | S |

Change all passwords before deploying to production.

### Authentication Features

- Password hashing with buddy-hashers (bcrypt)
- Secure cookie-based sessions
- Login/logout built-in at `/home/login` and `/home/logoff`
- All routes under `proutes` require an active session
- Role-based access per entity via `:rights`

### Custom Middleware

Add middleware in `src/myapp/core.clj`:

```clojure
(defn wrap-request-logging [handler]
  (fn [request]
    (println (:request-method request) (:uri request))
    (handler request)))

(defn app []
  (-> (routes open-routes proutes)
      wrap-request-logging
      wrap-session
      (wrap-defaults site-defaults)))
```

---

## 20. Themes

Set `:theme` in `app-config.edn`. Available Bootstrap themes:

`default` `cerulean` `cosmo` `cyborg` `darkly` `flatly` `journal` `litera` `lumen` `lux` `materia` `minty` `morph` `pulse` `quartz` `sandstone` `simplex` `sketchy` `slate` `solar` `spacelab` `superhero` `united` `vapor` `yeti` `zephyr`

---

## 21. Deployment

### Build

```bash
lein uberjar
```

### Run

```bash
java -jar target/uberjar/myapp-0.1.0-standalone.jar
```

### Run on a Specific Port

```bash
java -jar target/uberjar/myapp-0.1.0-standalone.jar 9090
```

Set `:port` in `app-config.edn` or pass it as a command-line argument.

---

## 22. Publishing the Template

The template is published to Clojars at `org.clojars.hector/webgen`. To publish an updated version:

```bash
# Update version in project.clj
lein deploy clojars
```

---

## 23. Custom Dashboards and Reports

The entity system covers CRUD operations efficiently. It does not cover dashboards, aggregated reports, multi-step wizards, custom POS interfaces, or any page where the layout or data needs to differ fundamentally from a data grid.

For those cases, WebGen gets out of the way. You write a normal Clojure Ring handler using the same MVC directory pattern as `src/myapp/handlers/home/`. The framework wraps your output with the application layout, applies authentication middleware, and serves the page — nothing more.

### When to Use a Custom Handler Instead of an Entity

| Need | Approach |
|---|---|
| List, create, edit, delete a table | Entity EDN config |
| Aggregate totals, charts, KPIs | Custom handler |
| Date-range filtered report | Custom handler |
| CSV / Excel export | Custom handler |
| Multi-step form or wizard | Custom handler |
| Custom POS / kiosk screen | Custom handler |
| Read-only summary joining many tables | Custom handler or entity with `:list` query |

### Directory Layout

Create a subdirectory under `handlers/` for each logical section:

```
src/myapp/handlers/
  dashboard/
    controller.clj   ; Ring handlers — receives request, returns response
    model.clj        ; Database queries, aggregations, business logic
    view.clj         ; Hiccup HTML templates
  reports/
    controller.clj
    model.clj
    view.clj
```

### Registering Routes

All protected pages go in `src/myapp/routes/proutes.clj`. The framework applies authentication middleware to every route declared here automatically.

```clojure
(ns myapp.routes.proutes
  (:require [compojure.core :refer [defroutes GET POST]]
            [myapp.handlers.dashboard.controller :as dashboard]
            [myapp.handlers.reports.controller   :as reports]))

(defroutes proutes
  (GET  "/dashboard"              request (dashboard/main request))
  (GET  "/reports/sales"          request (reports/sales request))
  (GET  "/reports/sales/export"   request (reports/export-csv request))
  (POST "/reports/sales"          request (reports/sales request)))
```

### Adding Items to the Menu

To make your custom pages appear in the navigation bar alongside the entity entries, edit `src/myapp/menu.clj`. Add top-level links to `custom-nav-links` and grouped dropdown entries to `custom-dropdowns`:

```clojure
;; Top-level nav link — visible to admins and above
(def custom-nav-links
  [["/"          "Home"      nil  0]
   ["/dashboard" "Dashboard" "A"  5]])

;; Dropdown group with report links
(def custom-dropdowns
  {:Reports
   {:id      "navdropReports"
    :data-id "Reports"
    :label   "Reports"
    :order   40
    :items   [["/reports/sales" "Sales Report" "A" 10]]}})
```

---

### Dashboard with KPI Cards and Chart Data

**`src/myapp/handlers/dashboard/model.clj`**

```clojure
(ns myapp.handlers.dashboard.model
  (:require [myapp.models.crud :refer [db Query]]))

(defn kpi-summary
  "Aggregate totals for today, this month, and all time."
  []
  (let [today        (first (Query db ["SELECT
                                         COUNT(*)           AS ventas_hoy,
                                         COALESCE(SUM(total), 0) AS ingresos_hoy
                                       FROM ventas
                                       WHERE DATE(fecha) = DATE('now')
                                         AND estado = 'completada'"]))
        this-month   (first (Query db ["SELECT
                                         COUNT(*)           AS ventas_mes,
                                         COALESCE(SUM(total), 0) AS ingresos_mes
                                       FROM ventas
                                       WHERE strftime('%Y-%m', fecha) = strftime('%Y-%m', 'now')
                                         AND estado = 'completada'"]))
        total-prods  (first (Query db ["SELECT COUNT(*) AS total FROM productos"]))]
    (merge today this-month total-prods)))

(defn sales-by-day
  "Last 30 days of daily revenue — suitable for a chart."
  []
  (Query db ["SELECT
               DATE(fecha)         AS dia,
               COUNT(*)            AS ventas,
               COALESCE(SUM(total), 0) AS ingresos
             FROM ventas
             WHERE fecha >= DATE('now', '-30 days')
               AND estado = 'completada'
             GROUP BY DATE(fecha)
             ORDER BY dia"]))

(defn top-products
  "Top 10 products by quantity sold."
  [limit]
  (Query db ["SELECT
               p.nombre,
               SUM(d.cantidad)            AS unidades,
               SUM(d.subtotal)            AS ingresos
             FROM ventas_detalle d
             JOIN productos p ON d.producto_id = p.id
             JOIN ventas    v ON d.venta_id    = v.id
             WHERE v.estado = 'completada'
             GROUP BY p.id
             ORDER BY unidades DESC
             LIMIT ?"
            limit]))
```

**`src/myapp/handlers/dashboard/view.clj`**

```clojure
(ns myapp.handlers.dashboard.view
  (:require [clojure.data.json :as json]))

(defn- kpi-card [label value icon color]
  [:div.col-md-3
   [:div.card.border-0.shadow-sm
    [:div.card-body.d-flex.align-items-center.gap-3
     [:div {:class (str "fs-1 text-" color)}
      [:i {:class (str "bi " icon)}]]
     [:div
      [:div.text-muted.small label]
      [:div.fs-4.fw-bold value]]]]])

(defn- top-products-table [rows]
  [:table.table.table-sm.table-hover
   [:thead.table-light
    [:tr [:th "Producto"] [:th.text-end "Unidades"] [:th.text-end "Ingresos"]]]
   [:tbody
    (for [r rows]
      [:tr
       [:td (:nombre r)]
       [:td.text-end (:unidades r)]
       [:td.text-end (format "$%.2f" (double (:ingresos r 0)))]])]])

(defn dashboard-view [kpi chart-data top-prods]
  (list
   ;; KPI cards
   [:div.row.g-3.mb-4
    (kpi-card "Ventas hoy"       (:ventas_hoy   kpi 0) "bi-cart-check"   "primary")
    (kpi-card "Ingresos hoy"     (format "$%.2f" (double (:ingresos_hoy  kpi 0))) "bi-currency-dollar" "success")
    (kpi-card "Ventas este mes"  (:ventas_mes   kpi 0) "bi-calendar-month" "info")
    (kpi-card "Productos"        (:total        kpi 0) "bi-box-seam"     "warning")]

   ;; Revenue chart (Chart.js)
   [:div.row.g-3.mb-4
    [:div.col-lg-8
     [:div.card.border-0.shadow-sm
      [:div.card-header.bg-transparent.fw-bold "Ingresos diarios (últimos 30 días)"]
      [:div.card-body
       [:canvas#sales-chart {:height "120"}]]]]
    [:div.col-lg-4
     [:div.card.border-0.shadow-sm
      [:div.card-header.bg-transparent.fw-bold "Top productos"]
      [:div.card-body (top-products-table top-prods)]]]]

   ;; Embed chart data for JavaScript
   [:script
    (str "var CHART_DATA = " (json/write-str chart-data) ";")]
   [:script {:src "/vendor/chart.min.js"}]
   [:script
    "
    (function() {
      var labels  = CHART_DATA.map(function(d){ return d.dia; });
      var valores = CHART_DATA.map(function(d){ return d.ingresos; });
      new Chart(document.getElementById('sales-chart'), {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [{ label: 'Ingresos', data: valores,
                       backgroundColor: 'rgba(13,110,253,0.6)' }]
        },
        options: { responsive: true, plugins: { legend: { display: false } } }
      });
    })();
    "]))
```

**`src/myapp/handlers/dashboard/controller.clj`**

```clojure
(ns myapp.handlers.dashboard.controller
  (:require [myapp.handlers.dashboard.model :as model]
            [myapp.handlers.dashboard.view  :as view]
            [myapp.layout                   :refer [application]]
            [myapp.models.util              :refer [get-session-id]]))

(defn main [request]
  (let [kpi        (model/kpi-summary)
        chart-data (model/sales-by-day)
        top-prods  (model/top-products 10)
        content    (view/dashboard-view kpi chart-data top-prods)]
    (application request "Dashboard" (get-session-id request) nil content)))
```

---

### Date-Range Sales Report with CSV Export

**`src/myapp/handlers/reports/model.clj`**

```clojure
(ns myapp.handlers.reports.model
  (:require [myapp.models.crud :refer [db Query]]))

(defn sales-report
  "Fetch sales detail rows between two dates (inclusive)."
  [from to]
  (Query db ["SELECT
               v.id          AS venta_id,
               v.fecha,
               v.total,
               v.pago,
               v.cambio,
               u.username    AS cajero
             FROM ventas v
             LEFT JOIN users u ON v.usuario_id = u.id
             WHERE DATE(v.fecha) BETWEEN ? AND ?
               AND v.estado = 'completada'
             ORDER BY v.fecha DESC"
            from to]))

(defn report-totals [rows]
  {:count   (count rows)
   :total   (reduce + 0 (map :total rows))
   :promedio (if (seq rows)
               (/ (reduce + 0 (map :total rows)) (count rows))
               0)})
```

**`src/myapp/handlers/reports/view.clj`**

```clojure
(ns myapp.handlers.reports.view
  (:require [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn- filter-form [from to]
  [:form.row.g-2.align-items-end.mb-4
   {:method "GET" :action "/reports/sales"}
   [:div.col-md-3
    [:label.form-label "Desde"]
    [:input.form-control {:type "date" :name "from" :value from}]]
   [:div.col-md-3
    [:label.form-label "Hasta"]
    [:input.form-control {:type "date" :name "to" :value to}]]
   [:div.col-md-2
    [:button.btn.btn-primary.w-100 {:type "submit"} "Filtrar"]]
   [:div.col-md-2
    [:a.btn.btn-outline-secondary.w-100
     {:href (str "/reports/sales/export?from=" from "&to=" to)}
     [:i.bi.bi-download.me-1] "CSV"]]])

(defn- totals-bar [totals]
  [:div.row.g-2.mb-3
   [:div.col-md-3
    [:div.card.text-bg-light
     [:div.card-body.text-center
      [:div.text-muted.small "Ventas"]
      [:div.fw-bold.fs-5 (:count totals)]]]]
   [:div.col-md-3
    [:div.card.text-bg-light
     [:div.card-body.text-center
      [:div.text-muted.small "Total"]
      [:div.fw-bold.fs-5 (format "$%.2f" (double (:total totals 0)))]]]]
   [:div.col-md-3
    [:div.card.text-bg-light
     [:div.card-body.text-center
      [:div.text-muted.small "Promedio"]
      [:div.fw-bold.fs-5 (format "$%.2f" (double (:promedio totals 0)))]]]]])

(defn- results-table [rows]
  [:table.table.table-striped.table-sm
   [:thead.table-dark
    [:tr
     [:th "#"] [:th "Fecha"] [:th "Cajero"] [:th.text-end "Total"]
     [:th.text-end "Pago"] [:th.text-end "Cambio"]]]
   [:tbody
    (if (seq rows)
      (for [r rows]
        [:tr
         [:td (:venta_id r)]
         [:td (:fecha r)]
         [:td (:cajero r)]
         [:td.text-end (format "$%.2f" (double (:total  r 0)))]
         [:td.text-end (format "$%.2f" (double (:pago   r 0)))]
         [:td.text-end (format "$%.2f" (double (:cambio r 0)))]])
      [:tr [:td.text-center {:colspan 6} "No hay ventas en el período seleccionado."]])]])

(defn sales-view [from to rows totals]
  (list
   [:h4.mb-3 "Reporte de Ventas"]
   (filter-form from to)
   (totals-bar totals)
   [:div.card.border-0.shadow-sm
    [:div.card-body (results-table rows)]]))
```

**`src/myapp/handlers/reports/controller.clj`**

```clojure
(ns myapp.handlers.reports.controller
  (:require [myapp.handlers.reports.model :as model]
            [myapp.handlers.reports.view  :as view]
            [myapp.layout                 :refer [application]]
            [myapp.models.util            :refer [get-session-id]]
            [clojure.string               :as str]))

(defn- today [] (str (java.time.LocalDate/now)))
(defn- first-of-month []
  (str (.withDayOfMonth (java.time.LocalDate/now) 1)))

(defn sales [request]
  (let [from   (get-in request [:params :from] (first-of-month))
        to     (get-in request [:params :to]   (today))
        rows   (model/sales-report from to)
        totals (model/report-totals rows)
        content (view/sales-view from to rows totals)]
    (application request "Sales Report" (get-session-id request) nil content)))

(defn export-csv [request]
  (let [from  (get-in request [:params :from] (first-of-month))
        to    (get-in request [:params :to]   (today))
        rows  (model/sales-report from to)
        header "id,fecha,cajero,total,pago,cambio\n"
        lines  (map (fn [r]
                      (str/join ","
                        [(:venta_id r) (:fecha r) (:cajero r)
                         (:total r 0) (:pago r 0) (:cambio r 0)]))
                    rows)
        csv   (str header (str/join "\n" lines))
        filename (str "ventas-" from "-" to ".csv")]
    {:status  200
     :headers {"Content-Type"        "text/csv; charset=utf-8"
               "Content-Disposition" (str "attachment; filename=\"" filename "\"")}
     :body    csv}))
```

**How CSV export works:** The `export-csv` handler runs the same query as the report page but returns a plain-text response with `Content-Type: text/csv` and a `Content-Disposition: attachment` header. The browser treats this as a file download automatically — no JavaScript or library needed.

---

### JSON Data API for Charts

When you need charts that refresh without reloading the page, expose the aggregated data as a JSON endpoint:

```clojure
;; In proutes.clj
(GET "/api/reports/sales-by-day" request (reports/api-sales-by-day request))

;; In controller.clj
(defn api-sales-by-day [request]
  (let [from (get-in request [:params :from] (first-of-month))
        to   (get-in request [:params :to]   (today))
        data (model/sales-by-day-range from to)]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (json/write-str {:ok true :data data})}))
```

The front-end calls this endpoint with `fetch()` and renders the data into a Chart.js or any other chart library.

---

### Summary: The 20% Pattern

```
Entity EDN config  -->  80%  -->  CRUD grids, forms, FK lookups, subgrids
Custom handler     -->  20%  -->  Dashboards, reports, exports, custom screens
```

Both approaches live in the same project and share the same database connection, layout, session, i18n, and middleware. The entity system accelerates the repetitive parts; the custom handler gives you full control for the parts that make your application unique.

Users create projects directly from Clojars without cloning this repository:

```bash
lein new org.clojars.hector/webgen myapp
```

---

## License

MIT License. See LICENSE for details.

## Resources

- [Clojure](https://clojure.org/)
- [Leiningen](https://leiningen.org/)
- [Ring](https://github.com/ring-clojure/ring)
- [Compojure](https://github.com/weavejester/compojure)
- [Hiccup](https://github.com/weavejester/hiccup)
- [Bootstrap 5](https://getbootstrap.com/)
- [Bootstrap Icons](https://icons.getbootstrap.com/)
- [DataTables](https://datatables.net/)
- [Ragtime](https://github.com/weavejester/ragtime)

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
# Edit `resources/config/app-config.edn` with your database credentials
# Default uses SQLite - just update passwords for MySQL/PostgreSQL if needed
nano resources/config/app-config.edn

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
| `:checkbox` | Single checkbox | Active, featured | `value` (value sent when checked; `""` sent when unchecked) |
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

Edit `resources/config/app-config.edn`:

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
