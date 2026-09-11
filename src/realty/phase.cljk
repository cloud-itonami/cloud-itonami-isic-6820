(ns realty.phase
  "Phase 0->3 staged rollout -- the real-estate-fee-services analog of
  `cloud-itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- property intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds jurisdiction assessment writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:property/intake`/`:fee/file` (no
                                 capital risk yet) may auto-commit.
                                 `:fee/pay`/`:contract/execute` NEVER
                                 auto-commit, at any phase.

  `:fee/pay`/`:contract/execute` are deliberately ABSENT from every
  phase's `:auto` set, including phase 3 -- a permanent structural fact,
  not a rollout milestone still to come. Paying a real management fee
  and executing a real contract on the owner's behalf are the two
  real-world legal/financial acts this actor performs; both are always
  a human property manager's call. `realty.governor`'s `:actuation/
  pay-fee`/`:actuation/execute-contract` high-stakes gate enforces the
  same invariant independently -- two layers, not one, agree on this.
  `:property/intake`/`:fee/file` move no capital yet (still HARD-gated
  in `realty.governor`, but never `high-stakes`), so both ARE auto-
  eligible at phase 3, the same multi-auto-op posture `cloud-itonami-
  isic-6512`'s `casualty.phase` already establishes.")

(def read-ops  #{})
(def write-ops #{:property/intake :jurisdiction/assess
                 :fee/file :fee/pay :contract/execute})

;; NOTE the invariant: `:fee/pay`/`:contract/execute` are members of
;; `write-ops` (governor-gated like any write) but are NEVER members of
;; any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                              :auto #{}}
   1 {:label "assisted-intake" :writes #{:property/intake}                              :auto #{}}
   2 {:label "assisted-assess" :writes #{:property/intake :jurisdiction/assess}          :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:property/intake :fee/file}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:fee/pay`/`:contract/execute` are never auto-eligible at any phase,
    so they always escalate once the governor clears them (or hold if
    the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Real-Estate Fee-Services Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
