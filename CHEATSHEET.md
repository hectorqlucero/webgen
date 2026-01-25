# WebGen/LST Template - Complete Cheatsheet

> **Comprehensive reference for entities, hooks, validators, computed fields, and all configuration options**

---

## Table of Contents
1. [Entity Configuration Structure](#1-entity-configuration-structure)
2. [Field Types & Options](#2-field-types--options)
3. [Hooks System](#3-hooks-system-lifecycle-events)
4. [Validators](#4-validators)
5. [Computed Fields](#5-computed-fields)
6. [Subgrids (Parent-Child)](#6-subgrids-parent-child-relationships)
7. [Custom Queries](#7-custom-queries)
8. [Actions & Permissions](#8-actions--permissions)
9. [Audit Trail](#9-audit-trail)
10. [Multi-Database Support](#10-multi-database-support)
11. [Quick Commands](#11-quick-commands)
12. [Complete Examples](#12-complete-examples)

---

## 1. Entity Configuration Structure

### All Entity Options (Concise Reference)

```clojure
{;; ===== CORE CONFIGURATION =====
 :entity :products              ; Unique identifier (keyword)
 :title "Products"              ; Display name in UI
 :table "products"              ; Database table name
 :connection :default           ; DB connection (:default, :postgres, :mysql, :sqlite)
 :mode :parameter-driven        ; Configuration mode (default)
 
 ;; ===== ACCESS CONTROL =====
 :rights ["U" "A" "S"]          ; User/Admin/System access levels
 :audit? true                   ; Enable audit trail (created_by, modified_by, etc)
 
 ;; ===== MENU CONFIGURATION =====
 :menu-category :catalog        ; Menu group (:catalog, :clients, :properties, :financial, 
                                ;             :transactions, :documents, :system, :admin, :reports)
 :menu-order 10                 ; Sort order in menu (lower = higher priority)
 :menu-hidden? false            ; Hide from main menu navigation
 :menu-icon "bi bi-box"         ; Bootstrap icon class (optional)
 
 ;; ===== DATA CONFIGURATION =====
 :fields [...]                  ; Field definitions (see Section 2)
 :queries {...}                 ; SQL queries (:list, :get, custom)
 :actions {:new true            ; Enable create action
           :edit true           ; Enable update action
           :delete true}        ; Enable delete action
 
 ;; ===== BUSINESS LOGIC =====
 :hooks {:before-load fn        ; Modify query params
         :after-load fn         ; Transform loaded data
         :before-save fn        ; Validate before save
         :after-save fn         ; Side effects after save
         :before-delete fn      ; Validate before delete
         :after-delete fn}      ; Cleanup after delete
 
 ;; ===== RELATIONSHIPS =====
 :subgrids [{:entity :items     ; Child entity
             :foreign-key :product_id
             :title "Items"
             :icon "bi bi-list"}]
 
 ;; ===== UI CUSTOMIZATION (Advanced) =====
 :ui {:grid-fn :ns/custom-grid-render
      :form-fn :ns/custom-form-render
      :dashboard-fn :ns/custom-dashboard}}
```

### Menu Category Options

| Category | Description | Common Entities |
|----------|-------------|-----------------|
| `:catalog` | Products, inventory | products, categories, suppliers |
| `:clients` | Customers, contacts | customers, contacts, agents |
| `:properties` | Real estate | properties, buildings, units |
| `:financial` | Money management | payments, invoices, commissions |
| `:transactions` | Business operations | orders, sales, rentals |
| `:documents` | File management | documents, contracts, attachments |
| `:system` | System config | users, roles, settings |
| `:admin` | Administration | audit logs, backups |
| `:reports` | Reporting | dashboards, analytics |

### Minimal Entity
```clojure
{:entity :products
 :title "Products"
 :table "products"
 :connection :default
 :rights ["U" "A" "S"]
 :menu-category :catalog}
```

---

## 2. Field Types & Options

### Text Input Types

| Type | HTML Type | Common Options | Use Case |
|------|-----------|----------------|----------|
| `:text` | `text` | `placeholder`, `maxlength`, `required` | Single-line input |
| `:textarea` | `textarea` | `rows`, `placeholder`, `required` | Multi-line input |
| `:email` | `email` | `placeholder`, `required` | Email with validation |
| `:password` | `password` | `placeholder`, `required` | Masked password input |

**Examples:**
```clojure
{:id :name :label "Full Name" :type :text :required true :maxlength 100}
{:id :bio :label "Biography" :type :textarea :rows 5 :placeholder "Tell us about yourself..."}
{:id :email :label "Email" :type :email :required true}
```

### Numeric Types

| Type | HTML Type | Common Options | Use Case |
|------|-----------|----------------|----------|
| `:number` | `number` | `min`, `max`, `placeholder` | Integer input |
| `:decimal` | `number` (step=0.01) | `min`, `max`, `step`, `placeholder` | Currency/floats |

**Examples:**
```clojure
{:id :age :label "Age" :type :number :min 0 :max 120}
{:id :price :label "Price" :type :decimal :min 0 :step 0.01 :placeholder "0.00"}
{:id :quantity :label "Quantity" :type :number :min 1 :value 1}
```

### Date/Time Types

| Type | HTML Type | Common Options | Use Case |
|------|-----------|----------------|----------|
| `:date` | `date` | `min`, `max` | Date picker |
| `:datetime` | `datetime-local` | `min`, `max` | Date + time picker |
| `:time` | `time` | `min`, `max` | Time only |

**Examples:**
```clojure
{:id :birth_date :label "Birth Date" :type :date :required true}
{:id :appointment :label "Appointment" :type :datetime}
{:id :opening_time :label "Opening Time" :type :time}
```

### Selection Types

#### Dropdown (Select)
```clojure
{:id :status 
 :label "Status" 
 :type :select 
 :required true
 :options [{:value "active" :label "Active"}
           {:value "inactive" :label "Inactive"}
           {:value "pending" :label "Pending"}]}

;; With default value
{:id :priority 
 :label "Priority" 
 :type :select 
 :value "normal"
 :options [{:value "low" :label "Low"}
           {:value "normal" :label "Normal"}
           {:value "high" :label "High"}]}

;; Foreign key - FK
{:id :property_id
 :label "Property"
 :type :fk
 :fk :property
 :fk-field [:titulo :estado :contacto]
 :fk-sort [:clave :titulo]
 :fk-filter [:activo "T"]}

;; Dropdown with Database Value
{:id :category_id
 :label "Category"
 :type :select
 :options :inv.models.lookups/get-categories}
```

#### Radio Buttons
```clojure
{:id :gender 
 :label "Gender" 
 :type :radio 
 :value "M"
 :options [{:id "g1" :label "Male" :value "M"}
           {:id "g2" :label "Female" :value "F"}
           {:id "g3" :label "Other" :value "O"}]}
```

#### Checkbox
```clojure
{:id :newsletter 
 :label "Subscribe to Newsletter" 
 :type :checkbox 
 :value "T"}

{:id :terms_accepted 
 :label "I agree to terms" 
 :type :checkbox 
 :required true}
```

### Special Types

| Type | Use Case | Notes |
|------|----------|-------|
| `:file` | File/image upload | Handled via hooks |
| `:hidden` | Internal fields | ID fields, foreign keys |
| `:computed` | Read-only calculated | Via hooks/compute-fn |

**Examples:**
```clojure
{:id :id :label "ID" :type :hidden}
{:id :total :label "Total" :type :decimal :hidden-in-form? true}
{:id :total :label "Total" :type :computed :computed-fn :inv.hooks.products/calculate-total}
{:id :thumbnail :label "Image" :type :file}
```

### Field Visibility Flags

```clojure
;; Show in grid, hide in form
{:id :total 
 :label "Total" 
 :type :decimal
 :hidden-in-form? true}

;; Show in form, hide in grid
{:id :notes 
 :label "Internal Notes" 
 :type :textarea
 :hidden-in-grid? true}

;; Grid only - never appears in forms
{:id :customer_name 
 :label "Customer" 
 :type :text
 :grid-only? true}

;; Completely hidden (for computed/internal fields)
{:id :id 
 :label "ID" 
 :type :hidden}
```

### All Field Options Reference

```clojure
{:id :field_name              ; Required: Database column name
 :label "Display Label"       ; Required: UI label
 :type :text                  ; Required: Field type
 :required true               ; Make field mandatory
 :placeholder "hint text"     ; Placeholder text
 :value "default"             ; Default value
 :maxlength 100               ; Max character length
 :min 0                       ; Minimum value (numbers/dates)
 :max 100                     ; Maximum value (numbers/dates)
 :step 0.01                   ; Step increment (numbers)
 :rows 5                      ; Textarea rows
 :options [...]               ; Select/radio options
 :validation :ns/validator    ; Custom validator function
 :compute-fn :ns/computer     ; Computed value function
 :hidden-in-form? false       ; Hide in create/edit forms
 :hidden-in-grid? false       ; Hide in data grid
 :grid-only? false}           ; Only show in grid, never in forms
```

---

## 3. Hooks System (Lifecycle Events)

### Available Hooks

```clojure
:hooks {
  :before-load   fn     ; Modify query params before loading data
  :after-load    fn     ; Transform results after loading
  :before-save   fn     ; Validate/transform before saving
  :after-save    fn     ; Side effects after successful save
  :before-delete fn     ; Validate before deletion
  :after-delete  fn     ; Cleanup after deletion
}
```

### Hook Execution Flow

**Loading Data (GET/LIST):**
```
User Request → [before-load] → Execute Query → [after-load] → Render UI
```

**Saving Data (CREATE/UPDATE):**
```
Form Submit → [before-save] → Save to DB → [after-save] → Redirect
```

**Deleting Data:**
```
Delete Click → [before-delete] → Delete from DB → [after-delete] → Redirect
```

### Hook Signatures

| Hook | Parameters | Return (Success) | Return (Error) |
|------|------------|------------------|----------------|
| `before-load` | `[params]` | Modified params map | N/A (always returns params) |
| `after-load` | `[rows params]` | Modified rows vector | N/A (always returns rows) |
| `before-save` | `[params]` | Modified params map | `{:errors {...}}` |
| `after-save` | `[entity-id params]` | `{:success true}` or map | `{:error "message"}` |
| `before-delete` | `[entity-id]` | `{:success true}` | `{:errors {...}}` |
| `after-delete` | `[entity-id]` | `{:success true}` or map | `{:error "message"}` |

### Hook Examples

#### before-load: Filter Results
```clojure
(ns app.hooks.products)

(defn before-load
  "Only show active products to regular users"
  [params]
  (if (= "U" (:user-level params))
    (assoc params :status "active")
    params))
```

#### after-load: Add Computed Fields
```clojure
(defn after-load
  "Calculate totals and status for each row"
  [rows params]
  (mapv (fn [row]
          (let [subtotal (* (:quantity row 0) (:price row 0))
                tax (* subtotal 0.08)
                total (+ subtotal tax)]
            (assoc row
                   :subtotal subtotal
                   :tax tax
                   :total total
                   :status (if (> total 1000) "High Value" "Normal"))))
        rows))
```

#### before-save: Validation & Transformation
```clojure
(defn before-save
  "Validate business rules and set defaults"
  [params]
  (let [price (:price params)
        quantity (:quantity params)]
    (cond
      ;; Validation: Price must be positive
      (and price (<= price 0))
      {:errors {:price "Price must be greater than 0"}}
      
      ;; Validation: Quantity required
      (nil? quantity)
      {:errors {:quantity "Quantity is required"}}
      
      ;; Success: Add computed fields and defaults
      :else
      (-> params
          (assoc :total (* price quantity))
          (assoc :created_at (java.time.LocalDateTime/now))
          (assoc :status "pending")))))
```

#### after-save: Side Effects
```clojure
(defn after-save
  "Send notifications and update related records"
  [entity-id params]
  (try
    ;; Send email notification
    (when (:notify_customer params)
      (send-email (:customer_email params) 
                  "Order Confirmation" 
                  (format "Order #%s created" entity-id)))
    
    ;; Update inventory
    (update-inventory! (:product_id params) 
                       (- (:quantity params)))
    
    ;; Log to audit trail
    (log-activity! {:action :order-created
                    :order-id entity-id
                    :user (:user-id params)})
    
    {:success true :message "Order created successfully"}
    
    (catch Exception e
      {:error (str "Post-save error: " (.getMessage e))})))
```

#### before-delete: Constraint Checking
```clojure
(defn before-delete
  "Prevent deletion if related records exist"
  [entity-id]
  (let [has-orders? (has-related-orders? entity-id)
        has-payments? (has-related-payments? entity-id)]
    (cond
      has-orders?
      {:errors {:general "Cannot delete: Customer has existing orders"}}
      
      has-payments?
      {:errors {:general "Cannot delete: Customer has payment history"}}
      
      :else
      {:success true})))
```

#### after-delete: Cascade & Cleanup
```clojure
(defn after-delete
  "Clean up related records and files"
  [entity-id]
  (try
    ;; Delete related records
    (delete-related-addresses! entity-id)
    (delete-related-notes! entity-id)
    
    ;; Delete uploaded files
    (delete-customer-files! entity-id)
    
    ;; Invalidate caches
    (invalidate-cache! :customers entity-id)
    
    {:success true}
    
    (catch Exception e
      {:error (str "Cleanup error: " (.getMessage e))})))
```

### Hook Configuration in Entity

```clojure
{:entity :orders
 :title "Orders"
 :table "orders"
 
 :hooks {:before-load :app.hooks.orders/before-load
         :after-load :app.hooks.orders/after-load
         :before-save :app.hooks.orders/before-save
         :after-save :app.hooks.orders/after-save
         :before-delete :app.hooks.orders/before-delete
         :after-delete :app.hooks.orders/after-delete}}
```

### File Upload Hook Example

```clojure
(ns app.hooks.products
  (:require [clojure.java.io :as io]))

(defn before-save
  "Handle file upload and save path to database"
  [params]
  (if-let [file (:image-file params)]
    (let [filename (str "uploads/" (java.util.UUID/randomUUID) 
                        (get-extension (:filename file)))
          _ (io/copy (:tempfile file) (io/file filename))]
      (-> params
          (dissoc :image-file)
          (assoc :image_path filename)))
    params))
```

---

## 4. Validators

### Field-Level Validation

```clojure
;; In entity configuration
{:id :price
 :label "Price"
 :type :decimal
 :required true
 :validation :app.validators/positive-number}

;; In src/app/validators.clj
(ns app.validators)

(defn positive-number
  "Validate that number is positive"
  [value data]
  (when value
    (if (> value 0)
      true  ; Valid
      false))) ; Invalid

;; Return custom error message
(defn validate-email-format
  [value data]
  (if (re-matches #".+@.+\..+" (str value))
    true
    "Invalid email format"))
```

### Multi-Field Validation

```clojure
(ns app.validators)

(defn validate-date-range
  "Ensure end date is after start date"
  [params]
  (let [start (:start_date params)
        end (:end_date params)]
    (if (and start end (< (.compareTo end start) 0))
      {:end_date "End date must be after start date"}
      nil))) ; nil means valid

;; Use in before-save hook
(defn before-save [params]
  (if-let [errors (validate-date-range params)]
    {:errors errors}
    params))
```

### Common Validators

```clojure
(ns app.validators)

;; Positive number
(defn positive? [value _]
  (and value (> value 0)))

;; Email format
(defn email? [value _]
  (re-matches #".+@.+\..+" (str value)))

;; Phone format
(defn phone? [value _]
  (re-matches #"\d{3}-\d{3}-\d{4}" (str value)))

;; ZIP code
(defn zip-code? [value _]
  (re-matches #"\d{5}(-\d{4})?" (str value)))

;; URL format
(defn url? [value _]
  (re-matches #"https?://.+" (str value)))

;; Date in future
(defn future-date? [value _]
  (when value
    (< (.compareTo (java.time.LocalDate/now) value) 0)))

;; String length range
(defn length-between [min max]
  (fn [value _]
    (let [len (count (str value))]
      (<= min len max))))

;; One of allowed values
(defn one-of [allowed-values]
  (fn [value _]
    (some #(= value %) allowed-values)))
```

### Built-In HTML5 Validations

These are automatically handled by field types:
- `:email` → Email format validation
- `:required true` → Required field
- `:min` / `:max` → Numeric/date range
- `:maxlength` → String length limit

---

## 5. Computed Fields

### Method 1: Via after-load Hook

```clojure
;; Entity configuration
{:fields [{:id :quantity :label "Quantity" :type :number}
          {:id :price :label "Price" :type :decimal}
          {:id :total :label "Total" :type :decimal :hidden-in-form? true}]}

;; Hook implementation
(ns app.hooks.order-items)

(defn after-load [rows params]
  (mapv (fn [row]
          (let [qty (or (:quantity row) 0)
                price (or (:price row) 0.0)]
            (assoc row :total (* qty price))))
        rows))
```

### Method 2: Via compute-fn

```clojure
;; Entity configuration
{:id :total
 :label "Total"
 :type :decimal
 :hidden-in-form? true
 :compute-fn :app.hooks.orders/calculate-total}

;; Hook implementation
(ns app.hooks.orders)

(defn calculate-total
  "Compute total from quantity and price"
  [row]
  (let [qty (or (:quantity row) 0)
        price (or (:price row) 0.0)]
    (* qty price)))
```

### Complex Computed Field Examples

```clojure
(ns app.hooks.invoices)

;; Age calculation
(defn calculate-age [row]
  (when-let [birth-date (:birth_date row)]
    (let [now (java.time.LocalDate/now)
          years (.between java.time.temporal.ChronoUnit/YEARS 
                         birth-date now)]
      years)))

;; Days overdue
(defn days-overdue [row]
  (when-let [due-date (:due_date row)]
    (let [now (java.time.LocalDate/now)
          days (.between java.time.temporal.ChronoUnit/DAYS 
                        due-date now)]
      (max 0 days))))

;; Status badge
(defn status-badge [row]
  (let [overdue (days-overdue row)]
    (cond
      (nil? overdue) "N/A"
      (= 0 overdue) "✓ On Time"
      (< overdue 7) "⚠ Due Soon"
      :else "✗ Overdue")))

;; Full name from parts
(defn full-name [row]
  (str (:first_name row) " " (:last_name row)))

;; After-load hook using all computed fields
(defn after-load [rows params]
  (mapv (fn [row]
          (assoc row
                 :age (calculate-age row)
                 :days_overdue (days-overdue row)
                 :status_display (status-badge row)
                 :full_name (full-name row)))
        rows))
```

---

## 6. Subgrids (Parent-Child Relationships)

### Overview

When an entity has subgrids defined, the UI automatically switches to **TabGrid mode** with tabs for the parent record and each child entity.

### Basic Configuration

```clojure
{:entity :properties
 :title "Properties"
 :table "properties"
 
 :subgrids [{:entity :rentals
             :foreign-key :property_id
             :title "Rentals"
             :icon "bi bi-file-text"}
            
            {:entity :maintenance
             :foreign-key :property_id
             :title "Maintenance Requests"
             :icon "bi bi-tools"}]}
```

### User Experience

```
┌─────────────────────────────────────────┐
│  Property #12345                        │
│  [Select] [Edit] [Delete]               │
├─────────────────────────────────────────┤
│  Address: 123 Main St                   │
│  City: Los Angeles                      │
│  Price: $850,000                        │
└─────────────────────────────────────────┘

[Property Details] [Rentals] [Maintenance]

┌─────────────────────────────────────────┐
│  Rentals for Property #12345    [New]   │
├─────────────────────────────────────────┤
│  ID   Tenant        Amount    Status    │
│  1    John Doe      $2,500    Active    │
│  2    Jane Smith    $2,300    Pending   │
└─────────────────────────────────────────┘
```

### Complex Example

```clojure
{:entity :customers
 :title "Customers"
 :table "customers"
 
 :fields [{:id :id :type :hidden}
          {:id :name :label "Name" :type :text}
          {:id :email :label "Email" :type :email}]
 
 :subgrids [
   ;; Orders subgrid
   {:entity :orders
    :foreign-key :customer_id
    :title "Orders"
    :icon "bi bi-cart"}
   
   ;; Addresses subgrid
   {:entity :addresses
    :foreign-key :customer_id
    :title "Addresses"
    :icon "bi bi-house"}
   
   ;; Payment methods subgrid
   {:entity :payment_methods
    :foreign-key :customer_id
    :title "Payment Methods"
    :icon "bi bi-credit-card"}
   
   ;; Support tickets subgrid
   {:entity :tickets
    :foreign-key :customer_id
    :title "Support Tickets"
    :icon "bi bi-chat"}]}
```

### Automatic Detection

When scaffolding, foreign keys are automatically detected:

```bash
# Scaffold child entity
lein scaffold order_items

# If order_items has an order_id column:
# 1. Creates order_items.edn
# 2. Automatically adds to orders.edn subgrids
# 3. Links via order_id foreign key
```

### Icons Reference

Use Bootstrap Icons classes:
- `bi bi-cart` - Shopping cart
- `bi bi-file-text` - Document
- `bi bi-house` - Home/Address
- `bi bi-credit-card` - Payment
- `bi bi-chat` - Messages/Support
- `bi bi-tools` - Maintenance/Settings
- `bi bi-graph-up` - Analytics/Reports
- `bi bi-paperclip` - Attachments
- `bi bi-people` - Users/Contacts

Full list: https://icons.getbootstrap.com/

---

## 7. Custom Queries

### Simple SQL Queries

```clojure
:queries {
  :list "SELECT * FROM products ORDER BY name"
  :get "SELECT * FROM products WHERE id = ?"
  :active "SELECT * FROM products WHERE status = 'active'"
}
```

### Queries with Parameters

```clojure
:queries {
  ;; Use ? placeholders for parameters
  :by-category "SELECT * FROM products WHERE category_id = ? ORDER BY name"
  :by-date-range "SELECT * FROM orders WHERE order_date BETWEEN ? AND ?"
  :search "SELECT * FROM products WHERE name LIKE ? OR description LIKE ?"
}
```

### Queries with Joins

```clojure
:queries {
  :list "SELECT o.*, c.name as customer_name, c.email as customer_email
         FROM orders o
         LEFT JOIN customers c ON o.customer_id = c.id
         ORDER BY o.order_date DESC"
  
  :with-items "SELECT o.*, 
                COUNT(oi.id) as item_count,
                SUM(oi.quantity * oi.price) as total
               FROM orders o
               LEFT JOIN order_items oi ON o.id = oi.order_id
               WHERE o.id = ?
               GROUP BY o.id"
}
```

### Function-Based Queries

```clojure
;; In entity configuration
:queries {:low-stock :app.queries.products/low-stock
          :by-supplier :app.queries.products/by-supplier}

;; In src/app/queries/products.clj
(ns app.queries.products
  (:require [app.engine.crud :as crud]))

(defn low-stock
  "Return products below threshold"
  [params conn]
  (let [threshold (or (:threshold params) 10)]
    (crud/Query ["SELECT * FROM products 
                  WHERE stock < ? 
                  ORDER BY stock ASC" 
                 threshold]
                :conn conn)))

(defn by-supplier
  "Return products by supplier with supplier details"
  [params conn]
  (let [supplier-id (:supplier_id params)]
    (crud/Query ["SELECT p.*, s.name as supplier_name
                  FROM products p
                  JOIN suppliers s ON p.supplier_id = s.id
                  WHERE p.supplier_id = ?
                  ORDER BY p.name"
                 supplier-id]
                :conn conn)))
```

### Dynamic Query Building

```clojure
(ns app.queries.reports)

(defn sales-report
  "Dynamic sales report with filters"
  [params conn]
  (let [base "SELECT * FROM orders WHERE 1=1"
        conditions []
        values []
        
        ;; Add date filter if provided
        [query values] (if-let [start (:start_date params)]
                         [(str base " AND order_date >= ?")
                          (conj values start)]
                         [base values])
        
        ;; Add customer filter
        [query values] (if-let [cust (:customer_id params)]
                         [(str query " AND customer_id = ?")
                          (conj values cust)]
                         [query values])
        
        ;; Add status filter
        [query values] (if-let [status (:status params)]
                         [(str query " AND status = ?")
                          (conj values status)]
                         [query values])
        
        final-query (str query " ORDER BY order_date DESC")]
    
    (crud/Query (into [final-query] values) :conn conn)))
```

---

## 8. Actions & Permissions

### CRUD Actions

```clojure
;; Full CRUD access
:actions {:new true :edit true :delete true}

;; Read-only (for dashboards/reports)
:actions {:new false :edit false :delete false}

;; Create and edit only
:actions {:new true :edit true :delete false}

;; View and create only
:actions {:new true :edit false :delete false}
```

### User Access Levels

```clojure
;; Available rights
"U" - Regular User
"A" - Administrator
"S" - System (full access)

;; Entity-level permissions
{:entity :payroll
 :rights ["A" "S"]}  ; Only admins and system can access

{:entity :reports
 :rights ["U" "A" "S"]}  ; All users can access
```

### Conditional Actions (via Hooks)

```clojure
(ns app.hooks.orders)

(defn before-delete
  "Only allow deleting draft orders"
  [entity-id]
  (let [order (get-order entity-id)]
    (if (= "draft" (:status order))
      {:success true}
      {:errors {:general "Only draft orders can be deleted"}})))

(defn before-save
  "Prevent editing completed orders"
  [params]
  (if (and (:id params) ; Editing existing
           (= "completed" (:status (get-order (:id params)))))
    {:errors {:general "Completed orders cannot be edited"}}
    params))
```

---

## 9. Audit Trail

### Enable Audit Logging

```clojure
{:entity :customers
 :title "Customers"
 :table "customers"
 :audit? true}  ; Tracks who/when created and modified
```

### Audit Fields Added

When `:audit? true`:
- `created_by` - User ID who created record
- `created_at` - Timestamp of creation
- `modified_by` - User ID who last modified
- `modified_at` - Timestamp of last modification

These fields are automatically managed by the framework.

---

## 10. Multi-Database Support

### Configuration File

**File:** `resources/private/config.clj`

```clojure
{:connections {
   ;; SQLite
   :sqlite {:db-type "sqlite"
            :db-class "org.sqlite.JDBC"
            :db-name "db/app.sqlite"}
   
   ;; MySQL
   :mysql {:db-type "mysql"
           :db-host "localhost"
           :db-port 3306
           :db-name "mydb"
           :db-user "root"
           :db-pwd "password"}
   
   ;; PostgreSQL
   :postgres {:db-type "postgresql"
              :db-host "localhost"
              :db-port 5432
              :db-name "pgdb"
              :db-user "postgres"
              :db-pwd "password"}
   
   ;; Set defaults
   :default :sqlite    ; Default connection for entities
   :main :sqlite}      ; Connection for migrations
}
```

### Per-Entity Connection

```clojure
;; Most entities use default (SQLite)
{:entity :products
 :connection :default}

;; Archive stored in PostgreSQL
{:entity :archive
 :connection :postgres}

;; Analytics in separate MySQL database
{:entity :analytics
 :connection :mysql}
```

---

## 11. Quick Commands

### Development

```bash
# Start development server with auto-reload
lein with-profile dev run

# Start with specific port
lein with-profile dev run 8080
```

### Scaffolding

```bash
# Scaffold single table
lein scaffold products

# Scaffold all tables
lein scaffold --all

# Interactive mode (prompts for each table)
lein scaffold --interactive

# Scaffold with custom connection
lein scaffold products --connection postgres
```

### Database Migrations

```bash
# Apply pending migrations
lein migrate

# Rollback last migration
lein rollback

# Create new migration
lein create-migration add-customer-fields
```

### Production Build

```bash
# Build uberjar
lein uberjar

# Run production jar
java -jar target/uberjar/myapp-0.1.0-standalone.jar

# Run on specific port
java -jar target/uberjar/myapp-0.1.0-standalone.jar 8080
```

---

## 12. Complete Examples

### Example 1: Simple CRUD Entity

```clojure
{:entity :products
 :title "Products"
 :table "products"
 :connection :default
 :rights ["U" "A" "S"]
 :mode :parameter-driven
 
 :fields [
   {:id :id :type :hidden}
   {:id :name :label "Product Name" :type :text :required true}
   {:id :description :label "Description" :type :textarea :rows 4}
   {:id :price :label "Price" :type :decimal :min 0 :step 0.01 :required true}
   {:id :stock :label "Stock" :type :number :min 0 :step 1 :value 0}
   {:id :active :label "Active" :type :radio :value "T" :options [{:id "aT" :value "T" :label "Active"}
                                                                  {:id "aF" :value "F" :label "Inactive"}]}]
 
 :queries {
   :list "SELECT * FROM products ORDER BY name"
   :get "SELECT * FROM products WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}}
```

### Example 2: Complex Entity with Hooks

```clojure
{:entity :orders
 :title "Orders"
 :table "orders"
 :connection :default
 :rights ["U" "A" "S"]
 :mode :parameter-driven
 :audit? true
 
 :fields [
   {:id :id :type :hidden}
   {:id :order_number :label "Order #" :type :text :required true}
   
   {:id :customer_id 
    :label "Customer" 
    :type :select 
    :required true
    :options [{:value 1 :label "John Doe"}
              {:value 2 :label "Jane Smith"}]}
   
   {:id :order_date :label "Order Date" :type :date :required true}
   {:id :ship_date :label "Ship Date" :type :date}
   
   {:id :status 
    :label "Status" 
    :type :select 
    :value "pending"
    :options [{:value "pending" :label "Pending"}
              {:value "processing" :label "Processing"}
              {:value "shipped" :label "Shipped"}
              {:value "delivered" :label "Delivered"}
              {:value "cancelled" :label "Cancelled"}]}
   
   {:id :subtotal :label "Subtotal" :type :decimal :hidden-in-form? true}
   {:id :tax :label "Tax" :type :decimal :hidden-in-form? true}
   {:id :total :label "Total" :type :decimal :hidden-in-form? true}
   
   {:id :notes :label "Notes" :type :textarea :rows 3 :hidden-in-grid? true}]
 
 :queries {
   :list "SELECT o.*, c.name as customer_name 
          FROM orders o
          LEFT JOIN customers c ON o.customer_id = c.id
          ORDER BY o.order_date DESC"
   
   :get "SELECT * FROM orders WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}
 
 :hooks {
   :after-load :app.hooks.orders/calculate-totals
   :before-save :app.hooks.orders/validate-dates
   :after-save :app.hooks.orders/send-confirmation
   :before-delete :app.hooks.orders/check-items}
 
 :subgrids [{:entity :order_items
             :foreign-key :order_id
             :title "Order Items"
             :icon "bi bi-box-seam"}]}
```

**Hooks file:** `src/app/hooks/orders.clj`

```clojure
(ns app.hooks.orders
  (:require [app.engine.crud :as crud]))

(defn calculate-totals
  "Add computed totals to each order"
  [rows params]
  (mapv (fn [row]
          (let [subtotal (:subtotal row 0)
                tax (* subtotal 0.08)
                total (+ subtotal tax)]
            (assoc row
                   :tax tax
                   :total total)))
        rows))

(defn validate-dates
  "Ensure ship date is after order date"
  [params]
  (let [order-date (:order_date params)
        ship-date (:ship_date params)]
    (if (and order-date ship-date 
             (< (.compareTo ship-date order-date) 0))
      {:errors {:ship_date "Ship date must be after order date"}}
      params)))

(defn send-confirmation
  "Send email after order is saved"
  [entity-id params]
  (try
    (when (= "processing" (:status params))
      (send-email (:customer_email params)
                  "Order Confirmation"
                  (format "Your order #%s is being processed" 
                          (:order_number params))))
    {:success true}
    (catch Exception e
      {:error (str "Email error: " (.getMessage e))})))

(defn check-items
  "Prevent deleting orders with items"
  [entity-id]
  (let [items (crud/Query ["SELECT COUNT(*) as cnt 
                           FROM order_items 
                           WHERE order_id = ?" entity-id])
        count (get-in items [0 :cnt] 0)]
    (if (> count 0)
      {:errors {:general "Cannot delete order with items"}}
      {:success true})))
```

### Example 3: Parent-Child with Subgrids

**Parent:** `resources/entities/properties.edn`

```clojure
{:entity :properties
 :title "Properties"
 :table "properties"
 :connection :default
 :rights ["A" "S"]
 :mode :parameter-driven
 
 :fields [
   {:id :id :type :hidden}
   {:id :address :label "Address" :type :text :required true}
   {:id :city :label "City" :type :text :required true}
   {:id :state :label "State" :type :text :maxlength 2}
   {:id :zip :label "ZIP" :type :text :maxlength 10}
   {:id :price :label "Price" :type :decimal :min 0}
   {:id :bedrooms :label "Bedrooms" :type :number :min 0}
   {:id :bathrooms :label "Bathrooms" :type :decimal :step 0.5}
   {:id :sqft :label "Square Feet" :type :number :min 0}
   {:id :status :label "Status" :type :select :value "available"
    :options [{:value "available" :label "Available"}
              {:value "rented" :label "Rented"}
              {:value "maintenance" :label "Under Maintenance"}]}]
 
 :queries {
   :list "SELECT * FROM properties ORDER BY city, address"
   :get "SELECT * FROM properties WHERE id = ?"}
 
 :actions {:new true :edit true :delete false}
 
 :subgrids [
   {:entity :rentals
    :foreign-key :property_id
    :title "Rental History"
    :icon "bi bi-file-text"}
   
   {:entity :maintenance_requests
    :foreign-key :property_id
    :title "Maintenance"
    :icon "bi bi-tools"}
   
   {:entity :appraisals
    :foreign-key :property_id
    :title "Appraisals"
    :icon "bi bi-graph-up"}]}
```

**Child:** `resources/entities/rentals.edn`

```clojure
{:entity :rentals
 :title "Rentals"
 :table "rentals"
 :connection :default
 :rights ["A" "S"]
 :mode :parameter-driven
 
 :fields [
   {:id :id :type :hidden}
   {:id :property_id :type :hidden}  ; Foreign key
   {:id :tenant_name :label "Tenant" :type :text :required true}
   {:id :start_date :label "Start Date" :type :date :required true}
   {:id :end_date :label "End Date" :type :date}
   {:id :monthly_rent :label "Monthly Rent" :type :decimal :min 0}
   {:id :deposit :label "Security Deposit" :type :decimal :min 0}
   {:id :active :label "Active" :type :checkbox :value "T"}]
 
 :queries {
   :list "SELECT * FROM rentals WHERE property_id = ? ORDER BY start_date DESC"
   :get "SELECT * FROM rentals WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}}
```

---

## File Structure Reference

```
project-root/
├── src/
│   └── app/
│       ├── core.clj              ; Main application entry
│       ├── engine/
│       │   ├── config.clj        ; Config loader
│       │   ├── crud.clj          ; CRUD operations
│       │   ├── query.clj         ; Query execution
│       │   ├── render.clj        ; UI rendering
│       │   ├── router.clj        ; Dynamic routing
│       │   └── scaffold.clj      ; Scaffolding engine
│       ├── hooks/
│       │   ├── users.clj         ; User entity hooks
│       │   ├── products.clj      ; Product hooks
│       │   └── orders.clj        ; Order hooks
│       ├── validators/
│       │   └── common.clj        ; Custom validators
│       └── queries/
│           └── reports.clj       ; Custom query functions
│
├── resources/
│   ├── entities/                 ; Entity EDN configs
│   │   ├── users.edn
│   │   ├── products.edn
│   │   └── orders.edn
│   ├── migrations/               ; Database migrations
│   │   └── 001-initial-schema.sql
│   ├── private/
│   │   └── config.clj            ; Database config
│   ├── public/                   ; Static assets
│   │   ├── css/
│   │   ├── js/
│   │   └── images/
│   └── i18n/                     ; Internationalization
│       └── en.edn
│
└── project.clj                   ; Leiningen project config
```

---

## Additional Resources

- **HOOKS_GUIDE.md** - Deep dive into hooks with examples
- **FRAMEWORK_GUIDE.md** - Complete framework documentation
- **QUICKSTART.md** - Get started in 5 minutes
- **QUICK_REFERENCE.md** - Quick command reference

---

**You're now ready to build powerful data-driven applications with WebGen/LST!**
