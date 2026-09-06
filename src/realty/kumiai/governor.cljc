(ns realty.kumiai.governor
  "Condominium-Association Governor -- the independent compliance layer
  that earns the Kumiai-LLM the right to commit.

  The advisor has no way to know, on its own:

    - which jurisdictions have a STATUTORY resolution threshold at all
      (most do not -- the thresholds live in the association's own
      declaration or bylaws), so a confident answer there is a
      fabrication with no tell;
    - that Japan's ordinary-resolution DENOMINATOR moved from the total
      membership to the ATTENDING membership on 2026-04-01, so the same
      vote counts differently before and after and the arithmetic looks
      correct either way;
    - that a long-term repair plan shorter than the guideline's own
      sample horizon cannot be compared to the guideline's benchmark
      band at all;
    - whether a claimed reserve deficit survives an independent
      recompute under the SAME escalation and slippage assumptions;
    - that a works item slipping past the end of the plan makes the
      projected balance IMPROVE, so a plan can be made to look funded
      by delaying it;
    - or when a draft stops being a draft and becomes a real contract
      with a real contractor, paid out of other owners' reserve
      contributions.

  So this MUST be a separate system able to reject a proposal and fall
  back to HOLD.

  Twelve HARD checks (a human approver cannot override any of them) and
  a soft gate. HARD checks are ordered so an existence check runs
  before any check that depends on the entity existing -- the lesson
  `pension.governor`'s ADR-0001 records, applied proactively: scoping a
  spec-basis check to an op that ALSO carries a 'missing entity' check
  without guarding on existence first makes the wrong rule co-fire.

     1. Spec-basis                    -- did the proposal cite an
                                         OFFICIAL source for this
                                         jurisdiction, or invent one?
     2. Association not under
        management                    -- nothing may be planned,
                                         resolved or commissioned for
                                         an association not taken on.
     3. Threshold unverified          -- `:resolution/file` for a
                                         (jurisdiction, kind) with no
                                         statutory rule in `facts`.
                                         HOLD, never a default majority.
     4. Resolution tally mismatch     -- independently re-tally the vote
                                         with `realty.kumiai.resolution`
                                         and compare to the claim. Also
                                         catches a claim counted against
                                         the WRONG DENOMINATOR even when
                                         its pass/fail happens to agree.
     5. Plan not comparable           -- horizon or major-repair-cycle
                                         count below the guideline's own
                                         sample preconditions.
     6. Reserve simulation mismatch   -- independently re-project the
                                         reserve and compare the claimed
                                         deficit. EXACT match, like
                                         `realty.governor`'s fee check.
     7. Unmeasured outflow            -- works slipped past the plan
                                         horizon. A cheaper-looking plan
                                         that got that way by not
                                         counting something is not a
                                         cheaper plan.
     8. No projection on file       -- `:works/commission` for an
                                         association with no committed
                                         reserve projection.
     9. Works not authorised          -- `:works/commission` with no
                                         pending package, or whose
                                         authorising resolution did not
                                         pass. Doubles as the double-
                                         commissioning guard.
    10. Works exceeds resolution
        budget                        -- the order's value against the
                                         budget the meeting actually
                                         voted.
    11. Reserve cannot fund the works -- the independent projection goes
                                         negative and the resolution
                                         carries no borrowing or lump-sum
                                         funding to cover it.
    12. Evidence incomplete           -- the jurisdiction's required
                                         documents for a works order.

    13. Confidence floor / actuation gate (SOFT) -- low confidence, a
        resolution that landed EXACTLY on its threshold, a reserve level
        outside the guideline band, or the actuation op itself, all
        escalate to a human.

  A note on what is deliberately NOT hard: a reserve level outside the
  published benchmark band. The guideline says in terms that being
  outside the band does not by itself make the level improper -- it says
  to go and look at the plan. Holding on it would put this actor's
  opinion above the guideline's own words, so it escalates instead."
  (:require [realty.kumiai.facts :as facts]
            [realty.kumiai.reserve :as reserve]
            [realty.kumiai.resolution :as resolution]
            [realty.kumiai.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Commissioning major repair works is the one real-world actuation this
  actor performs."
  #{:actuation/commission-works})

(def ^:private plan-ops #{:plan/assess :reserve/simulate})
(def ^:private yen-tolerance
  "One yen. The reserve projection is money, and an exact-match rule on
  floating-point money would fire on representation noise rather than on
  a real disagreement."
  1.0)

;; ----------------------------- helpers -----------------------------

(defn- assoc-of
  "The association a request is about, whichever way the op names it."
  [{:keys [op subject association-id]} st]
  (store/association st (if (= op :resolution/file) association-id subject)))

(defn- safe-tally
  "Re-tally, but never let a malformed tally input crash the actor.

  `resolution/tally` throws on structurally impossible input (in-favour
  above the base, attendance above the membership, a missing axis).
  Those are real defects in the proposal, and the right answer to them
  is a HARD violation naming the defect -- not an exception escaping
  the governor, which would look identical to the actor being down."
  [rule input]
  (try
    [:ok (resolution/tally rule input)]
    (catch #?(:clj Exception :cljs :default) e
      [:error #?(:clj (.getMessage ^Exception e) :cljs (ex-message e))])))

(defn- effective-assumptions
  "Which escalation/slippage assumptions the funding checks run under.

  For a SIMULATION the request supplies them -- that is the question
  being asked. For `:works/commission` they come from the association's
  last COMMITTED projection instead, and there is no fall-back to the
  request.

  The reason is that an operator who could hand the governor its own
  assumptions could clear the funding check by asking about a world
  with no cost escalation in it. Reading them from the committed
  projection means the numbers the works order is judged against are
  the ones the board actually adopted and the ledger actually holds.
  No projection on file is therefore a HARD violation, not a default of
  zero -- see `no-projection-violations`.

  What this does NOT do, and cannot: decide which escalation rate is
  the right one. A board that re-runs the projection at a rosier rate
  and adopts THAT can clear the funding check. The guard is not that
  the assumptions are correct; it is that they are on the record, under
  the board's name, in an append-only ledger, next to the works order
  they authorised. Choosing 0% escalation in 2026 remains the board's
  decision -- it just stops being an invisible one."
  [{:keys [op assumptions]} a st]
  (if (= op :works/commission)
    (:assumptions (store/projection-of st (:id a)))
    assumptions))

(defn- projected
  "This actor's OWN projection for an association -- never the
  advisor's. Returns nil when the association has no plan on file."
  [st a assumptions]
  (when-let [plan (store/plan-of st (:id a))]
    (let [p (reserve/project a plan assumptions)]
      {:plan plan :projection p :shortfall (reserve/shortfall p)})))

;; ----------------------------- HARD checks -----------------------------

(defn- cites-are-checkable?
  "Is a missing citation actually evidence of fabrication for THIS
  request, or just the downstream shape of a different, more specific
  defect?

  The advisor returns no citations when it has nothing to cite -- a
  works order with no pending package, a resolution whose jurisdiction
  has no statutory rule. Firing `:no-spec-basis` there co-fires the
  wrong rule alongside the right one and buries the accurate diagnosis
  under a generic one. `pension.governor`'s ADR-0001 records exactly
  this failure; the fix is to guard on the entity the citation would be
  ABOUT, before asking whether it was cited."
  [{:keys [op resolution-kind]} a]
  (case op
    :works/commission (some? (:pending-works a))
    :resolution/file  (some? (facts/resolution-rule (:jurisdiction a) resolution-kind))
    true))

(defn- spec-basis-violations
  "Every write op needs an OFFICIAL spec-basis for the association's
  jurisdiction. Guarded twice: on the association existing (so a
  missing one reports 'not under management' instead), and on the
  citation being checkable at all (see `cites-are-checkable?`)."
  [{:keys [op] :as request} proposal st]
  (when (contains? (into plan-ops #{:resolution/file :works/commission}) op)
    (when-let [a (assoc-of request st)]
      (when (cites-are-checkable? request a)
        (when (or (empty? (:cites proposal))
                  (nil? (facts/spec-basis (:jurisdiction a))))
          [{:rule :no-spec-basis
            :detail (str (:jurisdiction a) " について公式の spec-basis 引用が無い提案は、"
                         "区分所有法制の要件として扱えない")}])))))

(defn- association-not-under-management-violations
  [{:keys [op] :as request} st]
  (when (contains? (into plan-ops #{:resolution/file :works/commission}) op)
    (let [a (assoc-of request st)]
      (when-not (= :under-management (:status a))
        [{:rule :association-not-under-management
          :detail (str (or (:id a) (:subject request))
                       " は管理受託(under-management)されていないため、計画・決議・発注は受理できない")}]))))

(defn- threshold-unverified-violations
  "`:resolution/file` for a (jurisdiction, kind) with no statutory rule
  on file. This is the check that keeps the honest coverage report
  honest: an unverified threshold must not produce the same answer as a
  verified one."
  [{:keys [op resolution-kind] :as request} st]
  (when (= op :resolution/file)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (when-not (facts/resolution-rule (:jurisdiction a) resolution-kind)
          [{:rule :threshold-unverified
            :detail (str (:jurisdiction a) " / " resolution-kind
                         " について検証済みの法定決議要件がこの actor に無い。"
                         "規約・宣言により定まる法域では既定値で代替せず HOLD する")}])))))

(defn- resolution-tally-mismatch-violations
  "Re-tally the vote INDEPENDENTLY and compare with the claim -- both
  the outcome and the denominator it was counted against.

  The denominator comparison is the one that earns its keep. A claim of
  `passed` counted against the total membership can agree with the
  statutory answer by luck on one vote and disagree on the next; if only
  the boolean were compared, the wrong method would keep passing until
  the day it mattered."
  [{:keys [op resolution-kind tally] :as request} proposal st]
  (when (= op :resolution/file)
    (when-let [a (assoc-of request st)]
      (when-let [rule (facts/resolution-rule (:jurisdiction a) resolution-kind)]
        (let [[status ours] (safe-tally rule tally)
              claimed (:tally-verdict (:value proposal))]
          (cond
            (= :error status)
            [{:rule :tally-malformed
              :detail (str "決議の集計値が構造的に成立しない: " ours)}]

            (nil? claimed) nil
            (not= (boolean (:passed? claimed)) (:passed? ours))
            [{:rule :resolution-tally-mismatch
              :detail (str "決議の可否について提案(" (:passed? claimed) ")と独立再計算("
                           (:passed? ours) ")が一致しない -- " (resolution/explain ours))}]

            (and (:base claimed) (not= (keyword (:base claimed)) (:base ours)))
            [{:rule :resolution-denominator-mismatch
              :detail (str "提案は母数を " (name (:base claimed)) " として数えているが、"
                           (:article ours) " が数えるのは " (name (:base ours))
                           " である (令和7年改正 2026-04-01施行)")}]
            :else nil))))))

(defn- plan-not-comparable-violations
  "A plan below the guideline's own sample preconditions cannot be
  compared with the guideline's benchmark. Producing a position against
  the band anyway would look like an assessment and would not be one."
  [{:keys [op] :as request} st]
  (when (contains? plan-ops op)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (when-let [plan (store/plan-of st (:id a))]
          (when-let [c (reserve/plan-conforms-to-horizon? (:jurisdiction a) plan)]
            (when-not (:conforms? c)
              [{:rule :plan-not-comparable
                :detail (str "計画期間 " (:horizon-years c) "年 (要 " (:required-horizon-years c)
                             "年以上) / 大規模修繕 " (:major-repair-cycles c) "回 (要 "
                             (:required-major-repair-cycles c) "回以上) -- "
                             "ガイドラインの目安の前提を満たさない計画は目安と比較できない")}])))))))

(defn- simulation-mismatch-violations
  "Independently re-project the reserve under the SAME assumptions and
  compare the claimed deficit. Never trusts a claimed figure -- the
  discipline `realty.registry/compute-management-fee` establishes,
  applied to a projection rather than a single multiplication."
  [{:keys [op assumptions] :as request} proposal st]
  (when (contains? plan-ops op)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (when-let [{:keys [shortfall]} (projected st a assumptions)]
          (when-let [claimed (get-in proposal [:value :deficit])]
            (when (> (Math/abs (- (double claimed) (double (:deficit shortfall)))) yen-tolerance)
              [{:rule :simulation-mismatch
                :detail (str "提案の不足額(" (Math/round (double claimed))
                             ")が独立再計算値(" (Math/round (double (:deficit shortfall)))
                             ")と一致しない")}])))))))

(defn- unmeasured-outflow-violations
  "Works pushed past the end of the plan horizon by the assumed
  slippage. Their cost is real and is not in the projection, so a
  projection that looks healthier for having deferred them is measuring
  a shorter plan."
  [{:keys [op] :as request} st]
  (when (contains? (conj plan-ops :works/commission) op)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (when-let [{:keys [shortfall]} (projected st a (effective-assumptions request a st))]
          (when (pos? (:unmeasured-outflow shortfall))
            [{:rule :unmeasured-outflow
              :detail (str "計画期間外に押し出された工事 " (pr-str (:deferred-works shortfall))
                           " の費用 " (Math/round (:unmeasured-outflow shortfall))
                           " 円が投影に含まれていない。安く見えているのは測っていないため")}]))))))

(defn- no-projection-violations
  "`:works/commission` with no committed reserve projection for the
  association. Nothing downstream can judge whether the reserve funds
  the works, and 'we did not project it' must not read the same as 'the
  projection was fine'."
  [{:keys [op] :as request} st]
  (when (= op :works/commission)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (when-not (store/projection-of st (:id a))
          [{:rule :no-projection-on-file
            :detail (str (:id a) " について確定した修繕積立金投影が台帳に無い。"
                         "投影していないことを、投影して問題が無かったことと同じに扱わない")}])))))

(defn- works-not-authorised-violations
  "No pending works package, or one whose authorising resolution is
  missing or did not pass. `store/commission-works!` clears
  `:pending-works` on commissioning, so a repeat attempt falls into the
  SAME first branch -- one accurate check covering both occurrences of
  the same underlying fact."
  [{:keys [op] :as request} st]
  (when (= op :works/commission)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (let [pw (:pending-works a)
              r (when pw (store/resolution st (:resolution-id pw)))]
          (cond
            (nil? pw)
            [{:rule :works-not-authorised
              :detail (str (:id a) " には現在発注待ちの工事が登録されていない (発注済みの場合も同じ)")}]
            (nil? r)
            [{:rule :works-not-authorised
              :detail (str "発注根拠として参照された決議 " (:resolution-id pw) " が登録されていない")}]
            (not= :passed (:status r))
            [{:rule :works-not-authorised
              :detail (str "決議 " (:resolution-id pw) " は可決されていない (" (:status r) ")")}]
            (store/works-already-commissioned? st (:id pw))
            [{:rule :double-commissioning
              :detail (str (:id pw) " は既に発注済み")}]
            :else nil))))))

