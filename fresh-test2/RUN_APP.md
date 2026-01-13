# Running Your Scaffolded Application

## 🎉 Success! Your app is ready to run

The scaffolding engine detected **15 database tables** and generated complete CRUD interfaces for:

- ✅ **agentes** (10 fields) - Real estate agents
- ✅ **alquileres** (22 fields) - Rentals
- ✅ **avaluos** (20 fields) - Property appraisals
- ✅ **bitacora** (9 fields) - Activity log
- ✅ **clientes** (39 fields) - Clients
- ✅ **comisiones** (9 fields) - Commissions
- ✅ **contratos** (3 fields) - Contracts
- ✅ **documentos** (17 fields) - Documents
- ✅ **fiadores** (15 fields) - Guarantors
- ✅ **pagos_renta** (12 fields) - Rental payments
- ✅ **pagos_ventas** (12 fields) - Sales payments
- ✅ **propiedades** (40 fields) - Properties
- ✅ **tramites** (16 fields) - Transactions
- ✅ **users** (existing) - System users
- ✅ **ventas** (22 fields) - Sales

---

## 🚀 Start the Server

### Step 1: Start the Application

#### Option 1: Development Mode with Auto-Reload (RECOMMENDED)
```bash
cd /home/hector/Downloads/rs
lein with-profile dev run
```
**This uses `wrap-reload` to automatically reload code changes without restarting!**
- Changes to `.clj` files reload instantly
- Changes to entity `.edn` configs reload on next request
- No server restart needed during development

#### Option 2: Production Mode
```bash
cd /home/hector/Downloads/rs
lein run
```
Runs without auto-reload (faster, for production).

#### Option 3: Ring Server (Alternative Dev Mode)
```bash
cd /home/hector/Downloads/rs
lein ring server
```
Opens browser automatically with auto-reload.

#### Option 4: Build and Run JAR (Production)
```bash
cd /home/hector/Downloads/rs
lein uberjar
java -jar target/uberjar/rs.jar
```

### Step 2: Login to the System

**The application requires authentication!**

1. Visit: **http://localhost:8080/home/login**
2. Enter your credentials (check your `users` table in database)
3. Click "Login"

**First Time Setup?**
If you don't have a user account, create one in the database:
```sql
INSERT INTO users (username, password, level, active)
VALUES ('admin@example.com',
        -- Password hash for "admin123" (use buddy.hashers/derive)
        'bcrypt+sha512$...',
        'S',  -- System admin level
        'T'); -- Active
```

Or use the existing seeder/migration scripts if available.

### Step 3: Access the Menu

After login, you'll see the main navigation with dropdown menus for all entities!

---

## 📊 Access Your Admin Panels

Once the server is running, you can immediately access any entity:

### Main Entities (Real Estate Management)
- **Clients**: http://localhost:8080/admin/clientes
- **Properties**: http://localhost:8080/admin/propiedades
- **Agents**: http://localhost:8080/admin/agentes
- **Rentals**: http://localhost:8080/admin/alquileres
- **Sales**: http://localhost:8080/admin/ventas

### Financial Management
- **Rental Payments**: http://localhost:8080/admin/pagos_renta
- **Sales Payments**: http://localhost:8080/admin/pagos_ventas
- **Commissions**: http://localhost:8080/admin/comisiones

### Documents & Processes
- **Documents**: http://localhost:8080/admin/documentos
- **Contracts**: http://localhost:8080/admin/contratos
- **Transactions**: http://localhost:8080/admin/tramites
- **Appraisals**: http://localhost:8080/admin/avaluos

### Other
- **Guarantors**: http://localhost:8080/admin/fiadores
- **Activity Log**: http://localhost:8080/admin/bitacora
- **Users**: http://localhost:8080/admin/users

---

## 🎨 What You Get Out of the Box

### Each Entity Has:
- ✅ **Data Grid** - Sortable, searchable, paginated table with DataTables
- ✅ **Add Form** - All fields with proper types (text, email, date, textarea, etc.)
- ✅ **Edit Form** - Pre-populated with existing data
- ✅ **Delete** - With confirmation
- ✅ **CSV Export** - Download data as spreadsheet
- ✅ **PDF Export** - Generate reports
- ✅ **Excel Export** - Full data export

### Smart Field Detection:
- 📧 Email fields → type `:email` with validation
- 📞 Phone fields → type `:text` with proper labels
- 📅 Date fields → type `:date` with date picker
- 💰 Decimal fields → type `:decimal` for money
- 🔢 Number fields → type `:number` for integers
- 📝 Long text → type `:textarea` for descriptions
- 🔑 Primary keys → type `:hidden` (auto-populated)

---

## 🛠️ Customization

All entity configurations are in `resources/entities/`

### Quick Edits:
```bash
# Edit any entity config
nano resources/entities/clientes.edn
# or
vim resources/entities/propiedades.edn
```

### Common Customizations:

1. **Change field types** (e.g., text → select dropdown)
2. **Add validators** (e.g., email format, required fields)
3. **Add computed fields** (e.g., total = quantity * price)
4. **Add hooks** (e.g., before-save to calculate values)
5. **Add subgrids** (e.g., show related records)
6. **Adjust permissions** (U=User, A=Admin, S=System)

