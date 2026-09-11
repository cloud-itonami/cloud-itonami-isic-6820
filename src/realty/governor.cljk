(ns realty.governor
  "Real-Estate Fee-Services Governor -- the independent compliance layer
  that earns the Realty-Fee-LLM the right to commit. The LLM has no
  notion of jurisdictional property-management/trust-account disclosure
  law, whether a fee can be filed for a property not yet under
  management, whether a claimed management fee actually matches the
  property's own management-fee-rate formula, whether a property
  actually has a contract pending execution, whether a contract's value
  exceeds the owner's pre-authorized spending limit, or when an act
  stops being a draft and becomes a real-world fee payment or contract
  execution, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD -- the real-estate-fee-services analog
  of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order. The first five are HARD violations: a
  human approver CANNOT override them (you don't get to approve your
  way past a fabricated jurisdiction spec-basis, incomplete trust-
  account/disclosure evidence, a fee filed against a property not under
  management, a fee amount that doesn't match this vehicle's own
  independent recompute, a contract execution with no pending contract
  on file, or a contract whose value exceeds the owner's own pre-
  authorized limit). The sixth is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `realty.phase`: for `:stake :actuation/pay-fee`/`:actuation/execute-
  contract` (a real fee disbursement or a real contract execution on
  the owner's behalf) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`realty.
                                       facts`), or invent one? Applies
                                       to `:fee/pay` ONLY when the fee
                                       actually exists (see `pension.
                                       governor`'s own ADR-0001 for the
                                       lesson this proactively avoids:
                                       scoping a spec-basis check to an
                                       op that ALSO carries a 'missing
                                       entity' check, without guarding
                                       on existence first, spuriously
                                       co-fires the wrong rule).
    2. Evidence incomplete         -- for `:fee/pay`, are the
                                       jurisdiction's required
                                       management-agreement/trust-
                                       account/disclosure docs actually
                                       satisfied?
    3. Property not under
       management                   -- for `:fee/file`, has the
                                       referenced property actually
                                       been placed under management? A
                                       fee cannot be filed against a
                                       property that was never taken on.
    4. Fee missing                 -- for `:fee/pay`, does the
                                       referenced fee actually exist on
                                       file?
    5. Fee calculation mismatch    -- for `:fee/pay`, does the fee's
                                       OWN claimed amount actually match
                                       `realty.registry/compute-
                                       management-fee`'s independent
                                       recompute of the property's own
                                       fee-rate formula? Never trusts a
                                       claimed figure as-is -- the SAME
                                       'independently re-derive, never
                                       trust a claimed number' discipline
                                       `cloud-itonami-isic-6629`'s and
                                       `cloud-itonami-isic-6520`'s
                                       checks apply, expressed here as
                                       an EXACT-MATCH (unlike `cloud-
                                       itonami-isic-6530`'s upper-bound
                                       cap) -- a fixed-percentage
                                       management fee has exactly one
                                       correct value.
    6. Contract missing            -- for `:contract/execute`, does the
                                       property actually have a pending
                                       contract? Doubles as the double-
                                       execution guard -- see `realty.
                                       store`'s own docstring.
    7. Contract exceeds
       authorization                -- for `:contract/execute`, does
                                       the pending contract's value
                                       exceed the property's own STATIC
                                       `:contract-authorization-limit`?
                                       Structurally like `casualty.
                                       governor/claim-exceeds-coverage-
                                       violations` (a cap against a
                                       stored constant, not a computed
                                       one) -- unlike check 5's exact-
                                       match against a COMPUTED formula.
    8. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:fee/pay`/
                                       `:contract/execute` (REAL legal/
                                       financial acts) -> escalate.

  One more guard, double-payment prevention, is enforced but NOT listed
  as a numbered HARD check above because it needs no upstream/property
  comparison at all -- `double-payment-violations` refuses to pay the
  SAME fee twice, off this actor's own payment history."
  (:require [realty.facts :as facts]
            [realty.registry :as registry]
            [realty.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Paying a real management fee and executing a real contract on the
  owner's behalf are the two real-world actuation events this actor
  performs."
  #{:actuation/pay-fee :actuation/execute-contract})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:fee/pay`) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  property-management/trust-account requirements. For `:fee/pay`, only
  applies when the fee actually exists (see ns docstring)."
  [{:keys [op subject]} proposal st]
  (when (contains? #{:jurisdiction/assess :fee/pay} op)
    (when (or (not= op :fee/pay) (store/fee st subject))
      (let [value (:value proposal)]
        (when (or (empty? (:cites proposal))
                  (and (contains? value :spec-basis) (nil? (:spec-basis value))))
          [{:rule :no-spec-basis
            :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}])))))

(defn- evidence-incomplete-violations
  "For `:fee/pay`, the jurisdiction's required management-agreement/
  trust-account/disclosure evidence must actually be satisfied for the
  fee's own property -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (= op :fee/pay)
    (when-let [f (store/fee st subject)]
      (let [p (store/property st (:property-id f))
            assessment (store/assessment-of st (:property-id f))]
        (when-not (and assessment
                       (facts/required-evidence-satisfied?
                        (:jurisdiction p) (:checklist assessment)))
          [{:rule :evidence-incomplete
            :detail "法域の必要書類(管理委託契約書/信託口座調整等)が充足していない状態での支払提案"}])))))

(defn- property-not-under-management-violations
  "For `:fee/file`, the referenced property must actually be
  `:status :under-management` -- a fee cannot be filed against a
  property that was never taken on. Like `reinsurance.governor/
  treaty-not-bound-violations` and `pension.governor/member-not-in-
  payout-violations`, a property's status never regresses out of
  `:under-management` once entered, so checking `:status` directly here
  carries none of `cloud-itonami-isic-6622`'s status-lifecycle risk."
  [{:keys [op property-id]} st]
  (when (= op :fee/file)
    (when-not (= :under-management (:status (store/property st property-id)))
      [{:rule :property-not-under-management
        :detail (str property-id " は管理受託(under-management)されていないため、報酬請求は受理できない")}])))

(defn- fee-missing-violations
  "For `:fee/pay`, the referenced fee must actually exist on file --
  refuses to pay out a fabricated/nonexistent fee id."
  [{:keys [op subject]} st]
  (when (= op :fee/pay)
    (when-not (store/fee st subject)
      [{:rule :fee-missing
        :detail (str subject " という報酬請求は登録されていない")}])))

(defn- close? [a b]
  (< (Math/abs (- (double a) (double b))) 0.01))

(defn- fee-calculation-mismatch-violations
  "For `:fee/pay`, INDEPENDENTLY recompute the management fee via
  `realty.registry/compute-management-fee` and compare against the
  fee's OWN claimed amount -- never trusts a claimed figure as-is."
  [{:keys [op subject]} st]
  (when (= op :fee/pay)
    (when-let [f (store/fee st subject)]
      (let [p (store/property st (:property-id f))
            recomputed (registry/compute-management-fee p (:collected-rent f))]
        (when-not (close? recomputed (:claimed-fee-amount f))
          [{:rule :fee-calculation-mismatch
            :detail (str subject " の請求額(" (:claimed-fee-amount f)
                        ")が独自再計算値(" recomputed ")と一致しない")}])))))

(defn- contract-missing-violations
  "For `:contract/execute`, the property must actually have a pending
  contract -- refuses to execute a nonexistent/already-executed
  contract. Doubles as the double-execution guard: `realty.store/
  execute-contract!` clears `:pending-contract` to nil on execution, so
  a repeat attempt falls into this SAME check."
  [{:keys [op subject]} st]
  (when (= op :contract/execute)
    (when-not (:pending-contract (store/property st subject))
      [{:rule :contract-missing
        :detail (str subject " には現在実行待ちの契約が登録されていない")}])))

(defn- contract-exceeds-authorization-violations
  "For `:contract/execute`, the pending contract's value must not
  exceed the property's own STATIC `:contract-authorization-limit` --
  independently checked against the property's own limit, never
  trusting the contract's value as automatically pre-approved."
  [{:keys [op subject]} st]
  (when (= op :contract/execute)
    (when-let [p (store/property st subject)]
      (when-let [pc (:pending-contract p)]
        (when (> (double (:contract-value pc)) (double (:contract-authorization-limit p)))
          [{:rule :contract-exceeds-authorization
            :detail (str subject " の契約額が所有者の事前承認上限を超過している")}])))))

(defn- double-payment-violations
  "For `:fee/pay`, refuses to pay the SAME fee twice, off this actor's
  own payment history -- needs no upstream/property comparison at
  all."
  [{:keys [op subject]} st]
  (when (= op :fee/pay)
    (when (store/fee-already-paid? st subject)
      [{:rule :double-payment
        :detail (str subject " は既に報酬支払い済み")}])))

(defn check
  "Censors a Realty-Fee-LLM proposal against the governor rules. Returns
   {:ok? bool :violations [..] :confidence c :escalate? bool :high-stakes? bool
    :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal st)
                           (evidence-incomplete-violations request st)
                           (property-not-under-management-violations request st)
                           (fee-missing-violations request st)
                           (fee-calculation-mismatch-violations request st)
                           (contract-missing-violations request st)
                           (contract-exceeds-authorization-violations request st)
                           (double-payment-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
