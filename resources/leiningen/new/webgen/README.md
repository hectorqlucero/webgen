# {{name}} - Parameter-Driven Enterprise Application

This project was generated using the WebGen framework template from Clojars.

## Quick Start

See [QUICKSTART.md](QUICKSTART.md) for detailed setup instructions.

### 1. Configure Database

Edit `resources/private/config.clj` with your database settings:

```bash
cd {{name}}
# Edit config.clj - update passwords and database names as needed
nano resources/private/config.clj
```

The file comes with sensible defaults using SQLite and your project name.

### 2. Setup Database
```bash
lein migrate       # Run database migrations
lein database      # Initialize database with default users
```

### 3. Start Server
```bash
lein with-profile dev run
# Visit: http://localhost:8080
# Default credentials: admin@example.com / admin
```

### 4. Create Your First Entity
```bash
lein scaffold products
# This creates resources/entities/products.edn and migrations
```

## Key Features

- ✅ **No Code Generation** - Pure configuration-driven
- ✅ **Hot Reload** - Change configs, refresh browser  
- ✅ **Multi-Database** - MySQL, PostgreSQL, SQLite support
- ✅ **Enterprise-Ready** - MRP, Accounting, Inventory, POS capable
- ✅ **Beginner-Friendly** - Non-programmers can create entities
- ✅ **Expert-Extensible** - Hooks, plugins, custom functions

## Supported Field Types

WebGen supports a comprehensive set of field types for building complex forms:

| Type | Description | Example | Common Options |
|------|-------------|---------|----------------|
| `:text` | Single-line text input | Name, SKU, code | `placeholder`, `required` |
| `:textarea` | Multi-line text input | Description, notes, comments | `rows`, `placeholder` |
| `:number` | Numeric integer input | Quantity, age, count | `min`, `max`, `placeholder` |
| `:decimal` | Decimal/float input | Price, percentage, weight | `min`, `max`, `step`, `placeholder` |
| `:email` | Email input with validation | Email address | `placeholder`, `required` |
| `:password` | Password input (masked) | Password field | `placeholder`, `required` |
| `:date` | Date picker | Birth date, expiry date | `min`, `max` |
| `:datetime` | Date and time picker | Created timestamp | `min`, `max` |
| `:time` | Time picker | Event time, opening hours | `min`, `max` |
| `:select` | Dropdown select | Category, status, type | `options` (array of `{:value :label}`) |
| `:radio` | Radio button group | Active/Inactive, type selection | `options` (array with `:id`, `:label`, `:value`), `value` |
| `:checkbox` | Single checkbox | Active, featured, enabled | `value` (default checked value) |
| `:file` | File upload | Image, PDF, attachment | Handled via hooks |
| `:hidden` | Hidden field | ID, foreign keys | `value` |
| `:computed` | Calculated/display only | Total, full name, age | Read-only, computed via hooks |

**Example using various field types:**

```clojure
{:entity :products
 :title "Products"
 :table "products"
 
 :fields [{:id :id :label "ID" :type :hidden}
          {:id :code :label "SKU" :type :text :required true :placeholder "SKU-001"}
          {:id :name :label "Name" :type :text :required true}
          {:id :description :label "Description" :type :textarea :rows 5}
          {:id :price :label "Price" :type :decimal :min 0 :step 0.01}
          {:id :stock :label "Stock" :type :number :min 0}
          {:id :category :label "Category" :type :select 
           :options [{:value "electronics" :label "Electronics"}
                     {:value "clothing" :label "Clothing"}]}
          {:id :active :label "Status" :type :radio :value "T"
           :options [{:id "activeT" :label "Active" :value "T"}
                     {:id "activeF" :label "Inactive" :value "F"}]}
          {:id :imagen :label "Image" :type :file}]}
```

## Documentation

- [QUICKSTART.md](QUICKSTART.md) - Get started quickly
- [FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md) - Complete framework guide
- [HOOKS_GUIDE.md](HOOKS_GUIDE.md) - Customization with hooks
- [QUICK_REFERENCE.md](QUICK_REFERENCE.md) - Command reference
- [DATABASE_MIGRATION_GUIDE.md](DATABASE_MIGRATION_GUIDE.md) - Database migrations
- [RUN_APP.md](RUN_APP.md) - Running and deployment

