# Database Migration Guide

This guide explains how to work with database migrations in the RS framework, including converting SQLite migrations to MySQL/PostgreSQL and migrating data between databases.

## Overview

The RS framework uses [Ragtime](https://github.com/weavejester/ragtime) for schema migrations. During prototyping, you typically use SQLite for quick iteration. When moving to production, you'll need to convert those migrations to MySQL or PostgreSQL.

The framework provides two utilities to simplify this process:

1. **Migration Converter** - Converts SQLite migration files to MySQL/PostgreSQL syntax
2. **Data Migrator** - Copies data from SQLite to MySQL/PostgreSQL

---

## Migration File Structure

Migrations follow this naming pattern:
```
{number}-{name}.{database}.{direction}.sql
```

Examples:
- `001-users.sqlite.up.sql` - SQLite schema creation
- `001-users.mysql.up.sql` - MySQL schema creation
- `001-users.postgresql.down.sql` - PostgreSQL schema rollback

---

## Converting Migration Files

### Quick Start

Convert SQLite migrations to MySQL:
```bash
lein convert-migrations mysql
```

Convert to PostgreSQL:
```bash
lein convert-migrations postgresql
```

### What It Does

The converter:
1. Finds all `.sqlite.up.sql` files without target database equivalents
2. Converts SQL syntax (types, functions, keywords)
3. Creates both `.up.sql` and `.down.sql` files for the target database
4. Preserves migration numbers and names

### SQL Conversions

The converter automatically handles these transformations:

**SQLite → MySQL:**
- `INTEGER PRIMARY KEY AUTOINCREMENT` → `INT AUTO_INCREMENT PRIMARY KEY`
- `INTEGER AUTOINCREMENT` → `INT AUTO_INCREMENT`
- `INTEGER` → `INT`
- `TEXT` → `TEXT`
- `REAL` → `DOUBLE`
- `datetime('now')` → `CURRENT_TIMESTAMP`
- `date('now')` → `CURRENT_DATE`

**SQLite → PostgreSQL:**
- `INTEGER PRIMARY KEY AUTOINCREMENT` → `SERIAL PRIMARY KEY`
- `INTEGER AUTOINCREMENT` → `SERIAL`
- `INTEGER` → `INTEGER`
- `TEXT` → `TEXT`
- `REAL` → `DOUBLE PRECISION`
- `BLOB` → `BYTEA`
- `datetime('now')` → `CURRENT_TIMESTAMP`
- `date('now')` → `CURRENT_DATE`

### Example

**Before (SQLite):**
```sql
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT,
  created_at TEXT DEFAULT (datetime('now'))
);
```

**After (MySQL):**
```sql
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name TEXT,
  created_at TEXT DEFAULT (CURRENT_TIMESTAMP)
);
```

**After (PostgreSQL):**
```sql
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  name TEXT,
  created_at TEXT DEFAULT (CURRENT_TIMESTAMP)
);
```

### Manual Review

Always review generated migrations! The converter handles common patterns, but you may need to adjust:

- **VARCHAR lengths** - Consider replacing `TEXT` with `VARCHAR(255)` for indexed columns
- **Data types** - MySQL distinguishes `CHAR(1)` vs `VARCHAR(255)`, PostgreSQL has more types
- **Foreign keys** - Add explicit `ON DELETE` and `ON UPDATE` clauses
- **Indexes** - Add indexes that make sense for your query patterns
- **Constraints** - Add `NOT NULL`, `UNIQUE`, `CHECK` constraints as needed

Example refinements:
```sql
-- Generated (basic)
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username TEXT,
  email TEXT
);

-- Improved (production-ready)
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username),
  INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Migrating Data

### Quick Start

Copy all data from SQLite to MySQL:
```bash
lein copy-data mysql
```

Copy to PostgreSQL:
```bash
lein copy-data postgresql
```

Clear target tables before copying (destructive!):
```bash
lein copy-data mysql --clear
```

### Prerequisites

Before running data migration:

1. **Apply migrations to target database:**
   ```bash
   lein migrate
   ```

2. **Configure database connection** in `src/rs/db/migrator.clj`:
   ```clojure
   (def db-configs
     {:sqlite
      {:classname "org.sqlite.JDBC"
       :subprotocol "sqlite"
       :subname "db/rs.db"}
      
      :mysql
      {:classname "com.mysql.cj.jdbc.Driver"
       :subprotocol "mysql"
       :subname "//127.0.0.1:3306/rs"
       :user "root"
       :password "your_password"}  ; ← UPDATE THIS
      
      :postgresql
      {:classname "org.postgresql.Driver"
       :subprotocol "postgresql"
       :subname "//localhost:5432/rs"
       :user "postgres"
       :password "your_password"}})  ; ← UPDATE THIS
   ```

3. **Verify database servers are running:**
   ```bash
   # MySQL
   mysql -u root -p -e "SELECT 1"
   
   # PostgreSQL
   psql -U postgres -c "SELECT 1"
   ```

### What It Does

The data migrator:
1. Connects to both SQLite (source) and target database
2. Reads all table names from SQLite
3. For each table:
   - Checks if it exists in target database
   - Reads all rows from SQLite
   - Inserts rows into target database
4. Reports summary of copied/skipped/failed tables

### Output Example

```
=== Database Data Migrator ===
Source: SQLite (db/rs.db)
Target: mysql
Clear target tables: NO

• Database connections successful

Copying users... • 5 rows
Copying contactos... • 12 rows
Copying propiedades... • 23 rows
Copying alquileres... • 8 rows
Copying ragtime_migrations... ⊘ No data in source table

=== Summary ===
Tables processed: 10
  Success: 8
  Skipped: 2
  Errors: 0
Total rows copied: 156

• Migration complete!
```

### Handling Errors

Common issues:

**Connection Error:**
```
• Connection error: Access denied for user 'root'@'localhost'
```
→ Update password in `src/rs/db/migrator.clj`

**Table Doesn't Exist:**
```
Copying new_table... ⊘ Table doesn't exist in target database
```
→ Run `lein migrate` to create tables first

**Duplicate Key Error:**
```
Copying users... • Duplicate entry '1' for key 'PRIMARY'
```
→ Use `--clear` flag to empty tables first, or manually clear data

**Foreign Key Constraint:**
```
Copying orders... • Cannot add or update a child row
```
→ Tables are copied in alphabetical order; you may need to temporarily disable FK checks

---

## Complete Workflow

### From SQLite to MySQL (Production)

```bash
# 1. Develop in SQLite (create entity, migrations run automatically)
lein scaffold propiedades

# 2. Convert migrations to MySQL
lein convert-migrations mysql

# 3. Review and refine generated migrations
vim resources/migrations/003-propiedades.mysql.up.sql

# 4. Configure target database in config.clj
vim resources/private/config.clj
# Set: :main :mysql

# 5. Run migrations on MySQL
lein migrate

# 6. Copy data from SQLite to MySQL
lein copy-data mysql

# 7. Test application with MySQL
lein run

# 8. Verify everything works
# Open http://localhost:8080/propiedades
```

### Development Workflow

```bash
# Prototype in SQLite (fast, no setup)
lein scaffold clientes
lein scaffold documentos

# When ready for production:
lein convert-migrations mysql
lein migrate
lein copy-data mysql

# Switch config to MySQL
vim resources/private/config.clj
```

---

## Advanced Usage

### Custom Type Mappings

Edit `src/rs/db/converter.clj` to add custom conversions:

```clojure
(def type-mappings
  {:mysql
   {"INTEGER PRIMARY KEY AUTOINCREMENT" "INT AUTO_INCREMENT PRIMARY KEY"
    "INTEGER AUTOINCREMENT" "INT AUTO_INCREMENT"
    "INTEGER" "INT"
    "TEXT" "VARCHAR(255)"  ; ← Custom: prefer VARCHAR
    "REAL" "DECIMAL(10,2)" ; ← Custom: use DECIMAL for money
    ;; Add your own mappings here
    }})
```

### Selective Data Migration

To migrate specific tables only, edit `src/rs/db/migrator.clj`:

```clojure
(defn copy-all-data [& args]
  (let [tables ["users" "propiedades" "alquileres"]]  ; ← Only these tables
    ;; ... rest of function
```

### Manual Migration (SQL Dump)

Alternative approach using SQL dumps:

```bash
# SQLite to MySQL via dump
sqlite3 db/rs.db .dump > dump.sql
# Manually edit dump.sql to fix syntax
mysql -u root -p rs < dump.sql

# Or use automated tools
# pip install mysql2sqlite
# mysql2sqlite -f db/rs.db -d rs -u root -p password
```

---

## Configuration Reference

### Database Settings (config.clj)

```clojure
{:connections
  {:sqlite   {:classname   "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname     "db/rs.db"}
   
   :mysql    {:classname   "com.mysql.cj.jdbc.Driver"
              :subprotocol "mysql"
              :subname     "//127.0.0.1:3306/rs"
              :user        "root"
              :password    ""}
   
   :postgres {:classname   "org.postgresql.Driver"
              :subprotocol "postgresql"
              :subname     "//localhost:5432/rs"
              :user        "postgres"
              :password    ""}}
 
 ;; Which database to use by default
 :main :sqlite     ; or :mysql, :postgres
 :default :sqlite} ; or :mysql, :postgres
```

### Per-Entity Override

In `resources/entities/propiedades.edn`:

```clojure
{:table "propiedades"
 :connection :mysql  ; ← This entity uses MySQL, others use default
 :fields [...]}
```

---

## Troubleshooting

### Converter Issues

**"No migrations to convert"**
- All SQLite migrations already have target equivalents
- Delete existing `.mysql.up.sql` files if you want to regenerate

**Generated SQL doesn't work**
- Review and manually adjust the generated files
- Some SQLite features don't have direct equivalents
- Add database-specific optimizations (indexes, constraints)

### Migration Issues

**"Table doesn't exist"**
- Run `lein migrate` before `lein copy-data`
- Check that migrations were successfully applied

**"Connection refused"**
- Ensure database server is running
- Check host/port in connection string
- Verify firewall allows connections

**"Access denied"**
- Update username/password in `src/rs/db/migrator.clj`
- Grant proper permissions to database user

**"Duplicate key error"**
- Use `--clear` flag to empty target tables first
- Or manually: `DELETE FROM tablename;` in target database

---

## Best Practices

1. **Always version control migrations** - Commit both SQLite and MySQL/PostgreSQL versions
2. **Test conversions** - Review generated SQL before applying to production
3. **Use transactions** - The migrator doesn't use transactions; consider wrapping in one
4. **Backup first** - Always backup production database before data migration
5. **Verify counts** - Compare row counts between source and target
6. **Test thoroughly** - Run full application test suite after migration

---

## See Also

- [Ragtime Documentation](https://github.com/weavejester/ragtime)
- [QUICKSTART.md](QUICKSTART.md) - Initial setup guide
- [FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md) - Complete framework reference
- [config.clj](resources/private/config.clj) - Database configuration