(defn- works-exceeds-budget-violations
  "The order's value against the budget the general meeting actually
  voted. Like `realty.governor/contract-exceeds-authorization-
  violations`, this is a cap against a stored figure -- but the figure
  here came from a vote, so exceeding it is not merely over-budget, it
  is unauthorised."
  [{:keys [op] :as request} st]
  (when (= op :works/commission)
    (when-let [a (assoc-of request st)]
      (when-let [pw (:pending-works a)]
        (when-let [r (store/resolution st (:resolution-id pw))]
          (when (and (= :passed (:status r))
                     (> (double (:contract-value pw)) (double (:budget-amount r))))
            [{:rule :works-exceeds-resolution-budget
              :detail (str "発注額 " (:contract-value pw) " が総会決議の予算額 "
                           (:budget-amount r) " を超過している")}]))))))

(defn- reserve-cannot-fund-violations
  "The independent projection dips below zero and the authorising
  resolution carries no borrowing or lump-sum levy to cover it.
  Commissioning works a reserve demonstrably cannot pay for is not a
  judgement call a human gets to approve past."
  [{:keys [op] :as request} st]
  (when (= op :works/commission)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (when-let [pw (:pending-works a)]
          (when-let [r (store/resolution st (:resolution-id pw))]
            (when-let [{:keys [shortfall]} (projected st a (effective-assumptions request a st))]
              (let [deficit (:deficit shortfall)
                    funding (double (or (:funding-amount r) 0))]
                (when (> deficit (+ funding yen-tolerance))
                  [{:rule :reserve-cannot-fund-works
                    :detail (str "投影上の不足額 " (Math/round deficit) " 円 (最小残高年 "
                                 (:min-balance-year shortfall) "年目) に対し、決議による"
                                 "借入・一時金の裏付けは " (Math/round funding) " 円しかない")}])))))))))

