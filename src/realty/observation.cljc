(ns realty.observation
  "The observation contract over the 6820 actor's world — how a reading of an
  OFFICIAL source (a jurisdiction's property-management / real-estate
  regulator) becomes a provenance-preserving, re-observable claim about the
  PUBLISHED property-management requirements and fee-disclosure facts this
  actor's `realty.facts` catalog seeds, and what such a claim can never
  become.

  This layer is deliberately SEPARATE from `realty.registry` (the actor's own
  fee-payment and contract-execution drafts) and from the actor's execution
  path: drafts are what this actor prepares under a human gate; observations
  are what external official sources publish. An observation is never a fee
  payment, never a contract execution, and never feeds back into a payment
  or execution decision.

  ONE CONTRACT, TWELVE PARTS. Every observation run against this contract
  produces the same shapes, so a refresh can be compared with a prior refresh
  and a reader can audit what was seen, when, from where, and what was NOT
  seen:

  1. SOURCE RECEIPT — `receipt`: the frozen record of one source reading
     (https URL, class, language, issuing entity, jurisdiction, sha256
     content-hash, observed-at vs asserted-at, method). A receipt without a
     hash is a rumor. The id must derive from content-hash + observed-at;
     a re-validated receipt whose id no longer derives that way is tampering,
     not history.
  2. TYPED SUBJECT + EVENTS — one subject: a jurisdiction's PUBLISHED
     property-management/fee-disclosure REQUIREMENT SET, identified by the
     SAME jurisdiction key `realty.facts/catalog` uses (sub-national
     exemplar keys like `USA-NY` included — the catalog's own federalism
     notes are the entity authority). Typed published-change events
     (requirement revised, fee schedule republished, trust-account rule
     amended, CMP scheme membership changed). No property, no unit, no
     person: a published requirement set is not a building and not a
     landlord. Party data is refused BY CONSTRUCTION — the scope's privacy
     boundaries forbid personal profiling of owners, landlords or tenants.
  3. MEASUREMENT WINDOW — every observation states `{:from :to}`; published
     changes must be asserted inside it; a claim without a window is
     un-dateable and refused.
  4. CURRENCY BASIS — a monetary figure carries its ISO-4217 currency and
     the date its amount is nominal at, plus the verbatim raw transcription.
     Nothing is normalized, converted or combined (a fee schedule under one
     currency is not comparable with another, and amounts at different dates
     are not interchangeable).
  5. METHOD / VERSION — every artifact names `fee-observation/1`; there is
     no model anywhere in this path (deterministic validation only).
  6. MISSINGNESS / COVERAGE — flags come from a closed vocabulary; a
     jurisdiction with no `realty.facts` spec-basis must carry
     `:jurisdiction-spec-basis-absent`; a requirement set read with no
     disclosed fee figure must carry `:fee-schedule-unavailable` — silence
     would claim completeness.
  7. DERIVED OBSERVATION — `window-observation` (per-subject event COUNTS +
     verbatim publication references) and `coverage-observation` (counts
     over the `realty.facts` catalog: spec-basis, provenance, evidence
     checklists). A COUNT, never a fee, a trend, a score or a market
     measure.
  8. REFRESH HISTORY — `observe` / `refresh` / `refresh-delta` (pure,
     append-only in data): re-observations link to what they refresh via
     `:obs/refresh-of`; the same observation id can never be recorded twice;
     a cross-subject refresh link is refused at append time, not just at
     readout; the delta is verbatim-level (added / removed / changed figures
     and events carried IN FULL on both sides, gap movement, both
     generations' receipt ids) and computes no numeric difference anywhere.
  9. HYAKKA PROPOSAL — `hyakka-proposal`: the exact claim shape proposed to
     the `fudosan` corpus, one per verbatim figure plus one per observed
     subject. The proposal carries the receipts, the verbatim values, their
     bases, the gaps, the scope's epistemic and privacy boundaries, and
     `:no-model true`. It is DATA for the proposing run to carry — this
     contract sends nothing anywhere. Prop names are contract-local and NOT
     registered in the Hyakka ontology; the proposal says so instead of
     quietly borrowing someone else's prop.
  10. QUERY / READBACK — `readback` (the latest observation for a subject at
      or before an as-of, re-validating everything it returns and refusing
      tampered receipts; a miss is reported as a miss, never defaulted) and
      `readback-chain` (the full `:obs/refresh-of` lineage, oldest first,
      every generation revalidated, refusing a truncated, cyclic or
      cross-subject lineage, with pairwise deltas aligned to the chain).
  11. HISTORY DISCIPLINE — duplicate observation ids are refused; the same
      jurisdiction key may never be re-scoped under a different subject
      scope (a national key is not a sub-national exemplar; re-scoping is
      refused); anything that is not this contract's observation is
      refused.
  12. REFUSALS — every rule above refuses loudly (`ex-info` with a
      `:refusal/code`) instead of degrading quietly. `refusals` documents
      the codes.

  WHAT THIS CONTRACT NEVER PRODUCES: a valuation, a market score, a ranking
  of properties / neighbourhoods / jurisdictions / managers, an eligibility
  conclusion about any person (published requirement conditions are
  observations, never adjudications), a fee that anyone should pay, or
  investment advice of any kind. Published fee schedules and requirement
  texts are observations of what a source disclosed — not what any
  management fee should be, not an offer, and not an endorsement of any
  manager or scheme."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [realty.facts :as facts]))

;; --- identity ---------------------------------------------------------------

(def contract-version
  "Every receipt, observation, derived row and proposal carries this string."
  "fee-observation/1")

;; --- refusals (loud, never silent degradation) ------------------------------

(defn- refuse
  [code message]
  (throw (ex-info message {:refusal/code code
                           :refusal/contract contract-version
                           :refusal/message message})))

(defn refusal-code
  "The `:refusal/code` of an `ex-info` thrown by this contract, or nil."
  [e]
  (:refusal/code (ex-data e)))

(def refusals
  "Every refusal code this contract can raise, with what it means. A caller
  catching an exception without a code here did not come from this contract."
  #{:receipt/missing-field
    :receipt/url-not-https
    :receipt/unknown-source-class
    :receipt/unknown-method
    :receipt/bad-content-hash
    :receipt/bad-observed-at
    :receipt/bad-asserted-at
    :receipt/observed-before-asserted
    :receipt/bad-jurisdiction
    :receipt/stale-id
    :figure/missing-field
    :figure/empty-raw
    :figure/unknown-kind
    :figure/monetary-without-currency
    :figure/bad-currency
    :figure/monetary-without-nominal-at
    :figure/bad-nominal-at
    :figure/bad-amount
    :subject/missing-field
    :subject/unknown-scope
    :subject/address-refused
    :observation/missing-field
    :observation/unknown-event-type
    :observation/missing-event-field
    :observation/bad-window
    :observation/window-inverted
    :observation/event-outside-window
    :observation/cross-subject-event
    :observation/address-refused
    :event/party-data-refused
    :observation/no-receipts
    :observation/cross-jurisdiction-receipt
    :observation/unknown-figure-receipt
    :observation/unknown-event-ref
    :observation/unknown-missingness-flag
    :observation/silence-claims-completeness
    :observation/refresh-of-unknown
    :observation/refresh-of-self
    :observation/refresh-of-cross-subject
    :history/duplicate-observation-id
    :history/not-an-observation
    :history/subject-scope-conflict
    :delta/cross-subject
    :coverage/bad-recorded-at
    :readback/tampered-receipt
    :readback/broken-lineage
    :readback/cyclic-lineage
    :readback/chain-cross-subject
    :proposal/not-an-observation})

;; --- vocabulary (closed; extending one is a contract change) ----------------

(def receipt-classes
  "The closed source-class vocabulary a receipt may carry. The first block is
  taken verbatim from the workspace real-estate scope's `:source-policy
  :allow`; the last two are property-management-regulator-adjacent
  publishers (government portals and programme operators also publish these
  requirements) and have NO scope class — see `unmapped-in-scope`. Unknown
  classes are refused, not guessed."
  #{:official-land-registry :official-cadastre :official-statistics-agency
    :official-tax-authority :official-regulator :official-securities-filing
    :official-stock-exchange-filing :official-municipal-planning-authority
    :official-central-bank :fund-first-party :manager-first-party
    :official-programme-operator :official-government-portal})

(def unmapped-in-scope
  "Receipt classes with NO counterpart in the scope's `:source-policy :allow`
  vocabulary. A proposal carrying one of these is flagged
  `:proposal/source-class-unmapped` — surfaced, never silently relabelled
  into a scope class it does not have."
  #{:official-programme-operator :official-government-portal})

(def receipt-methods
  "How the bytes were read. `:verbatim-citation` — page/PDF read and quoted;
   `:official-api` — structured response from the publisher's own endpoint;
   `:archived-receipt` — hash over bytes archived by a previous run."
  #{:verbatim-citation :official-api :archived-receipt})

(def subject-scopes
  "What a subject's jurisdiction key IS. The `realty.facts` catalog keys
  national jurisdictions (`JPN`, `GBR`, `DEU`) and sub-national exemplars
  (`USA-NY`) under one honest convention — an observation's subject key must
  name which kind it claims to be, and one key may never be re-scoped later
  (a national key is not a sub-national exemplar)."
  #{:national :sub-national-exemplar})

(def event-types
  "The closed vocabulary of PUBLISHED-change events this contract can
  observe. All of them are publication acts (a regulator published or
  republished something), never market acts and never a person's act."
  #{:requirement-revised
    :fee-schedule-republished
    :trust-account-rule-amended
    :cmp-scheme-membership-changed
    :disclosure-form-reissued})

(def figure-kinds
  "The closed figure vocabulary. `:disclosed-fee` is a fee amount a source
  PUBLISHED (a licence fee, a statutory disclosure amount) — an observation
  of what the source discloses, NOT what any management fee should be.
  `:requirement-count` is a count of required-evidence items the source
  itself enumerates — a fact about the publication, never a compliance
  verdict."
  #{:disclosed-fee :requirement-count})

(def missingness-flags
  "The closed missingness vocabulary an observation may declare. Chosen from
  the gaps this actor's world actually has; extending it is a contract
  change, not a free-form field."
  #{:fee-schedule-unavailable
    :requirement-text-partial
    :source-not-text-extractable
    :cmp-scheme-unverified
    :jurisdiction-spec-basis-absent})

(def epistemic-boundaries
  "The scope's epistemic boundaries this contract operates under, quoted as
  data so every proposal carries them instead of assuming them."
  #{:published-fee-schedule-is-not-a-fee-anyone-should-pay
    :published-requirement-is-not-a-compliance-verdict
    :missing-is-unmeasured
    :currency-amounts-are-nominal-at-their-own-date-not-comparable-without-a-stated-basis
    :a-national-key-is-not-a-sub-national-exemplar
    :worldwide-is-a-coverage-goal-not-a-completeness-claim
    :no-model-deterministic-validation-only})

(def privacy-boundaries
  "The scope's privacy boundaries enforced BY CONSTRUCTION here (see
  `refusals`: addresses and party data cannot enter an observation)."
  #{:no-natural-person-identification
    :no-personal-residential-address-linkage
    :no-owner-or-landlord-profiling
    :no-tenant-profiling
    :no-personal-contact-data
    :no-personal-wealth
    :no-eligibility-adjudication-about-any-person
    :no-manager-or-scheme-ranking})

;; --- shape helpers ----------------------------------------------------------

(def date-re
  "ISO calendar date, the only date shape this contract accepts."
  #"^\d{4}-\d{2}-\d{2}$")

(defn- valid-date? [s] (and (string? s) (re-find date-re s)))

(defn- nonempty-str? [x] (and (string? x) (not (str/blank? x))))

(defn- iso-locale-date?
  "A date string with no timezone ambiguity: plain YYYY-MM-DD compares
  correctly as a string, which is all this contract ever does with dates."
  [s] (valid-date? s))

(defn- frozen
  "What 'frozen' means concretely: a plain hash-map copy. Callers keep the
  original; the contract returns a value that shares nothing mutable."
  [m] (into {} m))

;; --- 1. source receipt ------------------------------------------------------

(defn receipt-id
  "The only id a receipt may carry: derived from its content-hash and
  observed-at. An id that no longer derives is an edited receipt."
  [{:receipt/keys [content-hash observed-at]}]
  (when (and (nonempty-str? content-hash) (valid-date? observed-at))
    (str "receipt:" (subs content-hash 0 16) ":" observed-at)))

(defn receipt
  "Validate + freeze one source receipt. Pure function — reads nothing,
  fetches nothing. The hash is the sha256 of the observed bytes as recorded
  by the reading run; this contract cannot recompute it (it never had the
  bytes) so it validates its FORM and refuses a receipt whose stored id no
  longer derives from hash + observed-at (:receipt/stale-id — edited after
  freezing is refused, never re-branded)."
  [{:receipt/keys [source-url source-class source-language issuing-entity
                   jurisdiction content-hash observed-at asserted-at method]
    :as r}]
  (when-not (and (map? r)
                 (nonempty-str? source-url) (nonempty-str? source-language)
                 (nonempty-str? issuing-entity) (nonempty-str? content-hash)
                 (some? source-class) (some? method))
    (refuse :receipt/missing-field
            "receipt: source-url, source-class, source-language,
             issuing-entity, content-hash and method are all required"))
  (when-not (str/starts-with? source-url "https://")
    (refuse :receipt/url-not-https
            (str "receipt: source-url must be https, got: " source-url)))
  (when-not (contains? receipt-classes source-class)
    (refuse :receipt/unknown-source-class
            (str "receipt: unknown source-class " source-class)))
  (when-not (contains? receipt-methods method)
    (refuse :receipt/unknown-method
            (str "receipt: unknown method " method)))
  (when-not (re-find #"^[0-9a-f]{64}$" content-hash)
    (refuse :receipt/bad-content-hash
            "receipt: content-hash must be lowercase sha256 hex (64 chars)"))
  (when-not (iso-locale-date? observed-at)
    (refuse :receipt/bad-observed-at
            "receipt: observed-at must be YYYY-MM-DD"))
  (when-not (iso-locale-date? asserted-at)
    (refuse :receipt/bad-asserted-at
            "receipt: asserted-at must be YYYY-MM-DD"))
  (when (neg? (compare observed-at asserted-at))
    (refuse :receipt/observed-before-asserted
            "receipt: observed-at precedes asserted-at — the reading cannot
             precede the source's own assertion"))
  (when-not (nonempty-str? jurisdiction)
    (refuse :receipt/bad-jurisdiction "receipt: jurisdiction required"))
  (let [expected (receipt-id r)]
    (when-not (= expected (:receipt/id r))
      (refuse :receipt/stale-id
              (str "receipt: stored id does not derive from content-hash + "
                   "observed-at (expected " expected ")"))))
  (frozen (assoc r :receipt/contract-version contract-version)))

(defn revalidate-receipt
  "Readback-time revalidation of a frozen receipt: the same rules, so a
  receipt edited after freezing is refused on the way out too."
  [r]
  (if (and (map? r) (= contract-version (:receipt/contract-version r)))
    (receipt r)
    (refuse :readback/tampered-receipt
            "readback: artifact is not this contract's receipt")))

;; --- 2. figures -------------------------------------------------------------

(defn figure
  "Validate one verbatim figure. `:figure/raw` is the transcription exactly
  as the source states it — required, never normalized. A `:disclosed-fee`
  figure carries :amount (integer), :currency (ISO-4217 alpha-3) and
  :nominal-at (the date the amount is nominal at). A `:requirement-count`
  figure carries :value (a non-negative integer) — how many evidence items
  the source itself enumerates, never a compliance verdict.
  `:figure/source` is the receipt id backing it; `:figure/event-ref`
  optionally names the published-change event it belongs to."
  [{:figure/keys [kind raw amount value currency nominal-at] :as f}]
  (when-not (and (map? f) (contains? figure-kinds kind))
    (refuse :figure/unknown-kind (str "figure: unknown kind " kind)))
  (when-not (nonempty-str? raw)
    (refuse :figure/empty-raw
            "figure: raw verbatim transcription required (no raw = not read)"))
  (case kind
    :disclosed-fee (do (when-not (and (int? amount) (not (neg? amount)))
                         (refuse :figure/bad-amount
                                 "figure: disclosed-fee amount must be a >= 0 integer"))
                       (when-not (and (nonempty-str? currency)
                                      (re-find #"^[A-Z]{3}$" currency))
                         (refuse :figure/monetary-without-currency
                                 "figure: disclosed-fee needs ISO-4217 currency"))
                       (when-not (iso-locale-date? nominal-at)
                         (refuse :figure/monetary-without-nominal-at
                                 "figure: disclosed-fee needs a nominal-at date —
                                  an amount without its own date is not
                                  comparable with anything")))
    :requirement-count (when-not (and (int? value) (not (neg? value)))
                         (refuse :figure/bad-value
                                 "figure: requirement-count value must be a >= 0 integer")))
  (when-not (nonempty-str? (:figure/source f))
    (refuse :figure/missing-field "figure: source receipt id required"))
  (frozen f))

;; --- 3. subject -------------------------------------------------------------

(defn subject
  "Validate one observation subject: a jurisdiction's published
  property-management/fee-disclosure requirement set, identified by the SAME
  jurisdiction key the `realty.facts` catalog uses, under a declared closed
  `:subject/scope`. A street address is refused wherever it tries to enter —
  the subject is a published requirement set, not a property and not a
  person."
  [{:subject/keys [id scope] :as s}]
  (when-not (nonempty-str? id)
    (refuse :subject/missing-field "subject: jurisdiction key required"))
  (when-not (contains? subject-scopes scope)
    (refuse :subject/unknown-scope
            (str "subject: unknown scope " scope)))
  (when (some #(contains? s %) [:subject/address :subject/street-address
                                :subject/location :subject/postal-address])
    (refuse :subject/address-refused
            "subject: address keys are refused — a published requirement
             set, not a property or a person's home"))
  (frozen s))

;; --- 4. observation ---------------------------------------------------------

(defn observation
  "Validate + freeze one observation of ONE jurisdiction's published
  requirement set over ONE window: receipts (>= 1), typed published-change
  events (each asserted inside the window and bound to the subject),
  verbatim figures with bases, closed-vocabulary missingness. Refuses party
  data and addresses by construction, refuses a jurisdiction with no
  `realty.facts` spec-basis that does not carry
  `:jurisdiction-spec-basis-absent`, and refuses an observation naming no
  requirement figure at all without `:fee-schedule-unavailable` or
  `:requirement-text-partial` — silence would claim completeness."
  [{:obs/keys [id subject events receipts figures missingness window
               refresh-of recorded-at]
    :as o}]
  (when-not (nonempty-str? id)
    (refuse :observation/missing-field "observation: :obs/id required"))
  (when-not (iso-locale-date? recorded-at)
    (refuse :observation/missing-field
            "observation: :obs/recorded-at must be YYYY-MM-DD"))
  (let [sid (:subject/id subject)
        jurisdiction (:obs/jurisdiction o)]
    (when-not (nonempty-str? jurisdiction)
      (refuse :observation/missing-field
              "observation: :obs/jurisdiction required"))
    (subject subject)
    (when-not (and (map? window)
                   (iso-locale-date? (:from window))
                   (iso-locale-date? (:to window)))
      (refuse :observation/bad-window "observation: window must be {:from :to}, YYYY-MM-DD"))
    (when (pos? (compare (:from window) (:to window)))
      (refuse :observation/window-inverted
              "observation: window :from is after :to"))
    (when (empty? receipts)
      (refuse :observation/no-receipts
              "observation: at least one source receipt required"))
    (doseq [r receipts]
      (revalidate-receipt r)
      (when-not (= jurisdiction (:receipt/jurisdiction r))
        (refuse :observation/cross-jurisdiction-receipt
                (str "observation: receipt " (:receipt/id r)
                     " is from " (:receipt/jurisdiction r)
                     ", observation is about " jurisdiction))))
    (doseq [e events]
      (when-not (contains? event-types (:event/type e))
        (refuse :observation/unknown-event-type
                (str "observation: unknown event type " (:event/type e))))
      (when-not (nonempty-str? (:event/publication-ref e))
        (refuse :observation/missing-event-field
                "observation: event needs a publication-ref"))
      (when-not (nonempty-str? (:event/publisher e))
        (refuse :observation/missing-event-field
                "observation: event needs a publishing entity (regulator)"))
      (when-not (iso-locale-date? (:event/asserted-at e))
        (refuse :observation/missing-event-field
                "observation: event needs a YYYY-MM-DD asserted-at"))
      (when (or (contains? e :event/parties) (contains? e :event/party-names)
                (contains? e :event/parties-anon))
        (refuse :event/party-data-refused
                "observation: party data is refused — no person is
                 identified, profiled or adjudicated by this contract"))
      (when-not (or (string? (:event/subject-id e)) (keyword? (:event/subject-id e)))
        (refuse :observation/missing-event-field
                "observation: event must name its subject"))
      (when-not (= sid (:event/subject-id e))
        (refuse :observation/cross-subject-event
                (str "observation: event " (:event/publication-ref e)
                     " belongs to " (:event/subject-id e)
                     ", observation is about " sid)))
      (when-not (and (neg? (compare (:event/asserted-at e) (:to window)))
                     (pos? (compare (:event/asserted-at e) (:from window))))
        (refuse :observation/event-outside-window
                (str "observation: event " (:event/publication-ref e)
                     " asserted " (:event/asserted-at e)
                     " is outside the window")))
      )
    (doseq [f figures]
      (figure f)
      (when-not (some #(= (:figure/source f) (:receipt/id %)) receipts)
        (refuse :observation/unknown-figure-receipt
                (str "observation: figure cites receipt "
                     (:figure/source f) " which is not in this observation")))
      (when-let [eref (:figure/event-ref f)]
        (when-not (some #(= eref (:event/publication-ref %)) events)
          (refuse :observation/unknown-event-ref
                  (str "observation: figure cites event " eref
                       " which is not in this observation")))))
    (let [flags (or missingness #{})]
      (when-not (and (set? flags) (every? #(contains? missingness-flags %) flags))
        (refuse :observation/unknown-missingness-flag
                "observation: missingness flags must come from the closed vocabulary"))
      (when (and (nil? (facts/spec-basis jurisdiction))
                 (not (contains? flags :jurisdiction-spec-basis-absent)))
        (refuse :observation/silence-claims-completeness
                (str "observation: jurisdiction " jurisdiction
                     " has no spec-basis in realty.facts — carry "
                     ":jurisdiction-spec-basis-absent, do not claim silently")))
      (doseq [e events]
        (when (and (= :fee-schedule-republished (:event/type e))
                   (not (some #(and (= :disclosed-fee (:figure/kind %))
                                    (= (:event/publication-ref e)
                                       (:figure/event-ref %)))
                              figures))
                   (not (contains? flags :fee-schedule-unavailable)))
          (refuse :observation/silence-claims-completeness
                  (str "observation: fee-schedule republication "
                       (:event/publication-ref e)
                       " has no disclosed-fee figure and no
                        :fee-schedule-unavailable flag — declare the gap or
                        carry the figure")))))
    (frozen (assoc o
                   :obs/contract-version contract-version
                   :obs/refresh-of (or refresh-of nil)))))

(defn- same-subject? [a b]
  (= (:subject/id a) (:subject/id b)))

;; --- 8. history (pure, append-only in data) ---------------------------------

(defn observe
  "Append a validated observation to a history vector, returning a NEW
  vector (never mutate history in place). Refuses a duplicate observation
  id, a non-observation, and re-scoping a jurisdiction key under a different
  subject scope."
  [history o]
  (when-not (and (map? o) (= contract-version (:obs/contract-version o)))
    (refuse :history/not-an-observation
            "history: not an observation of this contract"))
  (when (some #(= (:obs/id o) (:obs/id %)) history)
    (refuse :history/duplicate-observation-id
            (str "history: observation id " (:obs/id o) " already recorded")))
  (when-let [prior (some #(when (= (:subject/id (:obs/subject o))
                                   (:subject/id (:obs/subject %)))
                             %)
                         history)]
    (when-not (= (:subject/scope (:obs/subject o))
                 (:subject/scope (:obs/subject prior)))
      (refuse :history/subject-scope-conflict
              (str "history: jurisdiction key " (:subject/id (:obs/subject o))
                   " was recorded as " (:subject/scope (:obs/subject prior))
                   ", now re-scoped as " (:subject/scope (:obs/subject o))
                   " — a national key is not a sub-national exemplar"))))
  (conj (vec history) o))

(defn refresh
  "Record `o` as a re-observation of the observation named `prior-id`:
  links it via :obs/refresh-of and appends. Refuses an unknown prior, a
  self-link, and — at APPEND time, not only at readout — a link across
  subjects."
  [history prior-id o]
  (let [prior (some #(when (= prior-id (:obs/id %)) %) history)]
    (when-not prior
      (refuse :observation/refresh-of-unknown
              (str "refresh: prior observation " prior-id " not in history")))
    (when (= prior-id (:obs/id o))
      (refuse :observation/refresh-of-self
              "refresh: an observation cannot refresh itself"))
    (when-not (same-subject? (:obs/subject prior) (:obs/subject o))
      (refuse :observation/refresh-of-cross-subject
              (str "refresh: " (:obs/id o) " is about "
                   (:subject/id (:obs/subject o))
                   " but claims to refresh " prior-id " about "
                   (:subject/id (:obs/subject prior)))))
    (observe history (assoc o :obs/refresh-of prior-id))))

;; --- 8b. refresh delta (verbatim-level; no numeric difference anywhere) -----

(defn- keyed-events [obs]
  (into {} (map (fn [e] [(:event/publication-ref e) e]) (:obs/events obs))))

(defn- keyed-figures [obs]
  (into {}
        (map (fn [f] [[(:figure/kind f) (:figure/event-ref f) (:figure/raw f)] f])
             (:obs/figures obs))))

(defn refresh-delta
  "The verbatim-level audit of what moved between two frozen observations of
  the SAME subject: events added / removed / changed, figures added /
  removed / changed (both sides carried IN FULL), missingness flags added
  and removed, and the receipt ids of BOTH generations. Computes no numeric
  difference and normalizes nothing — fee amounts at different dates and
  under different currencies are not comparable, so they are only ever
  carried, side by side, never combined. `:delta/kind` is :unchanged when
  nothing moved."
  [prior next]
  (when-not (same-subject? (:obs/subject prior) (:obs/subject next))
    (refuse :delta/cross-subject
            "delta: the two observations are about different subjects"))
  (let [pe (keyed-events prior) ne (keyed-events next)
        pf (keyed-figures prior) nf (keyed-figures next)
        pm (:obs/missingness prior #{}) nm (:obs/missingness next #{})
        changed-events (vec (for [k (filter (fn [k] (and (contains? pe k) (contains? ne k)
                                                        (not= (get pe k) (get ne k))))
                                            (distinct (concat (keys pe) (keys ne))))]
                              {:delta/key k :delta/prior (get pe k) :delta/next (get ne k)}))
        changed-figures (vec (for [k (filter (fn [k] (and (contains? pf k) (contains? nf k)
                                                          (not= (get pf k) (get nf k))))
                                             (distinct (concat (keys pf) (keys nf))))]
                               {:delta/key k :delta/prior (get pf k) :delta/next (get nf k)}))
        added-events (vec (map #(get ne %) (filter #(contains? ne %) (remove #(contains? pe %) (keys ne)))))
        removed-events (vec (map #(get pe %) (filter #(contains? pe %) (remove #(contains? ne %) (keys pe)))))
        added-figures (vec (map #(get nf %) (filter #(contains? nf %) (remove #(contains? pf %) (keys nf)))))
        removed-figures (vec (map #(get pf %) (filter #(contains? pf %) (remove #(contains? nf %) (keys pf)))))
        gap-added (vec (sort (map name (set/difference nm pm))))
        gap-removed (vec (sort (map name (set/difference pm nm))))
        moved? (or (seq added-events) (seq removed-events) (seq changed-events)
                   (seq added-figures) (seq removed-figures) (seq changed-figures)
                   (seq gap-added) (seq gap-removed))]
    (frozen
     {:delta/kind (if moved? :changed :unchanged)
      :delta/subject-id (:subject/id (:obs/subject prior))
      :delta/prior-id (:obs/id prior) :delta/next-id (:obs/id next)
      :delta/prior-receipts (vec (sort (map :receipt/id (:obs/receipts prior))))
      :delta/next-receipts (vec (sort (map :receipt/id (:obs/receipts next))))
      :delta/added-events added-events :delta/removed-events removed-events
      :delta/changed-events changed-events
      :delta/added-figures added-figures :delta/removed-figures removed-figures
      :delta/changed-figures changed-figures
      :delta/gap-added gap-added :delta/gap-removed gap-removed
      :delta/comparability-note
      "verbatim-level only; no numeric difference is computed; fee amounts at
       different dates and under different currencies are carried side by
       side, never combined"
      :delta/contract-version contract-version})))

;; --- 7. derived observations (COUNTS, never fees or trends) -----------------

(defn window-observation
  "The derived, per-subject reading of ONE frozen observation: in-window
  event counts by type, the verbatim publication references, figure counts
  by kind, the missingness carried forward, the receipt ids. A count of what
  the receipts show — not a fee level, not a compliance measure, not a
  trend, and carrying no amounts at all."
  [obs]
  (frozen
   {:derived/contract-version contract-version
    :derived/subject-id (:subject/id (:obs/subject obs))
    :derived/window (:obs/window obs)
    :derived/event-counts (frequencies (map :event/type (:obs/events obs)))
    :derived/publication-refs (vec (sort (map :event/publication-ref
                                              (:obs/events obs))))
    :derived/figure-counts (frequencies (map :figure/kind (:obs/figures obs)))
    :derived/missingness (:obs/missingness obs #{})
    :derived/receipt-ids (vec (sort (map :receipt/id (:obs/receipts obs))))
    :derived/no-model true
    :derived/note
    "counts are coverage-limited observations of what the receipts show —
     not a fee level, not a compliance measure, not a trend"}))

(defn coverage-observation
  "The derived, catalog-level reading of `realty.facts`: how many
  jurisdictions carry a spec-basis and a provenance URL, how many evidence
  checklist items the catalog enumerates in total — COUNTS over what the
  catalog itself publishes, plus the catalog's own honest note. Worldwide is
  a coverage goal, not a completeness claim, and the note says so."
  [recorded-at]
  (when-not (iso-locale-date? recorded-at)
    (refuse :coverage/bad-recorded-at
            "coverage: recorded-at must be YYYY-MM-DD"))
  (let [ks (keys facts/catalog)
        with-prov (filter #(nonempty-str? (:provenance (facts/spec-basis %))) ks)
        evidence-items (reduce + (map #(count (facts/evidence-checklist %)) ks))]
    (frozen
     {:coverage/contract-version contract-version
      :coverage/recorded-at recorded-at
      :coverage/jurisdictions-with-spec-basis (count ks)
      :coverage/jurisdictions-with-provenance (count (distinct with-prov))
      :coverage/evidence-checklist-items evidence-items
      :coverage/note (:note (facts/coverage))
      :coverage/no-model true})))

;; --- 9. hyakka proposal (SHAPE — this contract transmits nothing) -----------

(defn hyakka-proposal
  "The exact claim shapes proposed to the `fudosan` corpus: one claim per
  verbatim figure plus one per observed subject, each carrying its receipt,
  value, basis, window and gaps; plus the scope's epistemic and privacy
  boundaries and `:no-model true`. Prop names are contract-local and NOT
  registered in the Hyakka ontology — the proposal says so
  (`:proposal/props-unregistered`). Receipt classes without a scope
  counterpart are flagged `:proposal/source-class-unmapped`. This returns
  DATA for the proposing run to carry; nothing is sent anywhere by this
  contract."
  [obs]
  (when-not (and (map? obs) (= contract-version (:obs/contract-version obs)))
    (refuse :proposal/not-an-observation
            "proposal: not an observation of this contract"))
  (let [subject (:obs/subject obs)
        window (:obs/window obs)
        gaps (vec (sort (map name (:obs/missingness obs #{}))))
        figure-claims
        (vec (for [f (:obs/figures obs)]
               (if (= :disclosed-fee (:figure/kind f))
                 {:claim/prop "fudosan.prop/disclosed-fee-observation"
                  :claim/subject-id (:subject/id subject)
                  :claim/subject-scope (:subject/scope subject)
                  :claim/value-verbatim (:figure/raw f)
                  :claim/amount (:figure/amount f)
                  :claim/currency (:figure/currency f)
                  :claim/nominal-at (:figure/nominal-at f)
                  :claim/receipt-id (:figure/source f)
                  :claim/event-ref (:figure/event-ref f)
                  :claim/window window
                  :claim/gaps gaps}
                 {:claim/prop "fudosan.prop/requirement-count-observation"
                  :claim/subject-id (:subject/id subject)
                  :claim/subject-scope (:subject/scope subject)
                  :claim/value-verbatim (:figure/raw f)
                  :claim/amount nil
                  :claim/currency nil
                  :claim/nominal-at nil
                  :claim/value (:figure/value f)
                  :claim/receipt-id (:figure/source f)
                  :claim/event-ref (:figure/event-ref f)
                  :claim/window window
                  :claim/gaps gaps})))
        subject-claim
        {:claim/prop "fudosan.prop/published-requirements-in-window"
         :claim/subject-id (:subject/id subject)
         :claim/subject-scope (:subject/scope subject)
         :claim/event-counts (frequencies (map :event/type (:obs/events obs)))
         :claim/window window
         :claim/receipt-ids (vec (sort (map :receipt/id (:obs/receipts obs))))
         :claim/gaps gaps}
        unmapped (vec (sort (map name (set/intersection
                                      (set (map :receipt/source-class
                                                (:obs/receipts obs)))
                                      unmapped-in-scope))))]
    (frozen
     {:proposal/corpus "fudosan"
      :proposal/target "network-awai/app-hyakka"
      :proposal/contract-version contract-version
      :proposal/subject-id (:subject/id subject)
      :proposal/jurisdiction (:obs/jurisdiction obs)
      :proposal/figure-claims figure-claims
      :proposal/subject-claims [subject-claim]
      :proposal/epistemic-boundaries (vec (sort (map name epistemic-boundaries)))
      :proposal/privacy-boundaries (vec (sort (map name privacy-boundaries)))
      :proposal/gaps gaps
      :proposal/no-model true
      :proposal/props-unregistered true
      :proposal/source-class-unmapped unmapped
      :proposal/note
      "SHAPE ONLY — this proposal is data for the proposing run to carry;
       this contract sends nothing anywhere"})))

;; --- 10. query / readback ---------------------------------------------------

(defn readback
  "The latest observation for `subject-id` whose :obs/recorded-at is at or
  before `as-of`, re-validating everything it returns (receipts by their
  derived id, figures, missingness, subject) and refusing tampered
  artifacts on the way out. A miss is a miss: nil, never a default."
  [history subject-id as-of]
  (when-not (iso-locale-date? as-of)
    (refuse :observation/bad-window "readback: as-of must be YYYY-MM-DD"))
  (let [candidates (filter #(and (= subject-id (:subject/id (:obs/subject %)))
                                 (not (pos? (compare (:obs/recorded-at %) as-of))))
                           history)]
    (when-let [obs (last (sort-by :obs/recorded-at candidates))]
      (doseq [r (:obs/receipts obs)]
        (revalidate-receipt r))
      (doseq [f (:obs/figures obs)] (figure f))
      (subject (:obs/subject obs))
      obs)))

(defn readback-chain
  "The full :obs/refresh-of lineage for a subject, OLDEST FIRST, every
  generation revalidated on the way out. Refuses a lineage whose link
  points at an unknown id (:readback/broken-lineage), a cyclic lineage
  (:readback/cyclic-lineage), and a chain element about another subject
  (:readback/chain-cross-subject). Returns the chain plus the pairwise
  deltas aligned to it (n-1 deltas for n generations)."
  [history subject-id]
  (let [by-id (into {} (map (fn [o] [(:obs/id o) o]) history))
        latest (last (filter #(= subject-id (:subject/id (:obs/subject %)))
                             (sort-by :obs/recorded-at history)))]
    (when latest
      (loop [cur latest, acc (), seen #{}]
        (when (contains? seen (:obs/id cur))
          (refuse :readback/cyclic-lineage
                  (str "readback: lineage cycles at " (:obs/id cur))))
        (let [acc (conj acc cur), seen (conj seen (:obs/id cur))]
          (doseq [r (:obs/receipts cur)] (revalidate-receipt r))
          (doseq [f (:obs/figures cur)] (figure f))
          (subject (:obs/subject cur))
          (when-not (= subject-id (:subject/id (:obs/subject cur)))
            (refuse :readback/chain-cross-subject
                    (str "readback: chain element " (:obs/id cur)
                         " is about " (:subject/id (:obs/subject cur)))))
          (if-let [pid (:obs/refresh-of cur)]
            (let [parent (get by-id pid)]
              (when-not parent
                (refuse :readback/broken-lineage
                        (str "readback: " (:obs/id cur) " refreshes " pid
                             ", which is not in history")))
              (recur parent acc seen))
            (let [chain (vec acc)
                  deltas (vec (map (fn [[a b]] (refresh-delta a b))
                                   (partition 2 1 chain)))]
              {:chain chain :deltas deltas})))))))
