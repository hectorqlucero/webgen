(ns {{sanitized}}.hooks.siblings
  "Business logic hooks for siblings entity.
   
   SENIOR DEVELOPER: Implement custom business logic here.
   
   See: HOOKS_GUIDE.md for detailed documentation and examples.
   Example: src/rs/hooks/alquileres.clj
   
   Uncomment the hooks you need and implement the logic."
  (:require [{{sanitized}}.models.util :refer [image-link]]))

;; =============================================================================
;; Validators
;; =============================================================================

;; Example validator function:
;; (defn validate-dates
;;   "Validates that end date is after start date"
;;   [params]
;;   (let [start (:start_date params)
;;         end (:end_date params)]
;;     (when (and start end)
;;       ;; Add your validation logic here
;;       nil)))  ; Return nil if valid, or {:field "error message"}

;; =============================================================================
;; Computed Fields
;; =============================================================================

;; Example computed field:
;; (defn compute-total
;;   "Computes total from quantity and price"
;;   [row]
;;   (* (or (:quantity row) 0)
;;      (or (:price row) 0)))

;; =============================================================================
;; Lifecycle Hooks
;; =============================================================================

(defn before-load
  "Hook executed before loading records.
   
   Use cases:
   - Filter by user permissions
   - Add default filters
   - Log access
   
   Args: [params] - Query parameters
   Returns: Modified params map"
  [params]
  ;; TODO: Add your logic here
  (println "[INFO] Loading siblings with params:" params)
  params)

(defn after-load
  "Hook executed after loading records.
   
   Use cases:
   - Add computed fields
   - Format data
   - Enrich with lookups
   
   Args: [rows params] - Loaded rows and query params
   Returns: Modified rows vector"
  [rows params]
  ;; TODO: Add your logic here
  (println "[INFO] Loaded" (count rows) "siblings record(s)")
  (map #(assoc % :imagen (image-link (:imagen %))) rows))

(defn before-save
  "Hook executed before saving a record.
   
   Use cases:
   - Validate data
   - Set defaults
   - Transform values
   - Check permissions
   
   Args: [params] - Form data to be saved
   Returns: Modified params map OR {:errors {...}} if validation fails"
  [params]
  ;; TODO: Add validation and transformation logic
  ;; Example:
  ;; (if-let [errors (validate-dates params)]
  ;;   {:errors errors}
  ;;   (assoc params :status "active"))

  (println "[INFO] Saving siblings...")
  params)

(defn after-save
  "Hook executed after successfully saving a record.
   
   Use cases:
   - Send notifications
   - Update related records
   - Create audit logs
   - Trigger workflows
   
   Args: [entity-id params] - Saved record ID and data
   Returns: {:success true}"
  [entity-id params]
  ;; TODO: Add post-save logic
  (println "[INFO] Siblings saved successfully. ID:" entity-id)
  {:success true})

(defn before-delete
  "Hook executed before deleting a record.
   
   Use cases:
   - Check for related records
   - Verify permissions
   - Prevent deletion if constraints
   
   Args: [entity-id] - ID of record to delete
   Returns: {:success true} to allow, or {:errors {...}} to prevent"
  [entity-id]
  ;; TODO: Add pre-delete checks
  (println "[INFO] Checking if siblings can be deleted. ID:" entity-id)
  {:success true})

(defn after-delete
  "Hook executed after successfully deleting a record.
   
   Use cases:
   - Delete related files
   - Update related records
   - Send notifications
   - Archive data
   
   Args: [entity-id] - ID of deleted record
   Returns: {:success true}"
  [entity-id]
  ;; TODO: Add post-delete logic
  (println "[INFO] Siblings deleted successfully. ID:" entity-id)
  {:success true})