(defn- evidence-incomplete-violations
  [{:keys [op] :as request} st]
  (when (= op :works/commission)
    (when-let [a (assoc-of request st)]
      (when (= :under-management (:status a))
        (let [assessment (store/assessment-of st (:id a))]
          (when-not (and assessment
                         (facts/required-evidence-satisfied?
                          (:jurisdiction a) (:checklist assessment)))
            [{:rule :evidence-incomplete
              :detail "法域の必要書類(管理規約/長期修繕計画書/収支報告/議事録/見積妥当性確認)が充足していない状態での発注提案"}]))))))

;; ----------------------------- SOFT advisories -----------------------------

(defn- advisories
  "Reasons to ask a human to look, which are not reasons to refuse."
  [{:keys [op resolution-kind tally] :as request} st]
  (let [a (assoc-of request st)]
    (cond-> []
      (and (= op :resolution/file) a
           (when-let [rule (facts/resolution-rule (:jurisdiction a) resolution-kind)]
             (let [[status v] (safe-tally rule tally)]
               (and (= :ok status) (:on-boundary? v)))))
      (conj {:advisory :resolution-on-boundary
             :detail "可決要件ちょうどの軸がある -- 委任状1通で結論が反転する"})

      (and (contains? plan-ops op) a (= :under-management (:status a))
           (when-let [plan (store/plan-of st (:id a))]
             (when-let [assessed (reserve/assess-against-benchmark (:jurisdiction a) a plan)]
               (not= :within (:position assessed)))))
      (conj {:advisory :outside-guideline-band
             :detail "積立金水準がガイドラインの目安の幅の外にある。ガイドライン自身が『直ちに不適切ではない』としているため HOLD ではなく人的確認"}))))

