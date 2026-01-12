# Collaboration Guide - Junior & Senior Developer Workflow

## Overview

This guide explains how junior and senior developers work together using the RS Framework. The framework is designed for a **progressive handoff** where juniors create functional prototypes (80%) and seniors add business logic (20%).

---

## 🎯 **The 80/20 Rule**

- **Junior Developers:** Create 80% functional prototype using scaffolding and EDN configuration
- **Senior Developers:** Add 20% business logic through hooks and custom code

**Key Principle:** Junior work is **never discarded**. Senior developers enhance, not replace.

---

## 👨‍💻 **Junior Developer Workflow**

### Phase 1: Database Setup (5 minutes)

1. **Create Migration**
   ```sql
   -- resources/migrations/003-products.sqlite.up.sql
   CREATE TABLE products (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     name TEXT NOT NULL,
     price REAL DEFAULT 0.0,
     stock INTEGER DEFAULT 0
   );
   ```

2. **Apply Migration**
   ```bash
   lein migrate
   ```

### Phase 2: Scaffold Entity (30 seconds)

```bash
lein scaffold products
```

**What Gets Generated:**
- ✅ `resources/entities/products.edn` - Entity configuration
- ✅ `src/rs/hooks/products.clj` - Hook stub file (for senior)
- ✅ Auto-detected fields, foreign keys, subgrids
- ✅ Working CRUD interface at `/admin/products`

### Phase 3: Customize Configuration (15 minutes)

Edit `resources/entities/products.edn`:

```clojure
{:entity :products
 :title "Products"  ; ← Customize this
 :fields [
   {:id :id :label "ID" :type :hidden}
   {:id :name :label "Product Name" :type :text :required? true}  ; ← Mark required
   {:id :price :label "Price" :type :decimal :required? true}
   {:id :stock :label "Stock" :type :number}
   {:id :active :label "Active" :type :checkbox}]  ; ← Change field type
 
 :queries {:list "SELECT * FROM products WHERE active = 'T'"}  ; ← Filter inactive
 :actions {:new true :edit true :delete true}}
```

**What Juniors Can Do:**
- ✅ Change field labels (English/Spanish)
- ✅ Mark required fields (`:required? true`)
- ✅ Change field types (text, date, select, etc.)
- ✅ Hide fields in grid (`:hidden-in-grid? true`)
- ✅ Add placeholders
- ✅ Write simple SQL queries
- ✅ Enable/disable actions (new, edit, delete)

**What Juniors CANNOT Do:**
- ❌ Complex validation logic
- ❌ Computed fields (calculations)
- ❌ Business rules (workflows)
- ❌ External integrations (email, APIs)

### Phase 4: Test & Document (10 minutes)

1. **Test CRUD Operations**
   - Visit: `http://localhost:8080/admin/products`
   - Create new product
   - Edit existing product
   - Delete product
   - Verify grid displays correctly

2. **Document Business Rules**
   ```clojure
   ;; In products.edn, add comments:
   
   ;; BUSINESS RULES FOR SENIOR DEVELOPER:
   ;;   - Price must be positive
   ;;   - Stock cannot be negative
   ;;   - Name must be unique
   ;;   - Send email when stock < 10
   ;;   - Update inventory on save
   ```

### Junior Checklist ✅

- [ ] Migration created and applied
- [ ] Entity scaffolded
- [ ] Field labels customized
- [ ] Required fields marked
- [ ] Queries tested
- [ ] CRUD operations work
- [ ] Business rules documented in comments
- [ ] Ready for senior developer handoff

---

## 👔 **Senior Developer Workflow**

### Phase 1: Review (5 minutes)

1. **Check Entity Configuration**
   - Open `resources/entities/products.edn`
   - Read junior's comments about business rules
   - Verify field definitions

2. **Test Current Functionality**
   - Visit `/admin/products`
   - Verify basic CRUD works
   - Identify what needs enhancement

### Phase 2: Implement Hooks (30-60 minutes)

Edit `src/rs/hooks/products.clj`:

```clojure
(ns rs.hooks.products
  "Business logic for products")

;; Validation
(defn validate-price [params]
  (when (and (:price params) (<= (:price params) 0))
    {:price "Price must be positive"}))

(defn validate-stock [params]
  (when (and (:stock params) (< (:stock params) 0))
    {:stock "Stock cannot be negative"}))

;; Lifecycle Hooks
(defn before-save [params]
  "Validates data before saving"
  (let [errors (merge (validate-price params)
                     (validate-stock params))]
    (if (empty? errors)
      params
      {:errors errors})))

(defn after-save [entity-id params]
  "Sends notification if stock is low"
  (when (< (:stock params) 10)
    (send-email "manager@company.com"
                "Low Stock Alert"
                (str "Product " (:name params) " has low stock: " (:stock params))))
  {:success true})
```

### Phase 3: Enable Hooks (2 minutes)

Edit `resources/entities/products.edn` - **Uncomment the hooks section**:

```clojure
{:entity :products
 ;; ... fields, queries, etc ...
 
 ;; UNCOMMENT THIS:
 :hooks {:before-save :rs.hooks.products/before-save
         :after-save :rs.hooks.products/after-save}}
```

### Phase 4: Test (10 minutes)

1. **Test Validation**
   - Try to save product with negative price → Should show error
   - Try to save product with negative stock → Should show error

2. **Test Side Effects**
   - Save product with stock < 10 → Check if email sent
   - Verify logs show hook execution

### Senior Checklist ✅

