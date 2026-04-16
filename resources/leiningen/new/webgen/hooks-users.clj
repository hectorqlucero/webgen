(ns {{sanitized}}.hooks.users
  "Business logic hooks for users entity.
   
   SENIOR DEVELOPER: Implement custom business logic here.
   
   See: HOOKS_GUIDE.md for detailed documentation and examples.
   Example: src/{{sanitized}}/hooks/alquileres.clj
   
   Uncomment the hooks you need and implement the logic."
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as st]))

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
  (println "[INFO] Loading users with params:" params)
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
  (println "[INFO] Loaded" (count rows) "users record(s)")
  rows)

(defn before-save
  "Hook executed before saving a record.
   
   Password handling:
   - New user with no password → default password set to their username (email).
     They must change it via /change/password after first login.
   - Existing user with no password in params → password column left untouched.
   - Any user where a password value is provided → hashed before saving.
   
   Args: [params] - Form data to be saved
   Returns: Modified params map OR {:errors {...}} if validation fails"
  [params]
  (let [password (:password params)
        is-new?  (let [id (:id params)] (or (nil? id) (st/blank? (str id)) (= (str id) "0")))]
    (cond
      ;; Password provided — hash it regardless of new/edit
      (and password (not (st/blank? password)))
      (assoc params :password (hashers/derive password))

      ;; New record, no password — set default to username (email)
      is-new?
      (assoc params :password (hashers/derive (or (:username params) "changeme")))

      ;; Existing record, no password — leave the column untouched
      :else
      (dissoc params :password))))

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
  (println "[INFO] Users saved successfully. ID:" entity-id)
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
  (println "[INFO] Checking if users can be deleted. ID:" entity-id)
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
  (println "[INFO] Users deleted successfully. ID:" entity-id)
  {:success true})
