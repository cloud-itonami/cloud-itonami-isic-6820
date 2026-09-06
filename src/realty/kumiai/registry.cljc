(ns realty.kumiai.registry
  "Pure-function record construction for the condominium-association
  actor -- an append-only book of record for two things an association
  actually does: RESOLVE something at a general meeting, and COMMISSION
  the works that resolution authorised.

  Same discipline as `realty.registry`: there is no international
  check-digit standard for a minute number or a works-order number, so
  this does not invent one -- it builds a jurisdiction-scoped sequence
  and validates the required fields. Every certificate is UNSIGNED;
  signing is the association's officers' act, not this actor's.

  Note what is deliberately NOT here: the vote arithmetic. Judging a
  resolution is `realty.kumiai.resolution`, and the governor runs it
  independently before this namespace is ever reached. A registry that
  both computed and recorded the verdict would have nothing to be
  checked against."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-resolution
  "Validate + construct the RESOLUTION-MINUTE draft: what the general
  meeting resolved, on which statutory basis, with which tallies.

  `verdict` is a `realty.kumiai.resolution/tally` map. It is embedded
  whole rather than reduced to a boolean, because 'passed' without the
  denominator it was counted against is not auditable -- and the
  denominator is the thing that changed under the 令和7年改正."
  [association-id resolution-id kind budget-amount jurisdiction verdict sequence]
  (when-not (and association-id (not= association-id ""))
    (throw (ex-info "resolution: association_id required" {})))
  (when-not (and resolution-id (not= resolution-id ""))
    (throw (ex-info "resolution: resolution_id required" {})))
  (when-not (keyword? kind)
    (throw (ex-info "resolution: kind must be a keyword" {})))
  (when (neg? (double (or budget-amount 0)))
    (throw (ex-info "resolution: budget-amount must be >= 0" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "resolution: jurisdiction required" {})))
  (when-not (map? verdict)
    (throw (ex-info "resolution: verdict (a resolution/tally map) required" {})))
  (when (< sequence 0)
    (throw (ex-info "resolution: sequence must be >= 0" {})))
  (let [number (str (str/upper-case jurisdiction) "-RES-" (zero-pad sequence 6))
        record {"record_id" number
                "kind" "resolution-minute-draft"
                "association_id" association-id
                "resolution_id" resolution-id
                "resolution_kind" (name kind)
                "budget_amount" budget-amount
                "jurisdiction" jurisdiction
                "passed" (boolean (:passed? verdict))
                "statutory_basis" (:article verdict)
                ;; Per AXIS, not per rule. Under WEG § 21 Absatz 2 the
                ;; two axes of one resolution are counted against
                ;; different denominators at different fractions, so a
                ;; single pair of fields here would record a rule that
                ;; does not exist.
                "counted_against" (str/join "+" (map name (or (:bases verdict)
                                                              [(or (:base verdict) :unknown)])))
                "effective_fraction" (let [f (get-in verdict [:effective-fraction :fraction])]
                                       (if (and (:numer f) (:denom f))
                                         (str (:numer f) "/" (:denom f))
                                         "per-axis"))
                "fraction_source" (name (get-in verdict [:effective-fraction :source] :unknown))
                "axes" (mapv (fn [a] {"axis" (name (:axis a))
                                      "counted_against" (name (or (:counted-against a) :unknown))
                                      "fraction" (let [f (:fraction a)]
                                                   (when f (str (:numer f) "/" (:denom f))))
                                      "base" (:base a)
                                      "in_favour" (:in-favour a) "required" (:required a)
                                      "met" (boolean (:met? a))})
                             (:axes verdict))
                "quorum_met" (if (:quorum verdict) (boolean (:met? (:quorum verdict))) nil)
                "on_boundary" (boolean (:on-boundary? verdict))
                "immutable" true}]
    {"record" record "resolution_number" number
     "certificate" (unsigned-certificate "AssociationResolutionCertificate" number number)}))

(defn register-works-order
  "Validate + construct the WORKS-ORDER draft -- the association's own
  legal act of commissioning major repair works against a resolution it
  actually passed.

  `resolution-number` is required and non-empty by construction: an
  order with no minute behind it is not a document this registry knows
  how to write."
  [association-id works-id contractor contract-value jurisdiction resolution-number sequence]
  (when-not (and association-id (not= association-id ""))
    (throw (ex-info "works-order: association_id required" {})))
  (when-not (and works-id (not= works-id ""))
    (throw (ex-info "works-order: works_id required" {})))
  (when-not (and contractor (not= contractor ""))
    (throw (ex-info "works-order: contractor required" {})))
  (when (neg? (double contract-value))
    (throw (ex-info "works-order: contract-value must be >= 0" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "works-order: jurisdiction required" {})))
  (when-not (and resolution-number (not= resolution-number ""))
    (throw (ex-info "works-order: resolution_number required -- no order without a minute" {})))
  (when (< sequence 0)
    (throw (ex-info "works-order: sequence must be >= 0" {})))
  (let [number (str (str/upper-case jurisdiction) "-WRK-" (zero-pad sequence 6))
        record {"record_id" number
                "kind" "works-order-draft"
                "association_id" association-id
                "works_id" works-id
                "contractor" contractor
                "contract_value" contract-value
                "jurisdiction" jurisdiction
                "authorised_by" resolution-number
                "immutable" true}]
    {"record" record "works_number" number
     "certificate" (unsigned-certificate "MajorRepairWorksOrderCertificate" number number)}))

(defn append
  "Append a record, returning a NEW list (never mutate history)."
  [history result]
  (conj (vec history) (get result "record")))
