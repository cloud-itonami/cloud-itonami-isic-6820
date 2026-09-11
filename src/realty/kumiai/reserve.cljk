(ns realty.kumiai.reserve
  "Reserve-fund arithmetic for a condominium association: does the
  long-term repair plan actually fund itself, once construction-cost
  escalation and schedule slippage are treated as variables rather than
  as things that will not happen?

  This is the namespace the whole kumiai extension exists for. The
  reserve balance a Japanese association reports is a plan drawn at
  some base year against costs quoted at that base year. Two forces
  move it, and they move it in OPPOSITE directions, which is why the
  answer cannot be eyeballed:

    - COST ESCALATION pushes every future works item up compounding
      from the base year to the year the work is actually done.
    - SCHEDULE SLIPPAGE pushes the work later, which buys more months
      of contributions -- but ALSO buys more compounding.

  Deferring a major repair therefore helps or hurts depending on
  whether the contribution rate outruns the escalation rate over the
  deferral, and the crossover is not intuitive.

  == The honest-arithmetic rules this namespace holds itself to ==

  `project` NEVER silently drops a works item. Slipping a year-29 item
  by two years on a thirty-year plan would push it past the horizon,
  the projected balance would improve, and the plan would look BETTER
  for having been delayed. Those items come back in
  `:deferred-beyond-horizon` and `shortfall` refuses to call a plan
  funded while any are outstanding -- a cost that was not measured must
  not read the same as a cost that was measured and covered.

  `required-contribution` solves for the rate by bisection on a
  monotone function rather than by a closed form, because the closed
  form only exists for the level-contribution case and quietly stops
  being right for a staged schedule.

  == Provenance ==

  The guideline arithmetic (`guideline-average`, `benchmark-band`,
  `staged-increase-verdict`) transcribes 国土交通省「マンションの修繕
  積立金に関するガイドライン」(令和6年6月改定). The formulas and the
  benchmark values live in `realty.kumiai.facts/jpn-reserve-guideline`
  with their source URL; this namespace only computes with them. The
  guideline itself states that a figure outside the published band is
  NOT automatically improper, so nothing here reports a band miss as a
  violation -- `realty.kumiai.governor` escalates it to a human
  instead.

  Pure functions, no I/O."
  (:require [realty.kumiai.facts :as facts]))

;; ----------------------------- contributions -----------------------------

