(ns realty.kumiai.phase
  "Phase 0->3 staged rollout for the condominium-association actor --
  the same shape as `realty.phase`, with one op that is permanently
  outside every `:auto` set.

    Phase 0  read-only         -- no writes, still governor-gated.
    Phase 1  assisted-intake   -- association intake only.
    Phase 2  assisted-plan     -- adds plan assessment and reserve
                                  simulation.
    Phase 3  supervised auto   -- all writes permitted; only
                                  `:association/intake` and
                                  `:reserve/simulate` may auto-commit.

  `:works/commission` is the actuation: a real contract with a real
  contractor for a major repair, paid out of other people's reserve
  contributions. It is absent from every phase's `:auto` set, phase 3
  included -- a permanent structural fact, not a milestone still to
  come, and `realty.kumiai.governor` enforces the same invariant
  independently through `:actuation/commission-works`.

  `:resolution/file` is also never auto, for a different reason: it
  moves no money, but it is the record that everything downstream
  treats as authority. A wrongly recorded minute does not misstate a
  fact so much as manufacture one.")

(def read-ops #{})
(def write-ops #{:association/intake :plan/assess :reserve/simulate
                 :resolution/file :works/commission})

;; INVARIANT: `:works/commission` and `:resolution/file` are members of
;; `write-ops` but are never members of any `:auto` set below.
(def phases
  {0 {:label "read-only"       :writes #{}                                    :auto #{}}
   1 {:label "assisted-intake" :writes #{:association/intake}                 :auto #{}}
   2 {:label "assisted-plan"   :writes #{:association/intake :plan/assess :reserve/simulate}
      :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:association/intake :reserve/simulate}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. A governor HOLD
  always stays HOLD; the phase gate can only add caution, never remove
  it."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)  {:disposition :hold :reason nil}
      (contains? read-ops op)         {:disposition governor-disposition :reason nil}
      (not (contains? writes op))     {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op))) {:disposition :escalate :reason :phase-approval}
      :else                           {:disposition governor-disposition :reason nil})))

(defn verdict->disposition [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))

(defn auto-eligible?
  "Exposed so a test can assert the invariant directly rather than by
  reading the map literal above."
  [phase op]
  (contains? (get-in phases [phase :auto] #{}) op))
