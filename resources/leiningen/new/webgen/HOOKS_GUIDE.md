# Lifecycle Hooks Guide - Complete Reference

## Overview

Lifecycle hooks are functions that execute at specific points in the CRUD lifecycle. They allow you to add custom business logic, validation, data transformation, and side effects without modifying the framework code.

---

## Hook Execution Flow

### When Loading Records (GET/LIST)

```
User Requests Data
       ↓
[before-load] ← Modify query params, add filters
       ↓
Execute Query
       ↓
[after-load] ← Transform results, add computed fields
       ↓
Return to User
```

### When Saving Records (CREATE/UPDATE)

```
User Submits Form
       ↓
[before-save] ← Validate, set defaults, transform data
       ↓
Save to Database
       ↓
[after-save] ← Send notifications, update related records
       ↓
Return Success/Error
```

### When Deleting Records

```
User Clicks Delete
       ↓
[before-delete] ← Check constraints, verify permissions
       ↓
Delete from Database
       ↓
[after-delete] ← Cleanup, cascade deletes, send notifications
       ↓
Return Success
```

---

## Hook Signatures & Examples

### 1. `before-load` Hook

**When:** Before loading list of records or a single record
**Purpose:** Modify query parameters, add filters, restrict access
**Signature:** `[params] → modified-params`

**Example Use Cases:**
- Filter records by user permissions
- Add default date range filters
- Log access attempts
- Add custom WHERE clauses

**Alquileres Example:**
```clojure
(defn before-load
  [params]
  (println "[INFO] Loading alquileres with params:" params)
  ;; In production, you might:
  ;; - Filter by user's assigned properties
  ;; - Add date range: only show active rentals
  ;; - Track who accessed the data
  params)  ;; Return modified or original params
```

**Test:**
1. Navigate to Alquileres list
2. Check server logs: `tail -f /tmp/rs-server.log | grep "Loading alquileres"`
3. Should see: `[INFO] Loading alquileres with params: {}`

---

### 2. `after-load` Hook

**When:** After loading records, before displaying
**Purpose:** Transform data, add computed fields, format values
**Signature:** `[rows params] → modified-rows`

**Example Use Cases:**
- Calculate computed fields (totals, durations, etc.)
- Format dates, currencies, percentages
- Enrich data with lookups
- Add virtual columns

**Alquileres Example:**
```clojure
(defn after-load
  [rows params]
  (println "[INFO] Loaded" (count rows) "alquileres")
  ;; Add computed fields to each row
  (mapv (fn [row]
          (assoc row
                 :total_inicial (compute-total-inicial row)
                 :duracion_meses (compute-duracion-meses row)))
        rows))
```

**Test:**
1. Navigate to Alquileres list
2. Check server logs: Should see `[INFO] Loaded N alquileres`
3. View grid: Should show computed fields (if configured to display)

---

### 3. `before-save` Hook

**When:** Before creating or updating a record
**Purpose:** Validation, set defaults, transform data
**Signature:** `[params] → modified-params OR {:errors {...}}`

**Example Use Cases:**
- Validate business rules
- Set default values
- Calculate derived fields
- Check permissions
- Prevent save with validation errors

**Alquileres Example:**
```clojure
(defn before-save
  [params]
  (let [errors (merge (validate-dates params)
                     (validate-montos params))]
    (if (empty? errors)
      ;; No errors - set defaults and allow save
      (cond-> params
        (nil? (:estado_alquiler params))
        (assoc :estado_alquiler "Activo"))
      ;; Has errors - prevent save
      {:errors errors})))
```

**Test Validation:**
1. Click "Add" on Alquileres
2. Enter fecha_inicio = "2026-06-01"
3. Enter fecha_fin = "2026-01-01" (BEFORE start date)
4. Try to save
5. **Expected:** Error message "End date must be after start date"

**Test Amount Validation:**
1. Enter monto_mensual = 0 or negative value
2. Try to save
3. **Expected:** Error message "Monthly amount must be positive"

**Test Default Values:**
1. Create new alquiler
2. Leave "Estado" field empty
3. Save successfully
4. Edit the record
5. **Expected:** Estado should be set to "Activo"

---

### 4. `after-save` Hook

**When:** After successfully saving a record
**Purpose:** Side effects, notifications, update related records
**Signature:** `[entity-id params] → {:success true}`

**Example Use Cases:**
- Send email notifications
- Update property availability status
- Create audit trail entries
- Trigger background jobs
- Update elasticsearch indexes

**Alquileres Example:**
```clojure
(defn after-save
  [entity-id params]
  (println "[INFO] Alquiler" entity-id "saved successfully")
  (println "[INFO] Property:" (:id_propiedad params))
  (println "[INFO] Tenant:" (:id_inquilino params))
  (println "[INFO] Monthly:" (:monto_mensual params))
  ;; In production you might:
  ;; - Send email to property owner
  ;; - Mark property as "Rented"
  ;; - Create first payment due record
  {:success true})
```

**Test:**
1. Create or edit an Alquiler
2. Save successfully
3. Check server logs: `tail -f /tmp/rs-server.log | grep "Alquiler"`
4. **Expected:** See log entries with property ID, tenant ID, monthly amount

---

### 5. `before-delete` Hook

**When:** Before deleting a record
**Purpose:** Validate deletion is allowed, check constraints
**Signature:** `[entity-id] → {:success true} OR {:errors {...}}`

**Example Use Cases:**
- Check for related records (payments, contracts)
- Verify user has permission to delete
- Prevent deletion if constraints exist
- Log deletion attempts

