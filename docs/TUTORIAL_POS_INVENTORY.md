# Tutorial: Building a Point of Sale Inventory System with WebGen

This tutorial walks you through building a complete inventory management application from scratch using WebGen. By the end you will have a working system with products, suppliers, inventory tracking, and stock movements that automatically update inventory when sales and purchases are recorded.

This tutorial is written for junior developers. Every step is explained in detail. No prior Clojure experience is required, but you should be comfortable using a terminal.

---

## What You Will Build

A Point of Sale inventory system with the following entities:

| Entity | Purpose |
|---|---|
| **Productos** | Product catalog with name, price, category, and image |
| **Provedores** | Supplier contact list |
| **Inventario** | Current stock levels per product and supplier |
| **Movimientos** | Sales and purchase records that automatically adjust inventory |

The key feature is that when you record a **compra** (purchase), inventory goes up. When you record a **venta** (sale), inventory goes down. This is handled automatically by a hook — you never touch the inventory table directly.

---

## Prerequisites

Install these before starting:

- Java 11 or later: `java -version`
- Leiningen: `lein -version` (install from https://leiningen.org/)

---

## Step 1 — Create the Project

Run this in your terminal:

```bash
lein new org.clojars.hector/webgen pos
cd pos
```

This creates a new directory called `pos` with the complete application structure.

---

## Step 2 — Understand the Project Structure

Open the `pos` directory. The most important folders are:

```
pos/
  resources/
    entities/       <-- You will work here most of the time
    migrations/     <-- Database schema files
    config/
      app-config.edn  <-- Database and app settings
  src/pos/
    hooks/          <-- Business logic goes here
```

Think of it this way: `entities/` files describe *what* your data looks like. `hooks/` files describe *what happens* when data changes.

---

## Step 3 — Remove the Example Files

WebGen generates a set of example files to demonstrate its features: a Contactos entity, a Cars entity, a Siblings entity, and their matching hooks and migrations. These are useful for exploration, but they will clutter the application menu. Remove them now. The audit log migration (006) is kept because it is part of the core system.

### Delete the example migrations

```bash
rm resources/migrations/003-contactos.sqlite.up.sql
rm resources/migrations/003-contactos.sqlite.down.sql
rm resources/migrations/004-siblings.sqlite.up.sql
rm resources/migrations/004-siblings.sqlite.down.sql
rm resources/migrations/005-cars.sqlite.up.sql
rm resources/migrations/005-cars.sqlite.down.sql
```

### Delete the example entities

```bash
rm resources/entities/contactos.edn
rm resources/entities/cars.edn
rm resources/entities/siblings.edn
```

### Delete the example hooks

```bash
rm src/pos/hooks/contactos.clj
rm src/pos/hooks/cars.clj
rm src/pos/hooks/siblings.clj
```

After these deletions the remaining migration files should be `001-users.*`, `002-users_view.*`, and `006-audit_log.*`. The only hook file remaining should be `users.clj`.

---

## Step 4 — Configure the Database

Open `resources/config/app-config.edn`. Find the connections section. The default database is SQLite, which stores everything in a single file and requires no setup. Leave it as-is for now.

The relevant settings look like this:

```clojure
:default :sqlite   ; which database entities use
:main    :sqlite   ; which database migrations use
```

When you are ready for production, you can change these to `:mysql` or `:postgres` and update the credentials.

Also verify the port is set to something you like:

```clojure
:port 3000
```

---

## Step 5 — Write the Database Migrations

Migrations are SQL files that create your database tables. WebGen runs them in order based on their number prefix.

### Migration 007 — Products table

Create the file `resources/migrations/007-productos.sqlite.up.sql`:

```sql
CREATE TABLE productos (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre   VARCHAR(255),
    precio   DECIMAL(10,2),
    categoria VARCHAR(255),
    imagen   VARCHAR(255)
);
```

Create the rollback file `resources/migrations/007-productos.sqlite.down.sql`:

```sql
DROP TABLE IF EXISTS productos;
```

### Migration 008 — Suppliers table

Create `resources/migrations/008-provedores.sqlite.up.sql`:

```sql
CREATE TABLE provedores (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre   VARCHAR(255),
    email    VARCHAR(255),
    telefono VARCHAR(255)
);
```

Create `resources/migrations/008-provedores.sqlite.down.sql`:

```sql
DROP TABLE IF EXISTS provedores;
```

### Migration 009 — Inventory table

Create `resources/migrations/009-inventario.sqlite.up.sql`:

```sql
CREATE TABLE inventario (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id          INT,
    cantidad             INT DEFAULT 0,
    provedor_id          INT,
    ultima_actualizacion DATE DEFAULT (date('now')),
    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE,
    FOREIGN KEY (provedor_id) REFERENCES provedores(id) ON DELETE SET NULL
);

CREATE INDEX idx_inventario_producto_id ON inventario(producto_id);
CREATE INDEX idx_inventario_provedor_id ON inventario(provedor_id);
```

Create `resources/migrations/009-inventario.sqlite.down.sql`:

```sql
DROP TABLE IF EXISTS inventario;
```

### Migration 010 — Stock movements table

Create `resources/migrations/010-movimientos.sqlite.up.sql`:

```sql
CREATE TABLE movimientos (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id      INT,
    tipo_movimiento  VARCHAR(10) NOT NULL CHECK (tipo_movimiento IN ('venta', 'compra')),
    fecha_movimiento DATE DEFAULT (date('now')),
    cantidad         INT NOT NULL,
    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE
);

CREATE INDEX idx_movimientos_producto_id ON movimientos(producto_id);
```

Create `resources/migrations/010-movimientos.sqlite.down.sql`:

```sql
DROP TABLE IF EXISTS movimientos;
```

---

## Step 6 — Run the Migrations

Now tell WebGen to apply all the migrations:

```bash
lein migrate
```

You should see output listing each migration being applied. If you see an error, double-check the SQL file for typos.

Then seed the default users:

```bash
lein database
```

This creates three default users for you to log in with.

---

## Step 7 — Start the Development Server

```bash
lein with-profile dev run
```

Wait for the message that the server started, then open your browser to `http://localhost:3000`.

Log in with `admin@example.com` and password `admin`.

You will see the default application with only the Users entity in the menu. In the next steps you will add your own entities.

---

## Step 8 — Create the Products Entity

Entity files tell WebGen what fields exist, how they look in the UI, and what queries to run. Create `resources/entities/productos.edn`:

```clojure
{:entity        :productos
 :title         "Productos"
 :table         "productos"
 :connection    :default
 :rights        ["U" "A" "S"]
 :mode          :parameter-driven
 :menu-category :Productos
 :menu-order    10

 :fields [{:id :id       :label "ID"        :type :hidden}
          {:id :nombre   :label "Nombre"    :type :text    :required? true :placeholder "Nombre del producto"}
          {:id :precio   :label "Precio"    :type :decimal :min 0 :step 0.01 :placeholder "0.00"}
          {:id :categoria :label "Categoría" :type :text   :placeholder "Electrónicos, Ropa, etc."}
          {:id :imagen   :label "Imagen"    :type :file}]

 :queries {:list "SELECT * FROM productos ORDER BY nombre"
           :get  "SELECT * FROM productos WHERE id = ?"}

 :actions {:new true :edit true :delete true}

 :hooks {:after-load  :pos.hooks.productos/after-load
         :before-save :pos.hooks.productos/before-save}

 :subgrids [{:entity      :inventario
             :title       "Inventario"
             :foreign-key :producto_id
             :icon        "bi bi-box-seam"}
            {:entity      :movimientos
             :title       "Movimientos"
             :foreign-key :producto_id
             :icon        "bi bi-arrow-left-right"}]}
```

**What each part does:**

- `:entity` — the unique name WebGen uses internally to identify this entity
- `:table` — the actual database table name
- `:menu-category` — the dropdown group it appears under in the navigation bar
- `:fields` — each field becomes a column in the data grid and an input in the form
- `:type :file` — renders a file input; the actual upload is handled by the hook
- `:subgrids` — when you click a product, you will see tabs for its inventory and movements
- `:hooks` — functions that run at specific moments (after loading data, before saving)

Now refresh your browser. "Productos" should appear in the menu.

---

## Step 9 — Create the Products Hook for Image Uploads

Hooks are Clojure functions. The products entity uses two hooks:

- `after-load` — converts the stored filename into an HTML `<img>` tag so the image shows in the grid
- `before-save` — tells the framework to move the uploaded file to the uploads folder

Create `src/pos/hooks/productos.clj`:

```clojure
(ns pos.hooks.productos
  (:require [pos.models.util :refer [image-link]]))

(defn after-load
  "Convert stored image filename to an HTML img tag for display."
  [rows params]
  (map #(assoc % :imagen (image-link (:imagen %))) rows))

(defn before-save
  "Move the uploaded image file and store only the filename."
  [params]
  (if-let [imagen-file (:imagen params)]
    (if (and (map? imagen-file) (:tempfile imagen-file))
      ;; The :file key signals the framework to process the upload
      (-> params
          (assoc :file imagen-file)
          (dissoc :imagen))
      params)
    params))
```

**What is `image-link`?** It is a utility function already provided by WebGen that builds an `<img>` HTML tag from a filename. You do not need to write it.

**What is `:tempfile`?** When a user uploads a file through an HTML form, Ring (the web library) puts the uploaded data in a map with a `:tempfile` key pointing to the temporary file on disk. The `before-save` hook checks for this and signals WebGen to move that file to the uploads folder.

---

## Step 10 — Create the Suppliers Entity

Create `resources/entities/provedores.edn`:

```clojure
{:entity        :provedores
 :title         "Provedores"
 :table         "provedores"
 :connection    :default
 :rights        ["U" "A" "S"]
 :mode          :parameter-driven
 :menu-category :Provedores
 :menu-order    20

 :fields [{:id :id       :label "ID"       :type :hidden}
          {:id :nombre   :label "Nombre"   :type :text  :required? true :placeholder "Nombre del proveedor"}
          {:id :email    :label "Email"    :type :email :placeholder "contacto@empresa.com"}
          {:id :telefono :label "Teléfono" :type :text  :placeholder "555-1234"}]

 :queries {:list "SELECT * FROM provedores ORDER BY nombre"
           :get  "SELECT * FROM provedores WHERE id = ?"}

 :actions {:new true :edit true :delete true}

 :subgrids [{:entity      :inventario
             :title       "Inventario"
             :foreign-key :provedor_id
             :icon        "bi bi-box-seam"}]}
```

Refresh the browser. "Provedores" appears in the menu.

---

## Step 11 — Create the Inventory Entity

Inventory is a child of both Products and Suppliers. It is hidden from the main menu because it is only accessed through the Productos or Provedores subgrid tabs. You do not add inventory records directly — the Movimientos hook does it automatically. But you can view and correct them here.

Create `resources/entities/inventario.edn`:

```clojure
{:entity        :inventario
 :title         "Inventario"
 :table         "inventario"
 :connection    :default
 :rights        ["A" "S"]         ; Only admins can manage inventory directly
 :mode          :parameter-driven
 :menu-hidden?  true              ; Does not appear in the main menu

 :fields [{:id :id :label "ID" :type :hidden}

          ;; FK to provedores — hidden in grid, shown in form
          {:id           :provedor_id
           :label        "Proveedor"
           :type         :fk
           :fk           :provedores
           :fk-field     [:nombre]
           :fk-can-create? true
           :hidden-in-grid? true}
          {:id :provedor_nombre :label "Proveedor" :grid-only? true}

          ;; FK to productos — hidden in grid, shown in form
          {:id           :producto_id
           :label        "Producto"
           :type         :fk
           :fk           :productos
           :fk-field     [:nombre :precio]
           :fk-can-create? true
           :hidden-in-grid? true}
          {:id :producto_nombre :label "Producto" :grid-only? true}

          {:id :cantidad :label "Cantidad" :type :number :min 0}]

 :queries {:list "SELECT
                    inv.*,
                    pro.nombre  AS producto_nombre,
                    pro1.nombre AS provedor_nombre
                  FROM inventario inv
                  LEFT JOIN productos  pro  ON inv.producto_id = pro.id
                  LEFT JOIN provedores pro1 ON inv.provedor_id = pro1.id
                  ORDER BY inv.id DESC"

           :get  "SELECT
                    inv.*,
                    pro.nombre  AS producto_nombre,
                    pro1.nombre AS provedor_nombre
                  FROM inventario inv
                  LEFT JOIN productos  pro  ON inv.producto_id = pro.id
                  LEFT JOIN provedores pro1 ON inv.provedor_id = pro1.id
                  WHERE inv.id = ?"}

 :actions {:new true :edit true :delete true}}
```

**Understanding FK fields:**

A `:type :fk` field renders as a searchable dropdown populated from another table. The `:fk` key is the entity keyword (not the table name — though they are the same here). `:fk-field` lists which columns from the foreign table to show in the dropdown.

The pattern of having two fields — one FK field hidden in the grid and one `grid-only` text field showing the joined name — is the standard way to display foreign key data cleanly.

**Understanding the query:**

The `:list` query joins three tables to fetch the product name and supplier name alongside the inventory record. The columns aliased as `producto_nombre` and `provedor_nombre` match the `:id` values of the `grid-only?` fields above.

---

## Step 12 — Create the Stock Movements Entity

Movements are also a child entity (accessed through the Productos subgrid). This is where all the business logic lives. When a movement is saved, the hook automatically updates the inventory.

Create `resources/entities/movimientos.edn`:

```clojure
{:entity        :movimientos
 :title         "Movimientos"
 :table         "movimientos"
 :connection    :default
 :rights        ["U" "A" "S"]
 :mode          :parameter-driven
 :menu-hidden?  true              ; Only accessed via the Productos subgrid

 :fields [{:id :id :label "ID" :type :hidden}

          {:id           :producto_id
           :label        "Producto"
           :type         :fk
           :fk           :productos
           :fk-field     [:nombre]
           :fk-can-create? true
           :hidden-in-grid? true}
          {:id :producto_nombre :label "Producto" :grid-only? true}

          {:id      :tipo_movimiento
           :label   "Tipo"
           :type    :select
           :required? true
           :options [{:value "compra" :label "Compra"}
                     {:value "venta"  :label "Venta"}]}

          {:id :cantidad :label "Cantidad" :type :number :required? true :min 1}]

 :queries {:list "SELECT
                    mov.*,
                    pro.nombre AS producto_nombre
                  FROM movimientos mov
                  LEFT JOIN productos pro ON mov.producto_id = pro.id
                  ORDER BY mov.id DESC"

           :get  "SELECT
                    mov.*,
                    pro.nombre AS producto_nombre
                  FROM movimientos mov
                  LEFT JOIN productos pro ON mov.producto_id = pro.id
                  WHERE mov.id = ?"}

 :actions {:new true :edit true :delete true}

 :hooks {:after-save    :pos.hooks.movimientos/after-save
         :before-delete :pos.hooks.movimientos/before-delete
         :after-delete  :pos.hooks.movimientos/after-delete}}
```

---

## Step 13 — Create the Movements Hook (the core business logic)

This hook is the heart of the application. It does three things:

1. **`after-save`** — After a movement is saved, it finds the matching inventory record for that product and adjusts the quantity. If no inventory record exists yet, it creates one.
2. **`before-delete`** — Before deleting a movement, it saves the movement's data so `after-delete` can use it.
3. **`after-delete`** — After a movement is deleted, it reverses the original inventory adjustment.

Create `src/pos/hooks/movimientos.clj`:

```clojure
(ns pos.hooks.movimientos
  (:require [pos.models.crud :refer [Query Update Insert]]))

;; This atom temporarily stores movement data before deletion
;; so that after-delete can reverse the inventory adjustment.
;; An atom is a thread-safe mutable container in Clojure.
(def ^:private pending-delete-data (atom {}))

(defn after-save
  "After a movement is saved, update the inventory for the product.
   - compra (purchase) increases stock
   - venta  (sale)     decreases stock
   If no inventory record exists for the product, create one.
   Optionally accepts a db connection/transaction as the first argument
   so inventory updates can share an open transaction."
  ([data result] (after-save nil data result))
  ([conn data _result]
   (try
     (let [producto-id     (:producto_id data)
           tipo-movimiento (:tipo_movimiento data)
           cantidad        (if (string? (:cantidad data))
                             (parse-long (:cantidad data))
                             (:cantidad data))
           ;; Purchases add to stock, sales subtract
           adjustment      (if (= tipo-movimiento "compra")
                             cantidad
                             (- cantidad))
           today           (java.sql.Date/valueOf (java.time.LocalDate/now))]
       (when producto-id
         (if conn
           ;; --- inside an explicit transaction ---
           (let [existing-inv (first (Query conn ["SELECT * FROM inventario WHERE producto_id = ?" producto-id]))]
             (if existing-inv
               (Update conn :inventario
                       {:cantidad             (+ (:cantidad existing-inv) adjustment)
                        :ultima_actualizacion today}
                       ["id = ?" (:id existing-inv)])
               (Insert conn :inventario
                       {:producto_id          producto-id
                        :cantidad             adjustment
                        :ultima_actualizacion today})))
           ;; --- no explicit connection: use global db ---
           (let [existing-inv (first (Query ["SELECT * FROM inventario WHERE producto_id = ?" producto-id]))]
             (if existing-inv
               (Update :inventario
                       {:cantidad             (+ (:cantidad existing-inv) adjustment)
                        :ultima_actualizacion today}
                       ["id = ?" (:id existing-inv)])
               (Insert :inventario
                       {:producto_id          producto-id
                        :cantidad             adjustment
                        :ultima_actualizacion today}))))))
     (catch Exception e
       (println "[ERROR] after-save failed:" (.getMessage e))))
   {:success true}))

(defn before-delete
  "Save the movement record before it is deleted so after-delete can reverse it."
  [{:keys [id]}]
  (try
    (when id
      (let [record (first (Query ["SELECT * FROM movimientos WHERE id = ?" id]))]
        (when record
          ;; Store the record in the atom, keyed by the ID as a string
          (swap! pending-delete-data assoc (str id) record))))
    (catch Exception e
      (println "[ERROR] before-delete failed:" (.getMessage e))))
  {:success true})

(defn after-delete
  "After a movement is deleted, reverse the inventory adjustment it made."
  [{:keys [id]} _result]
  (try
    (let [record (get @pending-delete-data (str id))]
      (when record
        ;; Remove the stored record from the atom
        (swap! pending-delete-data dissoc (str id))
        (let [producto-id     (:producto_id record)
              tipo-movimiento (:tipo_movimiento record)
              cantidad        (if (string? (:cantidad record))
                                (parse-long (:cantidad record))
                                (:cantidad record))
              ;; Reverse the original adjustment:
              ;; if it was a compra, subtract; if it was a venta, add back
              adjustment      (if (= tipo-movimiento "compra")
                                (- cantidad)
                                cantidad)]
          (when producto-id
            (let [existing-inv (first (Query ["SELECT * FROM inventario WHERE producto_id = ?" producto-id]))]
              (when existing-inv
                (Update :inventario
                        {:cantidad            (+ (:cantidad existing-inv) adjustment)
                         :ultima_actualizacion (java.sql.Date/valueOf (java.time.LocalDate/now))}
                        ["id = ?" (:id existing-inv)])))))))
    (catch Exception e
      (println "[ERROR] after-delete failed:" (.getMessage e))))
  {:success true})
```

**Understanding the `atom`:**

An atom is a way to store a value that can change over time in a thread-safe way. Here it acts as a short-lived scratchpad: `before-delete` writes a record into it, `after-delete` reads it and then removes it.

**Understanding `Query`, `Update`, `Insert`:**

These are three functions provided by WebGen's `crud` namespace. They run SQL against the database:
- `Query` takes a vector `[sql & params]` and returns rows as a vector of maps
- `Update` takes a table keyword, a map of new values, and a `WHERE` clause
- `Insert` takes a table keyword and a map of values to insert

---

## Step 14 — Test the Application

### Add products

1. Click **Productos** in the menu
2. Click **New**
3. Enter a name, price, and category. Upload an image if you want.
4. Save. Repeat for a few products.

### Add suppliers

1. Click **Provedores** in the menu
2. Add two or three suppliers

### Record a purchase

1. Go back to **Productos**
2. Click the row for one of your products (not the edit button — the row itself)
3. You will see two tabs: **Inventario** and **Movimientos**
4. Click the **Movimientos** tab
5. Click **New**
6. Set Type to **Compra**, Quantity to **100**
7. Save

Now click the **Inventario** tab. You should see a new record with `cantidad = 100`.
1. Click on Edit button to edit the **Inventario**
2. Add a new **Provedor** on the select box. If no **Provedor** exists you can create a new one by clicking on the + button to the right of the select

### Record a sale

1. Still on the same product, go to the **Movimientos** tab
2. Add a new movement: Type **Venta**, Quantity **30**
3. Save

Check **Inventario** again. The quantity should now be **70**.

### Delete a movement

1. In the Movimientos tab, delete the sale you just created
2. Check Inventario — quantity should return to **100**

The `before-delete` and `after-delete` hooks handled the reversal automatically.

---

## Step 15 — Understanding What You Built

Here is a summary of how the parts connect:

```
User adds a Movimiento (sale/purchase)
    │
    ▼
before-save (if any validation needed)
    │
    ▼
Framework saves the row to the movimientos table
    │
    ▼
after-save hook runs:
  - Reads the producto_id and cantidad from the saved row
  - Finds the matching inventario row (or creates one)
  - Adjusts cantidad up (compra) or down (venta)
    │
    ▼
Inventario reflects the new stock level
```

When a movement is deleted:

```
User clicks Delete on a Movimiento
    │
    ▼
before-delete hook runs:
  - Reads the movement from the database
  - Saves it in the atom
    │
    ▼
Framework deletes the row
    │
    ▼
after-delete hook runs:
  - Reads the saved movement from the atom
  - Reverses the inventory adjustment
  - Removes it from the atom
```

---

## Step 16 — Build the Custom POS Register Screen

Everything you have built so far uses the entity system: EDN config files drive the CRUD interface automatically. But a real point of sale register is different. A cashier needs a touch-friendly product grid, a running cart, a payment calculator, and a receipt — not a CRUD form.

WebGen lets you write custom pages alongside the entity system. The pattern is the classic MVC split: `model.clj` talks to the database, `view.clj` generates HTML, `controller.clj` connects routes to the model and view. This is exactly the same pattern as the `handlers/home/` directory that ships with every WebGen project.

### Migration 011 — Sales tables

You need two tables: `ventas` for the sale header (date, total, payment, change) and `ventas_detalle` for the individual line items.

Create `resources/migrations/011-ventas.sqlite.up.sql`:

```sql
CREATE TABLE ventas (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha     DATETIME DEFAULT CURRENT_TIMESTAMP,
    total     DECIMAL(12,2) NOT NULL DEFAULT 0,
    pago      DECIMAL(12,2) NOT NULL DEFAULT 0,
    cambio    DECIMAL(12,2) NOT NULL DEFAULT 0,
    usuario_id INT,
    estado    VARCHAR(20) NOT NULL DEFAULT 'completada'
              CHECK (estado IN ('completada', 'cancelada')),
    FOREIGN KEY (usuario_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_ventas_usuario_id ON ventas(usuario_id);

CREATE TABLE ventas_detalle (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_id        INT NOT NULL,
    producto_id     INT NOT NULL,
    cantidad        INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal        DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (venta_id)    REFERENCES ventas(id)    ON DELETE CASCADE,
    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE
);

CREATE INDEX idx_ventas_detalle_venta_id    ON ventas_detalle(venta_id);
CREATE INDEX idx_ventas_detalle_producto_id ON ventas_detalle(producto_id);
```

Create `resources/migrations/011-ventas.sqlite.down.sql`:

```sql
DROP TABLE IF EXISTS ventas_detalle;
DROP TABLE IF EXISTS ventas;
```

Run migrations again to apply:

```bash
lein migrate
```

### i18n keys

Add the POS keys to `resources/i18n/en.edn` (before the closing `}`):

```clojure
;; [POS screen]
:pos/title          "Point of Sale"
:pos/public-sale    "Public Sale"
:pos/search-product "Search product..."
:pos/best-sellers   "Best Sellers"
:pos/sale-details   "Sale Details"
:pos/empty-cart     "Cart is empty"
:pos/total          "Total:"
:pos/payment        "Payment:"
:pos/change         "Change:"
:pos/register-sale  "Register Sale"
:pos/print-receipt  "Print Receipt"
:pos/clear          "Clear"
```

Add the Spanish equivalents to `resources/i18n/es.edn`:

```clojure
;; [Pantalla POS]
:pos/title          "Punto de Venta"
:pos/public-sale    "Venta al Público"
:pos/search-product "Buscar producto..."
:pos/best-sellers   "Productos"
:pos/sale-details   "Detalle de Venta"
:pos/empty-cart     "Carrito vacío"
:pos/total          "Total:"
:pos/payment        "Efectivo:"
:pos/change         "Cambio:"
:pos/register-sale  "Registrar Venta"
:pos/print-receipt  "Imprimir Recibo"
:pos/clear          "Limpiar"
```

### Register the routes

Open `src/pos/routes/proutes.clj`. This file has your custom routes (separate from the automatic entity routes). Add the POS routes:

```clojure
(ns pos.routes.proutes
  (:require [compojure.core :refer [defroutes GET POST]]
            [pos.handlers.pos.controller :as pos]))

(defroutes proutes
  (GET  "/pos"              request (pos/pos request))
  (GET  "/api/pos/search"   request (pos/api-search request))
  (POST "/api/pos/register" request (pos/api-register-sale request)))
```

The framework picks up `proutes` automatically and adds these to the application's route table.

### Update the menu
Open `src/pos/menu.clj`. This file has your custom menus (separate from autogen menus). Add the POS menu:

```
(def custom-nav-links
  "Custom navigation links (non-dropdown, not entity-based)"
  [["/" "HOME" nil 0]
   ["/pos" "PUNTA DE VENTA" nil 10]])
```

### Model — database queries and the sale transaction

Create `src/pos/handlers/pos/model.clj`:

```clojure
(ns pos.handlers.pos.model
  (:require [clojure.java.jdbc :as jdbc]
            [pos.hooks.movimientos :as mov-hooks]
            [pos.models.crud :refer [db Query Insert]]))

(defn get-productos
  "Fetch all products with their current inventory stock level."
  []
  (Query db [(str "SELECT p.id, p.nombre, p.precio, p.categoria, p.imagen,"
                  " COALESCE(i.cantidad, 0) as stock"
                  " FROM productos p"
                  " LEFT JOIN inventario i ON i.producto_id = p.id"
                  " ORDER BY p.nombre")]))

(defn search-productos
  "Search products by name or category."
  [term]
  (let [like-term (str "%" term "%")]
    (Query db [(str "SELECT p.id, p.nombre, p.precio, p.categoria, p.imagen,"
                    " COALESCE(i.cantidad, 0) as stock"
                    " FROM productos p"
                    " LEFT JOIN inventario i ON i.producto_id = p.id"
                    " WHERE p.nombre LIKE ? OR p.categoria LIKE ?"
                    " ORDER BY p.nombre")
               like-term like-term])))

(defn register-sale-tx!
  "Registers a complete sale inside a single database transaction.
   Inserts the sale header, one detail row per item, and one movimiento
   per item (which triggers the inventory hook).
   Returns the new venta id."
  [venta-header items]
  (jdbc/with-db-transaction [tx db]
    (let [venta-result (first (Insert tx :ventas venta-header))
          venta-id     (or (:generated_key venta-result)
                           ((keyword "last_insert_rowid()") venta-result)
                           (:last_insert_rowid venta-result)
                           (:id venta-result)
                           (first (vals venta-result)))]
      (when (nil? venta-id)
        (throw (ex-info "Could not determine venta id after insert" {:result venta-result})))
      (doseq [item items]
        ;; Insert the detail line
        (Insert tx :ventas_detalle
                {:venta_id        venta-id
                 :producto_id     (:producto_id item)
                 :cantidad        (:cantidad item)
                 :precio_unitario (:precio item)
                 :subtotal        (* (:cantidad item) (:precio item))})
        ;; Insert a movimiento (type = venta) and fire the hook to reduce stock
        (let [mov {:producto_id     (:producto_id item)
                   :tipo_movimiento "venta"
                   :cantidad        (:cantidad item)}
              result (Insert tx :movimientos mov)]
          (mov-hooks/after-save tx mov result)))
      venta-id)))
```

**Key points:**

- `jdbc/with-db-transaction` wraps all inserts in one atomic operation. If any insert fails, everything is rolled back and no partial data is saved.
- After inserting each `movimiento`, `mov-hooks/after-save` is called directly. This is the same hook from Step 12 — reused here in the transaction context so stock is decreased as part of the same transaction.
- `COALESCE(i.cantidad, 0)` in the product queries means products with no inventory record show `stock = 0` instead of `null`.

### View — the HTML interface

The view is written in Hiccup, which is Clojure's way of writing HTML as data. Every HTML tag becomes a Clojure vector: `[:div.card "content"]` produces `<div class="card">content</div>`.

Create `src/pos/handlers/pos/view.clj`:

```clojure
(ns pos.handlers.pos.view
  (:require [pos.i18n.core :as i18n]
            [clojure.data.json :as json]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn- product-image [product]
  (if (not-empty (:imagen product))
    [:img {:src   (str "/uploads/" (:imagen product))
           :alt   (:nombre product)
           :style "max-height: 80px; max-width: 100%; object-fit: contain;"}]
    [:i.bi.bi-box-seam {:style "font-size: 3rem; color: #6c757d;"}]))

(defn- product-card [product]
  [:div.col-6.col-md-4.col-xl-3.pos-product-card
   {:data-id     (:id product)
    :data-nombre (:nombre product)
    :data-precio (str (:precio product))
    :data-stock  (str (:stock product))}
   [:div.card.h-100.border.pos-card-clickable
    {:role    "button"
     :onclick (str "POS.addItem(" (:id product) ")")}
    [:div.card-body.text-center.p-2
     [:div.pos-product-img.mb-2 (product-image product)]
     [:p.card-text.fw-semibold.mb-1.text-truncate (:nombre product)]
     [:span.badge.bg-success.fs-6 (str "$" (:precio product))]]]])

(defn- products-panel [request productos]
  [:div.col-lg-8
   [:div.card.shadow-sm.border-0
    [:div.card-body
     [:div.mb-3
      [:button.btn.btn-primary.btn-lg {:type "button"}
       [:i.bi.bi-person-fill.me-2]
       (i18n/tr request :pos/public-sale)]]
     [:div.mb-3
      [:div.input-group.input-group-lg
       [:span.input-group-text [:i.bi.bi-search]]
       [:input#pos-search.form-control
        {:type         "text"
         :placeholder  (i18n/tr request :pos/search-product)
         :autocomplete "off"}]]]
     [:h5.fw-bold.mb-3 (i18n/tr request :pos/best-sellers)]
     [:div#pos-product-grid.row.g-3
      (for [p productos] (product-card p))]
     [:div.mt-3
      [:button.btn.btn-outline-secondary
       {:type    "button"
        :onclick "POS.clearCart()"}
       [:i.bi.bi-pencil-square.me-2]
       (i18n/tr request :pos/clear)]]]]])

(defn- sale-details-panel [request csrf-token]
  [:div.col-lg-4
   [:div.card.shadow-sm.border-0
    [:div.card-header.bg-light
     [:h5.fw-bold.mb-0 (i18n/tr request :pos/sale-details)]]
    [:div.card-body
     [:div#pos-cart-items
      [:p.text-muted.text-center (i18n/tr request :pos/empty-cart)]]
     [:hr]
     [:div.d-flex.justify-content-between.align-items-center.mb-3
      [:span.fw-bold.fs-5 (i18n/tr request :pos/total)]
      [:span#pos-total.fw-bold.fs-4 "$0.00"]]
     [:div.mb-3
      [:div.d-flex.justify-content-between.align-items-center
       [:label.fw-semibold (i18n/tr request :pos/payment)]
       [:input#pos-payment.form-control.text-end
        {:type        "number"
         :step        "0.01"
         :min         "0"
         :style       "max-width: 150px;"
         :placeholder "0.00"
         :oninput     "POS.calcChange()"}]]]
     [:div.d-flex.justify-content-between.align-items-center.mb-4
      [:label.fw-semibold (i18n/tr request :pos/change)]
      [:span#pos-change.fs-5 "0.00"]]
     [:button#pos-register-btn.btn.btn-success.btn-lg.w-100.mb-3
      {:type     "button"
       :onclick  "POS.registerSale()"
       :disabled "disabled"}
      (i18n/tr request :pos/register-sale)]
     [:div.text-center
      [:a#pos-print-btn.text-decoration-none
       {:href    "#"
        :onclick "POS.printReceipt(); return false;"
        :style   "display:none;"}
       [:i.bi.bi-printer.me-2]
       (i18n/tr request :pos/print-receipt)]]]]
   ;; Hidden CSRF token consumed by the JavaScript fetch call
   [:div {:style "display:none;"} csrf-token]])

(defn pos-view
  "Render the full POS interface."
  [request productos]
  (let [csrf-token (anti-forgery-field)]
    (list
     [:link {:rel "stylesheet" :href "/css/pos.css?v=1"}]
     ;; Embed product data as JSON in a data attribute so JavaScript can read it
     [:div#pos-app {:data-productos (json/write-str productos)}
      [:div.row.g-3
       (products-panel request productos)
       (sale-details-panel request csrf-token)]]
     [:script {:src "/js/pos.js?v=1"}])))
```

**Why embed data in a `data-` attribute?**

Instead of making the JavaScript fetch the product list via an API call on page load, the controller fetches it once from the database and embeds it as JSON in the HTML. This means the page works without an extra network round-trip and the product cards are fully rendered server-side (so they are visible even if JavaScript is slow to load).

### Controller — Ring request handlers

Create `src/pos/handlers/pos/controller.clj`:

```clojure
(ns pos.handlers.pos.controller
  (:require [pos.handlers.pos.model   :as model]
            [pos.handlers.pos.view    :as view]
            [pos.layout               :refer [application]]
            [pos.i18n.core            :as i18n]
            [pos.models.util          :refer [get-session-id]]
            [clojure.data.json        :as json]))

(defn pos
  "Serve the main POS page."
  [request]
  (let [title    (i18n/tr request :pos/title)
        ok       (get-session-id request)
        productos (model/get-productos)
        content  (view/pos-view request productos)]
    (application request title ok nil content)))

(defn api-search
  "JSON API: search products by name or category."
  [request]
  (let [term    (get-in request [:params :q] "")
        results (if (empty? term)
                  (model/get-productos)
                  (model/search-productos term))]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (json/write-str {:ok true :data results})}))

(defn api-register-sale
  "JSON API: receive a cart from the browser and save the complete sale."
  [request]
  (try
    (let [body    (json/read-str (slurp (:body request)) :key-fn keyword)
          items   (:items body)
          pago    (or (:pago body) 0)
          user-id (get-in request [:session :user_id])]
      (if (empty? items)
        {:status  400
         :headers {"Content-Type" "application/json"}
         :body    (json/write-str {:ok false :error "No items in cart"})}
        (let [total    (reduce + 0 (map #(* (:cantidad %) (:precio %)) items))
              cambio   (- (double pago) (double total))
              venta-id (model/register-sale-tx!
                        {:total      total
                         :pago       pago
                         :cambio     (max cambio 0)
                         :usuario_id user-id}
                        items)]
          {:status  200
           :headers {"Content-Type" "application/json"}
           :body    (json/write-str {:ok      true
                                     :venta_id venta-id
                                     :total    total
                                     :cambio   (max cambio 0)})})))
    (catch Exception e
      (println "[ERROR] POS register-sale failed:" (.getMessage e))
      {:status  500
       :headers {"Content-Type" "application/json"}
       :body    (json/write-str {:ok false :error (.getMessage e)})})))
```

**The CSRF token:** The view embeds a hidden CSRF token in the page (via `anti-forgery-field`). The JavaScript reads it and sends it as the `X-CSRF-Token` header. The framework validates it before the `POST /api/pos/register` handler runs, blocking cross-site request forgery attempts.

### CSS — the POS stylesheet

Create `resources/public/css/pos.css`:

```css
/* Product cards */
.pos-card-clickable {
    cursor: pointer;
    transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.pos-card-clickable:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}
.pos-product-img {
    height: 80px;
    display: flex;
    align-items: center;
    justify-content: center;
}

/* Cart rows */
.pos-cart-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem 0;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}
.pos-cart-row:last-child { border-bottom: none; }
.pos-cart-name  { flex: 1; font-weight: 500; }
.pos-cart-qty   { width: 80px; text-align: center; font-weight: 700; }
.pos-cart-price { width: 90px; text-align: right; font-weight: 500; }
.pos-cart-remove {
    cursor: pointer;
    color: #dc3545;
    margin-left: 0.5rem;
    font-size: 1.1rem;
}
.pos-cart-remove:hover { color: #a71d2a; }

.pos-qty-btn {
    width: 28px;
    height: 28px;
    padding: 0;
    font-size: 0.85rem;
    border-radius: 50%;
}

/* Hide filtered cards */
.pos-product-card.hidden { display: none !important; }

/* Sale success flash */
.pos-sale-success { animation: posFlash 0.6s ease; }
@keyframes posFlash {
    0%   { background-color: #d4edda; }
    100% { background-color: transparent; }
}
```

### JavaScript — the cart engine

Create `resources/public/js/pos.js`. The script is wrapped in a self-executing function (IIFE) so its variables do not pollute the global scope. The only global it exposes is the `POS` object.

```javascript
var POS = (function () {
    'use strict';

    var cart = [];
    var lastSale = null;
    var allProducts = [];

    // Read products embedded as JSON in the data attribute
    function getProductData() {
        var el = document.getElementById('pos-app');
        if (!el) return [];
        try { return JSON.parse(el.getAttribute('data-productos') || '[]'); }
        catch (e) { return []; }
    }

    function init() {
        allProducts = getProductData();
        // Live search: show/hide cards as the user types
        var input = document.getElementById('pos-search');
        if (input) {
            input.addEventListener('input', function () {
                var term = this.value.toLowerCase().trim();
                document.querySelectorAll('.pos-product-card').forEach(function (card) {
                    var name = (card.getAttribute('data-nombre') || '').toLowerCase();
                    card.classList.toggle('hidden', !(!term || name.indexOf(term) !== -1));
                });
            });
        }
    }

    // --- Cart helpers ---
    function findProduct(id) {
        for (var i = 0; i < allProducts.length; i++) {
            if (allProducts[i].id == id) return allProducts[i];
        }
        return null;
    }

    function findCartItem(id) {
        for (var i = 0; i < cart.length; i++) {
            if (cart[i].producto_id == id) return i;
        }
        return -1;
    }

    function addItem(productId) {
        var product = findProduct(productId);
        if (!product) return;
        var idx = findCartItem(productId);
        if (idx >= 0) {
            cart[idx].cantidad++;
        } else {
            cart.push({
                producto_id: product.id,
                nombre:      product.nombre,
                precio:      parseFloat(product.precio) || 0,
                cantidad:    1,
                stock:       parseInt(product.stock) || 0
            });
        }
        renderCart();
    }

    function removeItem(productId) {
        var idx = findCartItem(productId);
        if (idx >= 0) { cart.splice(idx, 1); renderCart(); }
    }

    function updateQty(productId, delta) {
        var idx = findCartItem(productId);
        if (idx < 0) return;
        cart[idx].cantidad += delta;
        if (cart[idx].cantidad <= 0) cart.splice(idx, 1);
        renderCart();
    }

    function getTotal() {
        return cart.reduce(function (sum, item) {
            return sum + item.cantidad * item.precio;
        }, 0);
    }

    // --- Render the cart panel ---
    function renderCart() {
        var container  = document.getElementById('pos-cart-items');
        var totalEl    = document.getElementById('pos-total');
        var registerBtn = document.getElementById('pos-register-btn');

        if (cart.length === 0) {
            container.innerHTML = '<p class="text-muted text-center">Carrito vacío</p>';
            totalEl.textContent = '$0.00';
            registerBtn.disabled = true;
            calcChange();
            return;
        }

        var html = '';
        cart.forEach(function (item) {
            var subtotal = (item.cantidad * item.precio).toFixed(2);
            html += '<div class="pos-cart-row">';
            html += '<span class="pos-cart-name">' + escapeHtml(item.nombre) + '</span>';
            html += '<span class="pos-cart-qty">';
            html += '<button class="btn btn-sm btn-outline-secondary pos-qty-btn"'
                  + ' onclick="POS.updateQty(' + item.producto_id + ', -1)">-</button>';
            html += '<strong class="mx-1">' + item.cantidad + '</strong>';
            html += '<button class="btn btn-sm btn-outline-secondary pos-qty-btn"'
                  + ' onclick="POS.updateQty(' + item.producto_id + ', 1)">+</button>';
            html += '</span>';
            html += '<span class="pos-cart-price">$' + subtotal + '</span>';
            html += '<span class="pos-cart-remove"'
                  + ' onclick="POS.removeItem(' + item.producto_id + ')">'
                  + '<i class="bi bi-x-circle"></i></span>';
            html += '</div>';
        });

        container.innerHTML = html;
        totalEl.textContent = '$' + getTotal().toFixed(2);
        registerBtn.disabled = false;
        calcChange();
    }

    function calcChange() {
        var total  = getTotal();
        var pago   = parseFloat(document.getElementById('pos-payment').value) || 0;
        var cambio = pago - total;
        document.getElementById('pos-change').textContent =
            cambio >= 0 ? cambio.toFixed(2) : '0.00';
    }

    function clearCart() {
        cart = [];
        lastSale = null;
        document.getElementById('pos-payment').value = '';
        document.getElementById('pos-change').textContent = '0.00';
        document.getElementById('pos-print-btn').style.display = 'none';
        renderCart();
    }

    // --- Register the sale via fetch ---
    function registerSale() {
        if (cart.length === 0) return;

        var total = getTotal();
        var pago  = parseFloat(document.getElementById('pos-payment').value) || 0;
        if (pago < total) {
            alert('El pago debe ser mayor o igual al total.');
            return;
        }

        // Read the CSRF token injected by anti-forgery-field
        var csrfInput = document.querySelector('input[name="__anti-forgery-token"]');
        var csrfToken = csrfInput ? csrfInput.value : '';

        var payload = {
            items: cart.map(function (item) {
                return { producto_id: item.producto_id,
                         cantidad:    item.cantidad,
                         precio:      item.precio };
            }),
            pago: pago
        };

        var btn = document.getElementById('pos-register-btn');
        btn.disabled    = true;
        btn.textContent = 'Registrando...';

        fetch('/api/pos/register', {
            method:  'POST',
            headers: {
                'Content-Type':     'application/json',
                'X-CSRF-Token':     csrfToken,
                'x-requested-with': 'XMLHttpRequest'
            },
            body: JSON.stringify(payload)
        })
        .then(function (resp) { return resp.json(); })
        .then(function (data) {
            if (data.ok) {
                lastSale = { venta_id: data.venta_id, items: cart.slice(),
                             total: data.total, pago: pago,
                             cambio: data.cambio,
                             fecha: new Date().toLocaleString() };
                alert('Venta registrada #' + data.venta_id);
                document.getElementById('pos-print-btn').style.display = '';
                cart = [];
                document.getElementById('pos-payment').value = '';
                renderCart();
            } else {
                alert('Error: ' + (data.error || 'Error desconocido'));
            }
        })
        .catch(function (err) {
            alert('Error de red: ' + err.message);
        })
        .finally(function () {
            btn.disabled    = false;
            btn.textContent = 'Registrar Venta';
        });
    }

    // --- Print a receipt in a new window ---
    function printReceipt() {
        if (!lastSale) { alert('No hay venta para imprimir.'); return; }
        var s = lastSale;
        var w = window.open('', '_blank', 'width=400,height=600');
        if (!w) { alert('El bloqueador de ventanas emergentes impidió abrir el recibo.'); return; }
        var rows = s.items.map(function (it) {
            return '<tr><td>' + escapeHtml(it.nombre) + '</td>'
                 + '<td style="text-align:right">' + it.cantidad + '</td>'
                 + '<td style="text-align:right">$' + (it.cantidad * it.precio).toFixed(2) + '</td></tr>';
        }).join('');
        w.document.write(
            '<html><head><title>Recibo #' + s.venta_id + '</title>'
          + '<style>body{font-family:monospace;font-size:12px;width:300px;margin:20px auto}'
          + 'table{width:100%}.line{border-top:1px dashed #000;margin:8px 0}</style></head><body>'
          + '<h3 style="text-align:center">Recibo de Venta</h3>'
          + '<p style="text-align:center">Venta #' + s.venta_id + '<br>' + s.fecha + '</p>'
          + '<div class="line"></div>'
          + '<table><tr><th>Producto</th><th>Cant</th><th>Total</th></tr>' + rows + '</table>'
          + '<div class="line"></div>'
          + '<table>'
          + '<tr><td>Total:</td><td style="text-align:right">$' + s.total.toFixed(2) + '</td></tr>'
          + '<tr><td>Pago:</td><td style="text-align:right">$' + s.pago.toFixed(2) + '</td></tr>'
          + '<tr><td>Cambio:</td><td style="text-align:right">$' + s.cambio.toFixed(2) + '</td></tr>'
          + '</table>'
          + '<div class="line"></div>'
          + '<p style="text-align:center">¡Gracias por su compra!</p>'
          + '</body></html>'
        );
        w.document.close();
        w.focus();
        w.print();
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    return { addItem: addItem, removeItem: removeItem, updateQty: updateQty,
             clearCart: clearCart, calcChange: calcChange,
             registerSale: registerSale, printReceipt: printReceipt };
})();
```

### Test the POS screen

1. Restart the server: `lein with-profile dev run`
2. Navigate to `http://localhost:3000/pos`
3. You should see the product grid on the left and the cart panel on the right
4. Click a product card — it appears in the cart
5. Click the same card again — quantity increments
6. Use + / - buttons to adjust quantity
7. Type an amount in the Payment field — Change calculates automatically
8. Click **Register Sale** — the server saves the venta, detail rows, and fires the inventory hook for each item
9. Click **Print Receipt** to open the printable receipt in a new window

Verify the inventory was reduced: go to **Productos**, click a product row, open the **Inventario** tab. The quantity should be lower by the amount sold.

---

## Step 17 — Optional Improvements

These are enhancements you can try on your own, each building on what you have already learned.

### Show low stock as a visual warning

In `productos.edn`, add an `after-load` hook. In the hook, query the existing `inventario` table for each product and attach the quantity as a computed field. **No migration is needed** — `:stock_actual` is a virtual field that exists only in memory; it is never stored in the database.

```clojure
;; In the entity, add to :fields:
{:id :stock_actual :label "Stock" :type :number :grid-only? true}

;; In the hook namespace:
(defn after-load [rows params]
  (map (fn [row]
         (let [inv (first (Query ["SELECT cantidad FROM inventario WHERE producto_id = ?" (:id row)]))]
           (assoc row :stock_actual (or (:cantidad inv) 0))))
       rows))
```

The framework calls `after-load` after fetching the product rows and before rendering the grid. The hook adds `:stock_actual` to each row map on the fly, and the entity field definition tells the grid to display it. The value comes from the `inventario` table that already exists.

### Restrict who can delete movements

Only administrators should be able to delete a movement because it changes inventory. Change the `:actions` in `movimientos.edn` to hide the Edit button — movements should only be created or deleted, not edited in place:

```clojure
:actions {:new true :edit false :delete true}
```

To also hide the Delete button from regular users, change `:rights` so only admins and system users can access the entity at all:

```clojure
:rights ["A" "S"]
```

If you need finer control — for example, letting users view movements but only letting admins delete them — that requires a custom controller rather than a hook, because hooks do not have access to the session user.

### Move to a production database (MySQL or PostgreSQL)

When you are ready to move off SQLite to a real database server, the framework provides tools to convert the migration files and copy all data across.

**Step 1 — Convert the SQLite migrations to the target format:**

```bash
lein convert-migrations mysql
# or
lein convert-migrations postgresql
```

This reads every `*.sqlite.up.sql` and `*.sqlite.down.sql` file in `resources/migrations/` and writes equivalent `*.mysql.up.sql` / `*.postgresql.up.sql` files. Review the generated files before continuing — the converter handles the common cases but complex SQLite-specific SQL may need manual adjustment.

**Step 2 — Point the migrator at the target database:**

Open `app-config.edn` and change the `:main` key (used by `lein migrate`) to the target database connection:

```clojure
:main :mysql      ; or :postgres for PostgreSQL
```

Make sure the credentials for `:mysql` or `:postgres` in `:connections` are correct.

**Step 3 — Apply the migrations to the new database:**

```bash
lein migrate
```

**Step 4 — Copy all data from SQLite to the new database:**

```bash
lein copy-data localdb mysql        # SQLite → MySQL
# or
lein copy-data localdb postgres     # SQLite → PostgreSQL
```

The first argument is the **source** and the second is the **target**. `localdb` is the alias for the SQLite database defined in `app-config.edn`.

**Step 5 — Switch the application to use the new database:**

In `app-config.edn`, change `:default` (used by the running application) to match:

```clojure
:default :mysql   ; or :postgres
```

Restart the server and verify everything works before decommissioning the SQLite file.

---

## Common Errors and Fixes

**"Entity configuration not found :movimientos"**

The entity file does not exist or has a typo in the `:entity` key. Check `resources/entities/movimientos.edn` and make sure `:entity :movimientos` exactly matches.

**Function in hook not found (arity error)**

The namespace declaration in your hook file does not match what the entity config expects. If the entity says `:pos.hooks.movimientos/after-save`, the hook file must declare `(ns pos.hooks.movimientos ...)`.

**Inventory not updating**

Check the server terminal for `[ERROR]` lines from the hook. The most common cause is that `producto_id` is arriving as a string (`"5"`) instead of a number. The hook in Step 12 handles this with `parse-long`, so make sure you copied it exactly.

**Image not showing**

The `after-load` hook in `productos.clj` must require `image-link` from `pos.models.util`. If that function does not exist, the hook silently fails. Check that the require path matches your project namespace (replace `pos` with your actual project name if you named it differently).

**Port already in use**

```bash
lsof -i :3000
kill -9 <PID>
```

**Full reset (start from scratch)**

```bash
lein clean
rm db/pos.sqlite
lein migrate
lein database
lein with-profile dev run
```