(defn contribution-rate
  "The contribution rate (円/㎡・月) in force in plan year `y`.

  Two funding models, both real and both named in the guideline:
    :level  -- 均等積立方式 (`:monthly-per-m2`), the guideline's
               preferred model.
    :staged -- 段階増額積立方式 (`:schedule`, a vector of
               {:from-year y :monthly-per-m2 r} sorted or not), where
               the association plans to raise the rate over time and
               therefore plans to obtain agreement to do so later."
  [{:keys [monthly-per-m2 schedule]} y]
  (if (seq schedule)
    (let [applicable (filter #(<= (:from-year %) y) schedule)]
      (if (seq applicable)
        (:monthly-per-m2 (apply max-key :from-year applicable))
        (:monthly-per-m2 (apply min-key :from-year schedule))))
    (or monthly-per-m2 0)))

(defn annual-income
  "Contributions plus the transfers the guideline lets an association
  count (専用使用料等からの繰入). The guideline warns not to budget
  more transfer income than is actually collected, so this takes the
  figure as given and does not model parking occupancy."
  [{:keys [total-exclusive-area other-transfers-annual] :as association} y]
  (+ (* (double (contribution-rate association y)) (double total-exclusive-area) 12.0)
     (double (or other-transfers-annual 0))))

;; ----------------------------- escalation -----------------------------

(defn- work-delay [assumptions work]
  (double (or (get (:per-work-delay assumptions) (:id work))
              (:delay-years assumptions)
              0)))

(defn escalated-cost
  "A works item's cost in the year it is ACTUALLY carried out.

  cost = base-cost * (1 + escalation) ^ (scheduled-year + delay)

  Years are offsets from the plan's own base year, so the exponent is
  the whole distance from the price quotation to the work -- including
  the slippage. This is the single line that makes a deferral
  expensive."
  [work assumptions]
  (let [esc (double (or (:cost-escalation assumptions) 0))
        y (+ (double (:year work)) (work-delay assumptions work))]
    (* (double (:base-cost work)) (Math/pow (+ 1.0 esc) y))))

(defn executed-year [work assumptions]
  (+ (double (:year work)) (work-delay assumptions work)))

;; ----------------------------- projection -----------------------------

(defn project
  "Year-by-year reserve balance over the plan horizon.

  Returns
    {:rows [{:year :opening :income :interest :outflow :works :closing}]
     :deferred-beyond-horizon [{:work .. :executed-year .. :cost ..}]
     :totals {:income :outflow :deferred-outflow}}

  `:deferred-beyond-horizon` is the load-bearing key: those are works
  that slipped past the last planned year. They are NOT free, and they
  are NOT in `:rows`. Anything that reads `:rows` alone and calls the
  plan solvent has measured a shorter plan, not a healthier one."
  [association plan assumptions]
  (let [horizon (int (:horizon-years plan))
        ret (double (or (:investment-return assumptions) 0))
        by-year (group-by #(int (Math/floor (executed-year % assumptions))) (:works plan))
        beyond (->> (:works plan)
                    (filter #(>= (executed-year % assumptions) horizon))
                    (mapv (fn [w] {:work (:id w) :label (:label w)
                                   :scheduled-year (:year w)
                                   :executed-year (executed-year w assumptions)
                                   :cost (escalated-cost w assumptions)})))]
    (loop [y 0 opening (double (:opening-balance association)) rows [] income-total 0.0 outflow-total 0.0]
      (if (= y horizon)
        {:rows rows
         :deferred-beyond-horizon beyond
         :totals {:income income-total
                  :outflow outflow-total
                  :deferred-outflow (reduce + 0.0 (map :cost beyond))
                  :closing (if (seq rows) (:closing (peek rows)) opening)}}
        (let [income (annual-income association y)
              interest (* opening ret)
              ws (->> (get by-year y []) (remove #(>= (executed-year % assumptions) horizon)))
              outflow (reduce + 0.0 (map #(escalated-cost % assumptions) ws))
              closing (+ opening income interest (- outflow))]
          (recur (inc y) closing
                 (conj rows {:year y
                             :opening opening
                             :income income
                             :interest interest
                             :outflow outflow
                             :works (mapv (fn [w] {:id (:id w) :label (:label w)
                                                   :base-cost (:base-cost w)
                                                   :cost (escalated-cost w assumptions)}) ws)
                             :closing closing})
                 (+ income-total income)
                 (+ outflow-total outflow)))))))

(defn shortfall
  "The funding verdict for a projection.

    :funded?              -- balance never goes negative AND nothing
                             slipped past the horizon.
    :min-balance          -- the deepest point of the curve.
    :deficit              -- how much is missing at that point (0 when
                             the curve never dips below zero).
    :first-negative-year  -- when it first goes negative, or nil.
    :unmeasured-outflow   -- works pushed past the horizon. Non-zero
                             here forces `:funded? false` even when the
                             projected balance is comfortable, because
                             the comfort came from not counting them."
  [projection]
  (let [rows (:rows projection)
        closings (map :closing rows)
        min-row (when (seq rows) (apply min-key :closing rows))
        deferred (:deferred-beyond-horizon projection)
        deferred-cost (reduce + 0.0 (map :cost deferred))
        first-neg (some (fn [r] (when (neg? (:closing r)) (:year r))) rows)]
    {:funded? (and (every? #(>= % 0.0) closings) (empty? deferred))
     :min-balance (when min-row (:closing min-row))
     :min-balance-year (when min-row (:year min-row))
     :deficit (if (and min-row (neg? (:closing min-row))) (- (:closing min-row)) 0.0)
     :first-negative-year first-neg
     :final-balance (get-in projection [:totals :closing])
     :unmeasured-outflow deferred-cost
     :deferred-works (mapv :work deferred)}))

(defn required-contribution
  "The smallest LEVEL contribution rate (円/㎡・月) that keeps the
  balance non-negative for the whole horizon, under the same
  assumptions.

  Bisection, not algebra: the closed form exists only for a level plan
  with no interest, and quietly stops being correct for a staged
  schedule or a non-zero return. Monotone in the rate, so bisection is
  exact to the tolerance and deterministic.

  Returns nil when the answer is meaningless -- an association with no
  exclusive floor area cannot raise a per-square-metre rate at all --
  rather than returning a number that looks like an answer."
  [association plan assumptions]
  (when (pos? (double (:total-exclusive-area association)))
    (let [level (fn [rate]
                  (-> (assoc (dissoc association :schedule) :monthly-per-m2 rate)
                      (project plan assumptions)
                      shortfall))
          ok? (fn [rate] (let [s (level rate)]
                           (and (>= (or (:min-balance s) 0.0) 0.0)
                                (zero? (:unmeasured-outflow s)))))]
      (if-not (ok? 1.0e7)
        ;; Unreachable by contributions alone: works slipped past the
        ;; horizon, so no rate fixes it. Say so, do not return a number.
        {:solvable? false
         :reason :outflow-beyond-horizon
         :deferred-works (:deferred-works (level 1.0e7))}
        (loop [lo 0.0 hi 1.0e7 n 0]
          (if (or (= n 200) (< (- hi lo) 0.01))
            (let [rate hi
                  current (contribution-rate association 0)]
              {:solvable? true
               :monthly-per-m2 rate
               :monthly-per-m2-rounded (Math/ceil rate)
               :current-monthly-per-m2 current
               :increase-ratio (when (pos? (double current)) (/ rate (double current)))
               :monthly-per-unit-average (when (pos? (:units association 0))
                                           (/ (* rate (double (:total-exclusive-area association)))
                                              (double (:units association))))})
            (let [mid (/ (+ lo hi) 2.0)]
              (if (ok? mid) (recur lo mid (inc n)) (recur mid hi (inc n))))))))))

;; ----------------------------- guideline arithmetic -----------------------------

(defn guideline-average
  "計画期間全体における修繕積立金の平均額 Z (円/㎡・月), per the
  guideline's own formula

    Z = (A + B + C) / X / Y

  A = 計画期間当初における修繕積立金の残高
  B = 計画期間全体で集める修繕積立金の総額
  C = 計画期間全体における専用使用料等からの繰入額の総額
  X = マンションの総専有床面積
  Y = 長期修繕計画の計画期間 (ヶ月)

  Note the guideline uses these same letters with DIFFERENT meanings in
  its staged-increase section; `staged-increase-verdict` computes that
  one separately rather than reusing this value, because they are not
  the same quantity (this one includes the opening balance and the
  transfers; that one does not)."
  [association plan]
  (let [horizon (int (:horizon-years plan))
        months (* horizon 12)
        area (double (:total-exclusive-area association))
        contributions (reduce + 0.0 (for [y (range horizon)]
                                      (* (double (contribution-rate association y)) area 12.0)))
        transfers (* (double (or (:other-transfers-annual association) 0)) horizon)]
    (when (and (pos? area) (pos? months))
      {:z (/ (+ (double (:opening-balance association)) contributions transfers) area months)
       :opening-balance (double (:opening-balance association))
       :contributions-total contributions
       :transfers-total transfers
       :months months})))

(defn benchmark-band
  "The guideline's benchmark band for this building, including the
  mechanical-parking add-on the guideline says to add separately.

  Returns nil when this jurisdiction has no reserve guideline in
  `realty.kumiai.facts` -- there is no international benchmark to fall
  back on, and inventing one would be worse than having none."
  [iso3 {:keys [gross-floor-area floors total-exclusive-area mechanical-parking]}]
  (when-let [g (facts/reserve-guideline iso3)]
    (let [high-rise? (>= (double (or floors 0)) (double (get-in g [:high-rise :min-floors])))
          band (if high-rise?
                 (select-keys (:high-rise g) [:low :high :average])
                 (or (when gross-floor-area
                       (first (filter #(and (:upto %) (< (double gross-floor-area) (:upto %)))
                                      (:by-gross-floor-area g))))
                     (last (:by-gross-floor-area g))))
          unit-cost (get-in g [:mechanical-parking (:type mechanical-parking)])
          spaces (double (or (:spaces mechanical-parking) 0))
          add (if (and unit-cost (pos? spaces) (pos? (double total-exclusive-area)))
                (/ (* (double unit-cost) spaces) (double total-exclusive-area))
                0.0)]
      {:low (+ (double (:low band)) add)
       :high (+ (double (:high band)) add)
       :average (+ (double (:average band)) add)
       :parking-add add
       :parking-unit-cost unit-cost
       :parking-type-unknown? (and (:type mechanical-parking) (nil? unit-cost))
       :basis (if high-rise? :high-rise :gross-floor-area)
       :edition (:edition g)
       :provenance (:provenance g)})))

(defn assess-against-benchmark
  "Where does this plan's Z sit against the published band?

  `:within` / `:below` / `:above` -- and NOT a pass/fail. The guideline
  states in terms that a figure outside the band is not automatically
  improper; it says to go and check the plan and the funding method.
  So this returns a position and a caveat, and the governor escalates
  rather than holding."
  [iso3 association plan]
  (when-let [band (benchmark-band iso3 association)]
    (when-let [{:keys [z]} (guideline-average association plan)]
      {:z z
       :band band
       :position (cond (< z (:low band)) :below
                       (> z (:high band)) :above
                       :else :within)
       :caveat "ガイドラインの目安は規模以外の変動要因を織り込んでおらず、幅の外にあることが直ちに不適切を意味するものではない (令和6年6月改定 3(3))。"})))

(defn staged-increase-verdict
  "段階増額積立方式における適切な引上げの考え方 (令和6年6月改定):

    0.6 x D <= E  かつ  1.1 x D >= F

  D = 計画期間全体における月あたりの修繕積立金の平均額
      = A / B / C  (A=計画期間全体で集める総額, B=総専有床面積,
        C=計画期間(月))  -- note this D excludes the opening balance
        and the transfers that `guideline-average`'s Z includes.
  E = 計画期間中の最低額   F = 計画期間中の最高額 (円/㎡・月)

  A level plan trivially conforms (E = F = D). Returns nil for a
  jurisdiction with no guideline."
  [iso3 association plan]
  (when-let [g (facts/reserve-guideline iso3)]
    (let [horizon (int (:horizon-years plan))
          rates (mapv #(double (contribution-rate association %)) (range horizon))
          d (/ (reduce + 0.0 rates) (double (count rates)))
          e (apply min rates)
          f (apply max rates)
          {:keys [initial-min-ratio final-max-ratio]} (:staged-increase g)]
      {:d d :e e :f f
       :initial-ok? (<= (* initial-min-ratio d) e)
       :final-ok? (>= (* final-max-ratio d) f)
       :conforms? (and (<= (* initial-min-ratio d) e) (>= (* final-max-ratio d) f))
       :level? (== e f)
       :initial-min-ratio initial-min-ratio
       :final-max-ratio final-max-ratio
       :edition (:edition g)})))

(defn plan-conforms-to-horizon?
  "Does the plan meet the guideline's own preconditions -- the ones the
  benchmark sample was drawn under? A plan shorter than the minimum
  horizon, or carrying fewer than the minimum number of major repairs,
  is not comparable to the published band at all. Comparing it anyway
  would produce a number that looks like an assessment and is not one."
  [iso3 plan]
  (when-let [g (facts/reserve-guideline iso3)]
    (let [min-years (get-in g [:plan-horizon-years (if (:new-build? plan) :new :existing)])
          cycles (count (filter :major-repair? (:works plan)))]
      {:horizon-years (:horizon-years plan)
       :required-horizon-years min-years
       :horizon-ok? (>= (double (:horizon-years plan)) (double min-years))
       :major-repair-cycles cycles
       :required-major-repair-cycles (:major-repair-cycles-min g)
       :cycles-ok? (>= cycles (:major-repair-cycles-min g))
       :conforms? (and (>= (double (:horizon-years plan)) (double min-years))
                       (>= cycles (:major-repair-cycles-min g)))})))

;; ----------------------------- sensitivity -----------------------------

(defn sensitivity
  "The whole point, in one table: how the deficit moves as construction
  cost escalation and schedule slippage vary, holding everything else
  fixed.

  This is what an association board actually has to decide against --
  not a single projection, but which combinations of escalation and
  delay it can survive."
  [association plan base-assumptions escalations delays]
  (when (seq (:per-work-delay base-assumptions))
    ;; A per-work delay OVERRIDES the global one (see `work-delay`), so
    ;; sweeping `:delay-years` here would leave exactly those works
    ;; unmoved and the table would understate the sensitivity without
    ;; saying so. Refuse rather than return a quietly wrong grid.
    (throw (ex-info "sensitivity: cannot sweep :delay-years while :per-work-delay is set -- the per-work value wins and those works would not move"
                    {:per-work-delay (:per-work-delay base-assumptions)})))
  (vec (for [e escalations d delays]
         (let [a (assoc base-assumptions :cost-escalation e :delay-years d)
               s (shortfall (project association plan a))]
           {:cost-escalation e
            :delay-years d
            :funded? (:funded? s)
            :deficit (:deficit s)
            :min-balance (:min-balance s)
            :first-negative-year (:first-negative-year s)
            :unmeasured-outflow (:unmeasured-outflow s)}))))

(defn worst-case [rows]
  (when (seq rows) (apply max-key #(+ (:deficit %) (:unmeasured-outflow %)) rows)))