**Alquileres Example:**
```clojure
(defn before-delete
  [entity-id]
  (println "[INFO] Checking if alquiler" entity-id "can be deleted...")
  ;; In production you might:
  ;; - Check if there are related payments
  ;; - Check if contracts exist
  ;; - Verify property is not in legal dispute
  ;; For now, always allow
  {:success true})
```

**Test:**
1. Click "Delete" on any Alquiler
2. Confirm deletion
3. Check server logs: `tail -f /tmp/rs-server.log | grep "Checking if alquiler"`
4. **Expected:** See log message with the ID being deleted

---

### 6. `after-delete` Hook

**When:** After successfully deleting a record
**Purpose:** Cleanup, cascade operations, notifications
**Signature:** `[entity-id] → {:success true}`

**Example Use Cases:**
- Delete related files/documents
- Update property status to "Available"
- Send cancellation notifications
- Archive to backup table
- Update statistics

**Alquileres Example:**
```clojure
(defn after-delete
  [entity-id]
  (println "[INFO] Alquiler" entity-id "deleted successfully")
  ;; In production you might:
  ;; - Update property status to "Disponible"
  ;; - Send notification to property owner
  ;; - Archive rental documents
  {:success true})
```

**Test:**
1. Delete an Alquiler
2. Check server logs: `tail -f /tmp/rs-server.log | grep "deleted successfully"`
3. **Expected:** See confirmation message

---

## Helper Functions (Alquileres)

### Validators

```clojure
(defn validate-dates [params])
  ;; Returns {:fecha_fin "error message"} if invalid
  
(defn validate-montos [params])
  ;; Returns {:monto_mensual "error"} or {:deposito_garantia "error"}
```

### Computed Fields

```clojure
(defn compute-total-inicial [row])
  ;; Returns: deposito_garantia + primer_mes + ultimo_mes
  
(defn compute-duracion-meses [row])
  ;; Returns: months between fecha_inicio and fecha_fin
```

---

## Complete Test Checklist

### ✅ Test `before-load`
- [ ] Navigate to Alquileres list
- [ ] Check logs for: `[INFO] Loading alquileres with params:`

### ✅ Test `after-load`
- [ ] View Alquileres list
- [ ] Check logs for: `[INFO] Loaded N alquileres`
- [ ] Verify grid displays data correctly

### ✅ Test `before-save` - Date Validation
- [ ] Set fecha_inicio > fecha_fin
- [ ] Try to save
- [ ] See error: "End date must be after start date"

### ✅ Test `before-save` - Amount Validation
- [ ] Set monto_mensual = 0
- [ ] Try to save
- [ ] See error: "Monthly amount must be positive"

### ✅ Test `before-save` - Default Values
- [ ] Create new record without Estado
- [ ] Save
- [ ] Edit record
- [ ] Estado should be "Activo"

### ✅ Test `after-save`
- [ ] Save any Alquiler
- [ ] Check logs for: `[INFO] Alquiler X saved successfully`
- [ ] Check logs for property, tenant, monthly amount

### ✅ Test `before-delete`
- [ ] Click Delete on any row
- [ ] Check logs for: `[INFO] Checking if alquiler X can be deleted...`

### ✅ Test `after-delete`
- [ ] Confirm deletion
- [ ] Check logs for: `[INFO] Alquiler X deleted successfully`

---

## Viewing Server Logs

```bash
# View live logs
tail -f /tmp/rs-server.log

# Filter for hook messages
tail -f /tmp/rs-server.log | grep "\[INFO\]"

# Filter for specific hook
tail -f /tmp/rs-server.log | grep "saved successfully"
```

---

## Hook Return Values

| Hook | Success Return | Error Return |
|------|---------------|--------------|
| `before-load` | Modified params map | N/A (just return params) |
| `after-load` | Modified rows vector | N/A (just return rows) |
| `before-save` | Modified params map | `{:errors {:field "message"}}` |
| `after-save` | `{:success true}` | `{:success false :error "..."}` |
| `before-delete` | `{:success true}` | `{:errors {:general "Cannot delete"}}` |
| `after-delete` | `{:success true}` | `{:success false :error "..."}` |

---

## Advanced Example - Prevent Deletion

To actually prevent deletion based on business rules:

```clojure
(defn before-delete
  [entity-id]
  ;; Check if there are payments
  (let [payments (query-db "SELECT COUNT(*) FROM pagos_renta WHERE id_alquiler = ?" 
                          [entity-id])
        has-payments? (> (get-in payments [0 :count]) 0)]
    (if has-payments?
      ;; Prevent deletion
      {:errors {:general "Cannot delete rental with existing payments"}}
      ;; Allow deletion
      {:success true})))
```

---

## Production Checklist

When moving to production, enhance hooks to:

- [ ] Send real email notifications (after-save, after-delete)
- [ ] Update property status automatically (after-save, after-delete)
- [ ] Check for related records before delete (before-delete)
- [ ] Add audit logging (all hooks)
- [ ] Filter by user permissions (before-load)
- [ ] Validate against business rules (before-save)
- [ ] Create background jobs (after-save)
- [ ] Archive deleted data (after-delete)

---

## Current Status

**Alquileres Entity:**
- ✅ All 6 hooks implemented
- ✅ Date validation (fecha_inicio < fecha_fin)
- ✅ Amount validation (positive values)
- ✅ Default estado = "Activo"
- ✅ Computed fields (total_inicial, duracion_meses)
- ✅ Logging for all operations

**Other Entities:**
- Hooks NOT implemented (just use framework defaults)
- Can add hooks following the Alquileres pattern

The hooks demonstrate the full power of the framework - you can add any business logic without changing framework code! 