- [ ] Reviewed junior's work
- [ ] Understood business rules
- [ ] Implemented validation hooks
- [ ] Added side effects (email, etc.)
- [ ] Uncommented :hooks in EDN
- [ ] Tested all validation rules
- [ ] Tested side effects
- [ ] Documented hook functions

---

## 🔄 **Handoff Protocol**

### When Junior is Done:

1. **Create a Handoff Document** (or use comments in EDN):
   ```
   Entity: products
   Status: Ready for senior review
   
   Business Rules:
   - Price must be positive
   - Stock cannot be negative
   - Send email when stock < 10
   
   Edge Cases:
   - What happens when stock is exactly 0?
   - Should we allow negative stock for returns?
   
   Questions:
   - Do we need to track price history?
   - Should low stock alert go to multiple people?
   ```

2. **Notify Senior** (Slack, email, ticket, etc.)

### When Senior Receives Handoff:

1. **Review & Test** basic functionality
2. **Ask Questions** if business rules are unclear
3. **Implement Hooks** in `src/rs/hooks/`
4. **Uncomment :hooks** in entity EDN
5. **Test Thoroughly**
6. **Document** any changes or decisions
7. **Notify Junior** when complete

---

## 📋 **File Organization**

```
resources/
  entities/
    products.edn       ← Junior edits this
    
src/
  rs/
    hooks/
      products.clj     ← Senior edits this
```

**Key Point:** These are **separate files**. Junior and senior work in parallel without conflicts!

---

## 🎯 **Best Practices**

### For Juniors:
1. **Document everything** - Write business rules as comments
2. **Test thoroughly** - Make sure basic CRUD works before handoff
3. **Ask questions** - If business rule is unclear, ask before implementing
4. **Keep it simple** - Don't try to implement complex validation in EDN
5. **Use examples** - Look at existing entities (alquileres.edn)

### For Seniors:
1. **Review first** - Understand what junior built before coding
2. **Start simple** - Implement basic validation before complex logic
3. **Test incrementally** - Test each hook individually
4. **Document well** - Explain complex logic in comments
5. **Provide feedback** - Help junior learn what to look for

### For Both:
1. **Communicate frequently** - Don't wait until handoff to discuss
2. **Use version control** - Commit often with clear messages
3. **Follow naming conventions** - Keep consistency across entities
4. **Read the guides** - HOOKS_GUIDE.md has excellent examples

---

## 🚀 **Example Timeline**

### Real-World Project: 10 Entities

**Week 1: Junior Creates Prototypes**
- Monday: Scaffold 3 entities (3 hours)
- Tuesday: Scaffold 3 entities (3 hours)
- Wednesday: Scaffold 4 entities (4 hours)
- Thursday: Customize all 10 entities (8 hours)
- Friday: Test and document (8 hours)

**Result:** 80% functional application in 1 week

**Week 2: Senior Adds Business Logic**
- Monday: Review all entities (4 hours)
- Tuesday: Implement hooks for 3 critical entities (8 hours)
- Wednesday: Implement hooks for 3 more entities (8 hours)
- Thursday: Complex integrations (email, payments) (8 hours)
- Friday: Testing, bug fixes, documentation (8 hours)

**Result:** 100% production-ready application in 2 weeks

**Total Time:** 56 hours vs. 150+ hours with traditional frameworks

---

## 🔧 **Troubleshooting**

### "I don't know what business logic to add"
**Junior:** Document what you *think* the rules should be, mark as questions
**Senior:** Review with product owner/business analyst

### "The hook isn't being called"
**Check:**
1. Hook is uncommented in entity EDN
2. Namespace is correct (:rs.hooks.products/before-save)
3. Function name matches exactly
4. File is saved and server restarted (if not using hot reload)

### "Validation isn't showing in form"
**Check:**
1. Hook returns `{:errors {:field "message"}}` format
2. Field `:id` in error map matches field `:id` in EDN
3. Server logs show hook execution

### "Junior and senior editing same file causes conflicts"
**Solution:** They shouldn't be! 
- Junior edits: `resources/entities/*.edn`
- Senior edits: `src/rs/hooks/*.clj`

---

## 📚 **Additional Resources**

- **QUICKSTART.md** - Getting started guide
- **FRAMEWORK_GUIDE.md** - Complete framework reference
- **HOOKS_GUIDE.md** - Hook examples and patterns
- **DATABASE_MIGRATION_GUIDE.md** - Moving to production

---

## 🎓 **Training Recommendations**

### For Juniors:
- **Day 1:** Read QUICKSTART.md, scaffold 1 entity
- **Day 2:** Customize entity, test CRUD operations
- **Day 3:** Work on real project entity
- **Day 4:** Learn SQL queries, foreign keys
- **Day 5:** Scaffold complete module (3-5 related entities)

### For Seniors:
- **Day 1:** Read HOOKS_GUIDE.md, review alquileres example
- **Day 2:** Implement simple hook (validation)
- **Day 3:** Implement side effects (email, updates)
- **Day 4:** Complex queries, computed fields
- **Day 5:** Custom UI, advanced integrations

---

## ✅ **Success Metrics**

**Good Junior Work:**
- All fields properly labeled
- Required fields marked
- Foreign keys configured
- Basic queries work
- Business rules documented

**Good Senior Work:**
- All validation rules implemented
- Side effects work correctly
- Code is documented
- Error messages are clear
- Tests pass

**Good Collaboration:**
- No rework needed
- Clear communication
- Fast handoffs
- Minimal back-and-forth
- Both learn from each other

---

**Remember:** The goal is to build quickly AND maintain quality. Juniors provide speed, seniors provide correctness. Together, you're unstoppable! 🚀