See `FRAMEWORK_GUIDE.md` for detailed examples.

---

## 📝 Configuration Files

All auto-generated configs are ready to use but can be customized:

```
resources/entities/
├── agentes.edn         (2.1 KB)
├── alquileres.edn      (3.2 KB)
├── avaluos.edn         (3.1 KB)
├── bitacora.edn        (2.0 KB)
├── clientes.edn        (5.0 KB) ← Large! 39 fields
├── comisiones.edn      (1.9 KB)
├── contratos.edn       (1.5 KB)
├── documentos.edn      (2.8 KB)
├── fiadores.edn        (2.6 KB)
├── pagos_renta.edn     (2.3 KB)
├── pagos_ventas.edn    (2.3 KB)
├── propiedades.edn     (4.6 KB) ← Large! 40 fields
├── tramites.edn        (2.6 KB)
├── users.edn           (2.3 KB)
└── ventas.edn          (3.1 KB)
```

Each file contains:
- ✅ All database fields detected
- ✅ Proper field types assigned
- ✅ Default queries (list-all, get-by-id, insert, update, delete)
- ✅ TODO comments for customization
- ✅ Labels humanized (first_name → "First Name")

---

## 🔄 Hot Reload

The framework supports hot-reload in multiple ways!

### Code Auto-Reload (Development)
```bash
# Start with dev profile for automatic code reloading
lein with-profile dev run
```

**What reloads automatically:**
- ✅ **Clojure code** (`.clj` files) - Instant reload via `wrap-reload`
- ✅ **Entity configs** (`.edn` files) - Reload on next page load
- ✅ **Menu structure** - Regenerates automatically
- ✅ **Routes** - Dynamic routes update instantly

**No restart needed for:**
- Editing entity configurations
- Adding new fields
- Changing queries
- Modifying hooks
- Updating validators
- Adding new entities (just scaffold and refresh page)

### Manual Refresh (Production)
If running without auto-reload, restart the server to see changes:
```bash
# Stop server (Ctrl+C)
# Start again
lein run
```

---

## 🧪 Testing

### Test a Single Entity:
```bash
# Start server
lein run

# In browser:
# 1. Go to http://localhost:8080/admin/clientes
# 2. Click "Add" button
# 3. Fill form and save
# 4. Verify it appears in the grid
# 5. Click "Edit" and modify
# 6. Click "Delete" and confirm
```

### Test All Entities:
Just visit each URL listed above - all 15 entities work the same way!

---

## 📚 Documentation

Full documentation available:

- **QUICKSTART.md** - 5-minute setup guide
- **FRAMEWORK_GUIDE.md** - Complete API reference (19 KB)
- **MIGRATION_GUIDE.md** - Upgrade from v1 to v2
- **ADVANCED_EXAMPLES.md** - Real-world scenarios (MRP, Accounting, etc.)

---

## 🐛 Troubleshooting

### Server won't start?
```bash
# Check Java version (need 8+)
java -version

# Clear and rebuild
lein clean
lein compile

# Start with dev profile for auto-reload
lein with-profile dev run
```

### Entity not showing?
```bash
# Verify config file exists
ls resources/entities/clientes.edn

# Check for syntax errors
lein check
```

### Permission denied?
Edit the `:rights` field in the entity config:
```clojure
:rights ["U" "A" "S"]  ;; All user levels
:rights ["A" "S"]      ;; Admin and System only
```

---

## 🎯 What's Next?

### Immediate Actions:
1. **Start the server**: `lein with-profile dev run` (with auto-reload)
2. **Login**: Visit http://localhost:8080/home/login
3. **Test an entity**: Visit http://localhost:8080/admin/clientes
4. **Add some data**: Click "Add" and fill the form
5. **Customize**: Edit `resources/entities/clientes.edn` - changes reload automatically!

### Advanced Features:
- Add **subgrids** (show related records)
- Add **computed fields** (auto-calculate values)
- Add **validation hooks** (custom business rules)
- Add **custom actions** (export to specific format, send email, etc.)
- Add **custom queries** (complex reports)

See **ADVANCED_EXAMPLES.md** for detailed tutorials.

---

## 💡 Pro Tips

1. **Start with one entity** - Master `clientes` before customizing others
2. **Use hot-reload** - Edit configs without restarting
3. **Copy examples** - The `users.edn` file has good examples
4. **Read TODO comments** - Each generated file has hints for improvement
5. **Keep it simple** - 90% of cases work fine with defaults

---

## ✅ Summary

You now have:
- ✅ 15 fully functional CRUD interfaces
- ✅ All database tables scaffolded
- ✅ Smart field type detection
- ✅ Ready-to-use forms and grids
- ✅ Export to CSV, PDF, Excel
- ✅ Hot-reload for quick iterations

**Just run `lein run` and visit http://localhost:8080/admin/clientes to see it in action!**

---

Need help? Check the documentation or edit the entity configs to customize behavior.