## License

MIT License - see LICENSE file for details.
# Port configurable in resources/private/config.clj
```

### **4. Create Your First Entity**

`resources/entities/products.edn`:
```clojure
{:entity :products
 :title "Products"
 :table "products"
 :fields [{:id :name :label "Name" :type :text :required? true}
          {:id :price :label "Price" :type :decimal :required? true}]
 :queries {:list "SELECT * FROM products"
           :get "SELECT * FROM products WHERE id = ?"}
 :actions {:new true :edit true :delete true}}
```

Visit `/admin/products` - **Full CRUD interface ready!**

---

## **Documentation:** **Documentation**

- 📖 **[FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md)** - Complete framework documentation
- **Quick Start:** **[QUICKSTART.md](QUICKSTART.md)** - 5-minute setup guide
- 🎣 **[HOOKS_GUIDE.md](HOOKS_GUIDE.md)** - Business logic hooks
- 📋 **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - Quick reference
- 🏃 **[RUN_APP.md](RUN_APP.md)** - Deployment guide

---

## **Architecture** **Architecture**

```
┌─────────────────────────────────────┐
│  EDN Configs (resources/entities/)  │
│  → users.edn                        │
│  → products.edn                     │
│  → customers.edn                    │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Engine (src/rs/engine/)            │
│  → config.clj   - Load configs      │
│  → query.clj    - Execute queries   │
│  → crud.clj     - CRUD operations   │
│  → render.clj   - UI rendering      │
│  → router.clj   - Dynamic routes    │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Generic Routes                     │
│  /admin/:entity                     │
│  /admin/:entity/add-form            │
│  /admin/:entity/save                │
└─────────────────────────────────────┘
```

---

## **Design:** **Example Configurations**

### **Simple CRUD**
```clojure
{:entity :customers
 :title "Customers"
 :table "customers"
 :fields [{:id :name :label "Name" :type :text :required? true}
          {:id :email :label "Email" :type :email}]
 :queries {:list "SELECT * FROM customers"
           :get "SELECT * FROM customers WHERE id = ?"}
 :actions {:new true :edit true :delete true}}
```

### **With Business Logic**
```clojure
{:entity :orders
 :title "Orders"
 :table "orders"
 :fields [...]
 :hooks {:before-save :orders/calculate-totals
         :after-save :orders/send-confirmation}}
```

### **With Subgrids**
```clojure
{:entity :customers
 :subgrids [{:entity :orders
             :foreign-key :customer_id}]}
```

---

## **Tools:** **Technology Stack**

- **Language:** Clojure 1.12.4
- **Web:** Ring, Compojure, Hiccup
- **Databases:** MySQL, PostgreSQL, SQLite
- **UI:** Bootstrap 5, DataTables
- **Migrations:** Ragtime

---

## 📊 **Use Cases**

Built for real enterprise scenarios:

- 💼 **MRP** - Material Requirements Planning
- 💰 **Accounting** - Double-entry bookkeeping
- **Package:** **Inventory** - Stock management
- 🛒 **Point of Sale** - Retail systems
- 👥 **CRM** - Customer relationship management
- 📋 **Order Management** - Complex workflows

---

## 🎓 **Learning Path**

1. **Beginners** → Read [QUICKSTART.md](QUICKSTART.md)
2. **Developers** → Read [FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md)
3. **Business Logic** → Read [HOOKS_GUIDE.md](HOOKS_GUIDE.md)
4. **Database Migration** → Read [DATABASE_MIGRATION_GUIDE.md](DATABASE_MIGRATION_GUIDE.md)
5. **Team Collaboration** → Read [COLLABORATION_GUIDE.md](COLLABORATION_GUIDE.md)

---

## 🤝 **Contributing**

Contributions welcome! Please read our contributing guidelines.

---

## **File:** **License**

MIT License - See [LICENSE](LICENSE) for details.

---

## **Highlights:** **Why RS Framework?**

### **Traditional Approach**
- 81 generated files
- Lost modifications
- 4 hours per entity
- Spaghetti code

### **RS Framework**
- 27 config files
- Never lose changes
- 10 minutes per entity
- Clean, maintainable code

**Built with care for developers who value clean code.**
