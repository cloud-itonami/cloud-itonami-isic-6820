(ns realty.registry
  "Pure-function fee-payment and contract-execution record construction
  -- an append-only real-estate-fee-services book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a fee-payment or contract-execution
  reference number -- every management firm/jurisdiction assigns its
  own reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `realty.facts` uses.

  `compute-management-fee` is a REAL, well-known formula (a management
  fee as a fixed percentage of collected rent), not an invented
  placeholder default -- see its own docstring for the honest
  simplification it makes vs. a real management agreement's full terms
  (no tiered fee schedules, no leasing/renewal commissions, no
  maintenance markup). This is the SAME 'reimplement the well-known
  math independently, so a downstream governor can cross-check a
  claimed figure against it' pattern `cloud-itonami-isic-6629`'s
  `auxiliary.registry/apportion-general-average` and `cloud-itonami-
  isic-6520`'s `reinsurance.registry/compute-recovery` establish --
  applied here to a FOURTH domain-specific formula, as an EXACT-MATCH
  check (like `apportion-general-average`/`compute-recovery`) rather
  than an upper-bound cap (unlike `cloud-itonami-isic-6530`'s
  `compute-max-disbursement`) -- a fixed-percentage management fee has
  exactly one correct value, not a range.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any property-management/banking system. It builds the RECORD
  a management firm would keep, not the act of paying the fee or
  executing the contract itself (those are `realty.operation`'s
  `:fee/pay` and `:contract/execute`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  licensed management firm's act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn compute-management-fee
  "Pure computation of the management fee owed on collected rent -- a
  REAL, simplified formula (see ns docstring for what a full
  management agreement's terms additionally model that this does not):
  fee = collected-rent * management-fee-rate, a FIXED percentage with
  exactly one correct value (no tiered fee schedules, no leasing/
  renewal commissions, no maintenance markup)."
  [property collected-rent]
  (when (neg? collected-rent)
    (throw (ex-info "compute-management-fee: collected-rent must be >= 0" {})))
  (let [rate (get property :management-fee-rate)]
    (when-not (and rate (<= 0 rate 1))
      (throw (ex-info "compute-management-fee: management-fee-rate must be in [0,1]" {})))
    (* (double collected-rent) (double rate))))

(defn register-fee-payment
  "Validate + construct the FEE-PAYMENT registration DRAFT -- the
  management firm's own legal act of paying out a real management fee.
  Pure function -- does not touch any real banking/trust-account
  system; it builds the RECORD a firm would keep. `realty.governor`
  independently re-verifies the fee amount against `compute-
  management-fee`, and blocks a double-payment of the same fee request,
  before this is ever allowed to commit."
  [property-id fee-id collected-rent paid-amount jurisdiction sequence]
  (when-not (and property-id (not= property-id ""))
    (throw (ex-info "fee-payment: property_id required" {})))
  (when-not (and fee-id (not= fee-id ""))
    (throw (ex-info "fee-payment: fee_id required" {})))
  (when (neg? collected-rent)
    (throw (ex-info "fee-payment: collected-rent must be >= 0" {})))
  (when (neg? paid-amount)
    (throw (ex-info "fee-payment: paid-amount must be >= 0" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "fee-payment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "fee-payment: sequence must be >= 0" {})))
  (let [fee-number (str (str/upper-case jurisdiction) "-FEE-" (zero-pad sequence 6))
        record {"record_id" fee-number
                "kind" "fee-payment-draft"
                "property_id" property-id
                "fee_id" fee-id
                "collected_rent" collected-rent
                "paid_amount" paid-amount
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "fee_number" fee-number
     "certificate" (unsigned-certificate "FeePaymentCertificate" fee-number fee-number)}))

(defn register-contract-execution
  "Validate + construct the CONTRACT-EXECUTION registration DRAFT --
  the management firm's own legal act of executing a real contract
  (e.g. a vendor/maintenance contract, on the owner's behalf). Pure
  function -- does not touch any real vendor-management/e-signature
  system; it builds the RECORD a firm would keep."
  [property-id vendor contract-type contract-value jurisdiction sequence]
  (when-not (and property-id (not= property-id ""))
    (throw (ex-info "contract-execution: property_id required" {})))
  (when-not (and vendor (not= vendor ""))
    (throw (ex-info "contract-execution: vendor required" {})))
  (when (neg? contract-value)
    (throw (ex-info "contract-execution: contract-value must be >= 0" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "contract-execution: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "contract-execution: sequence must be >= 0" {})))
  (let [contract-number (str (str/upper-case jurisdiction) "-CTR-" (zero-pad sequence 6))
        record {"record_id" contract-number
                "kind" "contract-execution-draft"
                "property_id" property-id
                "vendor" vendor
                "contract_type" (name contract-type)
                "contract_value" contract-value
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "contract_number" contract-number
     "certificate" (unsigned-certificate "ContractExecutionCertificate" contract-number contract-number)}))

(defn append
  "Append a fee-payment/contract-execution record, returning a NEW list
  (never mutate history in place)."
  [history result]
  (conj (vec history) (get result "record")))