;; ----------------------------- check -----------------------------

(defn check
  "Censors a Kumiai-LLM proposal. Returns
   {:ok? bool :violations [..] :advisories [..] :confidence c
    :escalate? bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal st)
                           (association-not-under-management-violations request st)
                           (threshold-unverified-violations request st)
                           (resolution-tally-mismatch-violations request proposal st)
                           (plan-not-comparable-violations request st)
                           (simulation-mismatch-violations request proposal st)
                           (unmeasured-outflow-violations request st)
                           (no-projection-violations request st)
                           (works-not-authorised-violations request st)
                           (works-exceeds-budget-violations request st)
                           (reserve-cannot-fund-violations request st)
                           (evidence-incomplete-violations request st)))
        adv (advisories request st)
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?) (empty? adv))
     :violations   hard
     :advisories   adv
     :confidence   conf
     :hard?        hard?
     :escalate?    (boolean (and (not hard?) (or low? stakes? (seq adv))))
     :high-stakes? stakes?}))

(defn hold-fact
  [request context verdict]
  {:t           :governor-hold
   :op          (:op request)
   :actor       (:actor-id context)
   :subject     (:subject request)
   :disposition :hold
   :basis       (mapv :rule (:violations verdict))
   :violations  (:violations verdict)
   :confidence  (:confidence verdict)})
