(ns realty.kumiai.resolution
  "Pure judgement of ONE general-meeting resolution against ONE
  statutory rule from `realty.kumiai.facts`.

  This is the namespace the Condominium-Association Governor uses to
  answer, independently of anything the advisor claimed: did this
  resolution actually pass?

  Counting yes votes is the easy part. The hard part is that the SHAPE
  of the rule differs per statute, and an implementation that assumes
  one shape will answer confidently and wrongly under another. The
  shapes this namespace has to carry, each because a real statute
  demands it:

  1. THE DENOMINATOR IS PART OF THE LAW, and there are three of them.
     Japan's 第39条第1項 counts the ATTENDING members (and has since the
     令和7年改正 took effect on 2026-04-01); 第62条第1項 counts ALL
     members; Germany's WEG § 25 Absatz 1 and France's article 24 count
     the votes actually CAST -- `abgegebene Stimmen` / `voix
     exprimées` -- which excludes abstentions and so is smaller again.
     Same ballot, three denominators, three possible answers, and the
     arithmetic looks impeccable under each.

  2. EACH AXIS CAN CARRY ITS OWN FRACTION -- AND ITS OWN DENOMINATOR.
     France's article 26 is `la majorité des membres du syndicat
     représentant au moins les deux tiers des voix`: more than half the
     members AND at least two thirds of the voices. Germany's
     WEG § 21 Absatz 2 Nummer 1 is `mehr als zwei Dritteln der
     abgegebenen Stimmen und der Hälfte aller Miteigentumsanteile` --
     two thirds of the votes CAST and half of ALL co-ownership shares:
     different fraction AND different denominator on the two axes of
     one rule. A model with one fraction per rule cannot express
     either, and a model with one denominator per rule would put the
     German shares axis at 4,000/6,000 instead of 4,000/10,000 and pass
     a resolution that failed.

  3. EVERY AXIS MUST CLEAR ITS BAR INDEPENDENTLY. Clearing voting
     rights and missing heads is not a pass. Counting only the axis
     that is easy to obtain is the commonest real-world error.

  4. A QUORUM IS A SEPARATE STAGE. Japan's 第17条第1項 / 第31条第1項 /
     第61条第5項 require an attendance threshold BEFORE the
     supermajority is counted at all.

  5. SOME RULES HAVE NO BASE AT ALL. Singapore's ordinary resolution
     (Building (Strata Management) Act 2004, s 2(2)(b)) is decided by
     comparing the valid votes FOR against the valid votes AGAINST --
     `more than the valid votes counted against the motion`. There is
     no fraction and no denominator: abstentions are simply absent from
     both sides, and a TIE fails. A model that had to express every
     rule as a fraction of something would have to invent a denominator
     here, and the invented one would disagree with the statute as soon
     as anyone abstained.

  6. A THRESHOLD CAN BE SET ON THE OPPOSITION. New South Wales defines
     a special resolution as one where, of the value of votes cast,
     `not more than 25% are against` (Strata Schemes Management Act
     2015, s 5(1)) -- and a unanimous resolution as one where `no vote
     is cast against`. Nothing in either sentence mentions the votes in
     favour. Rewriting them as `at least 75% for` is algebra that holds
     only while every vote cast is either for or against, which is an
     assumption about the ballot and not something the statute says. So
     these are measured on the AGAINST tally, and the verdict reports
     the maximum opposition the rule allowed.

  7. A NOTICE PERIOD CAN BE PART OF THE DEFINITION, not evidence
     around it. The same Singapore section says a motion `is decided by
     ordinary resolution IF (a) the motion is passed at a duly convened
     general meeting held on the 15th day (or later) after the notice
     ... AND (b) the votes ...`. A motion carried on the 14th day is
     not a narrowly-failed ordinary resolution; it is not an ordinary
     resolution at all. So `:notice-days` is checked here and lands in
     `:passed?`, rather than being left to the evidence checklist.

  8. SOME STATUTES PROVIDE A FALLBACK. France's article 25-1 lets an
     assembly that failed the article 25 majority, but reached at least
     a third of all owners' votes, vote AGAIN immediately at the
     article 24 majority. That is a second ballot, not an arithmetic
     consequence, so this namespace reports `:fallback` and never
     silently applies it -- an actor that 'passed' a resolution on a
     vote that never happened would be manufacturing a fact.

  Comparisons are exact: fractions are carried as {:numer :denom} and
  compared by cross-multiplication, never as floating-point ratios, and
  never as ClojureScript-unreadable Ratio literals. `過半数` /
  `Mehrheit` / `majorité` / `mayoría` is a STRICT majority
  (`:greater-than`); `N分のM以上` / `mindestens` / `au moins` / `las
  tres quintas partes` is `:at-least`. A tally that lands EXACTLY on
  the line is flagged `:on-boundary?` so a human sees it -- one proxy
  form flips such a vote."
  (:require [clojure.string :as str]))

;; ----------------------------- exact fraction comparison -----------------------------

(defn meets?
  "Does `n` clear `fraction` of `base` under `comparison`?
  Cross-multiplied, so 2/3 of 99 is exact on every runtime."
  [n base {:keys [numer denom]} comparison]
  (let [lhs (* (double n) denom)
        rhs (* (double base) numer)]
    (if (= :greater-than comparison) (> lhs rhs) (>= lhs rhs))))

(defn on-boundary?
  "Is `n` EXACTLY `fraction` of `base`? Such a tally passes an
  `:at-least` rule and fails a `:greater-than` rule by a single vote,
  so it is always worth a human's eye."
  [n base {:keys [numer denom]}]
  (== (* (double n) denom) (* (double base) numer)))

(defn required
  "The smallest tally that clears the bar. Integer axes (heads) get an
  integer answer; continuous axes (voting weight apportioned by floor
  area, by co-ownership share or by cuota de participación) get the
  exact real threshold."
  [base {:keys [numer denom]} comparison integral?]
  (let [exact (/ (* (double base) numer) denom)]
    (if integral?
      (if (= :greater-than comparison)
        (inc (Math/floor exact))
        (Math/ceil exact))
      exact)))

;; ----------------------------- axes -----------------------------

(def ^:private integral-axes
  "Axes counted in whole people. The others are continuous quantities:
  `:voting-rights` is apportioned by exclusive floor area (JPN 第38条),
  or is the `voix` of a French syndicat or the `cuota de
  participación` of a Spanish comunidad; `:co-ownership-shares` is
  WEG Miteigentumsanteile; `:land-use-right-value` is a value."
  #{:owners :valid-votes})

(defn- normalize-axes
  "One axis descriptor per axis, with the rule's own values filled in.

  An axis may be a bare keyword -- it then takes the rule's base,
  fraction and comparison -- or a map overriding any of them. An axis
  that carries its OWN `:fraction` is a statutory constant on that
  axis: a rule-level relaxation or instrument override moves the rule's
  fraction, not that one."
  [rule effective-fraction]
  (mapv (fn [a]
          (let [m (if (keyword? a) {:axis a} a)]
            {:axis (:axis m)
             :base (or (:base m) (:base rule))
             :fraction (or (:fraction m) effective-fraction)
             :comparison (or (:comparison m) (:comparison rule))}))
        (:axes rule)))

;; ----------------------------- effective fraction -----------------------------

(defn- bylaw-permits?
  "May the association's own instrument move this rule's threshold to
  `fraction`? `:bylaw` says what the statute allows:

    :none            -- no instrument may change it (JPN 第31条第1項,
                        the Spanish and French rules, and every rule
                        whose axes carry their own fractions, since
                        there is no single rule-level fraction to move)
    :lower-to-above-half
                     -- may be lowered, but only to a fraction strictly
                        above one half (JPN 第17条第1項)
    :raise-only      -- may only be raised (the quorum rules)
    :agreement-only  -- may be changed, but only by a recorded
                        AGREEMENT (WEG Vereinbarung /
                        Gemeinschaftsordnung), never by a resolution of
                        the meeting itself
    :any             -- 別段の定め permitted (JPN 第39条第1項)"
  [{:keys [bylaw] :as rule} {:keys [numer denom] :as fraction} override]
  (let [statutory (:fraction rule)]
    (if (nil? statutory)
      false
      (let [lower? (< (* (double numer) (:denom statutory)) (* (double (:numer statutory)) denom))
            above-half? (> (* 2.0 numer) denom)]
        (case bylaw
          :none false
          :any true
          :agreement-only (= :agreement (:instrument override))
          :raise-only (not lower?)
          :lower-to-above-half (and (or lower? (= fraction statutory)) above-half?)
          false)))))

(defn effective-fraction
  "The fraction that actually governs this vote, plus why.

  Two things can move it off the statutory default, and they are NOT
  symmetric:

    - a statutory RELAXATION (JPN 第62条第2項 各号) applies when the
      building meets one of the listed physical conditions. It is a
      fact about the building, so it must be evidenced, not asserted.
    - an instrument override applies only where the statute lets the
      association's own instrument move it AND the association actually
      recorded such a provision. A claimed override with
      `:recorded? false` is refused here rather than silently honoured
      -- otherwise 'our bylaws say two thirds' becomes an unfalsifiable
      way to lower any bar."
  [rule {:keys [relaxation-conditions bylaw-override]}]
  (let [relaxed (:relaxed rule)
        relax? (and relaxed (seq relaxation-conditions)
                    (or (nil? (:conditions relaxed))
                        (seq (filter (set (:conditions relaxed)) relaxation-conditions))))
        base-fraction (if relax? (:fraction relaxed) (:fraction rule))
        base-source (if relax? {:source :statutory-relaxation
                                :article (:article relaxed)
                                :conditions (vec (sort (filter (set (:conditions relaxed))
                                                               (or relaxation-conditions #{}))))}
                        {:source :statutory :article (:article rule)})]
    (cond
      (nil? bylaw-override)
      (assoc base-source :fraction base-fraction)

      (not (:recorded? bylaw-override))
      (assoc base-source :fraction base-fraction
             :rejected-override {:reason :bylaw-override-not-recorded
                                 :claimed (:fraction bylaw-override)})

      (not (bylaw-permits? rule (:fraction bylaw-override) bylaw-override))
      (assoc base-source :fraction base-fraction
             :rejected-override {:reason :bylaw-override-not-permitted
                                 :claimed (:fraction bylaw-override)
                                 :allowed (:bylaw rule)})

      :else
      {:fraction (:fraction bylaw-override)
       :source :bylaw
       :article (:article rule)
       :provision (:provision bylaw-override)})))

;; ----------------------------- validation -----------------------------

(def ^:private base-label
  {:total "総数 / all members" :attending "出席者 / attending" :cast "投票 / votes cast"})

(defn- validate! [rule {:keys [total attending cast in-favour against notice-days-elapsed]}]
  (let [axes (normalize-axes rule (:fraction rule))
        counts {:total total :attending attending :cast cast}
        needed (cond-> (into #{} (map :base axes))
                 (:quorum rule) (into [:total :attending]))
        quorum-axes (when-let [q (:quorum rule)]
                      (if (= :any (:mode q)) (mapv :axis (:disjuncts q)) (:axes q)))]
    (doseq [b needed]
      (when (nil? (get counts b))
        (throw (ex-info (str "resolution: this rule counts the " (name b)
                             " base, so those counts are required")
                        {:article (:article rule) :base b}))))
    (doseq [axis quorum-axes]
      ;; Every disjunct must be MEASURABLE even though only one need be
      ;; met -- otherwise a missing count would silently remove a way of
      ;; seating the meeting.
      (when (or (nil? (get total axis)) (nil? (get attending axis)))
        (throw (ex-info (str "resolution: the quorum counts axis " axis
                             ", so total and attending are required for it")
                        {:axis axis :article (:article rule)}))))
    (when (and (:notice-days rule) (nil? notice-days-elapsed))
      (throw (ex-info (str "resolution: this rule defines itself partly by a notice period, "
                           "so :notice-days-elapsed is required")
                      {:article (:article rule) :notice-days (:notice-days rule)})))
    (doseq [{:keys [axis comparison]} axes]
      (when (and (contains? #{:more-than-opposed :opposition-at-most :opposition-less-than}
                            (or comparison (:comparison rule)))
                 (nil? (get against axis)))
        (throw (ex-info (str "resolution: this rule is decided on the votes AGAINST, "
                             "so :against is required on axis " axis)
                        {:axis axis :article (:article rule)}))))
    (doseq [{:keys [axis base comparison]} axes]
      (let [t (get total axis) f (get in-favour axis) b (get (get counts base) axis)
            opposition? (contains? #{:opposition-at-most :opposition-less-than}
                                   (or comparison (:comparison rule)))]
        (when (nil? t) (throw (ex-info (str "resolution: total is missing axis " axis) {:axis axis})))
        (when (nil? b) (throw (ex-info (str "resolution: " (name base) " is missing axis " axis)
                                       {:axis axis :base base})))
        (when (and (nil? f) (not opposition?))
          (throw (ex-info (str "resolution: in-favour is missing axis " axis) {:axis axis})))
        (when (neg? (double t)) (throw (ex-info "resolution: counts must be >= 0" {:axis axis})))
        (when (> (double b) (double t))
          (throw (ex-info (str "resolution: " (name base) " exceeds total on axis " axis) {:axis axis})))
        (when (and f (> (double f) (double b)))
          (throw (ex-info (str "resolution: in-favour exceeds the " (name base) " base on axis " axis)
                          {:axis axis :base base})))
        (when-let [ag (get against axis)]
          (when (> (+ (double (or f 0)) (double ag)) (double b))
            (throw (ex-info (str "resolution: for + against exceeds the " (name base)
                                 " base on axis " axis)
                            {:axis axis :base base}))))))
    ;; Votes cast cannot exceed attendance, wherever both are supplied:
    ;; a ballot cannot record more votes than there were people to cast
    ;; them. Checked across ALL supplied axes, not only the ones this
    ;; rule happens to count.
    (when (and cast attending)
      (doseq [[axis c] cast]
        (when-let [a (get attending axis)]
          (when (> (double c) (double a))
            (throw (ex-info (str "resolution: votes cast exceed attendance on axis " axis)
                            {:axis axis}))))))))

(defn- excluded-base
  "JPN 第38条の2 lets a court exclude an owner whose identity or
  whereabouts cannot be established from the denominator of EVERY
  resolution. That is a court's act, not the association's: an
  exclusion asserted without `:court-ordered? true` is ignored here
  (and reported), so it cannot be used to shrink a denominator into a
  pass."
  [total {:keys [count-by-axis court-ordered?] :as exclusion}]
  (if (and exclusion court-ordered? (map? count-by-axis))
    [(reduce-kv (fn [m axis v] (update m axis #(- (double (or % 0)) (double v)))) total count-by-axis)
     {:applied? true :article "建物の区分所有等に関する法律 第38条の2" :count-by-axis count-by-axis}]
    [total (when exclusion {:applied? false :reason :not-court-ordered})]))

(defn- axis-result
  "One axis judged. Two kinds of comparison land here:

    a FRACTION of a base (every civil-law rule in this catalog), and
    FOR versus AGAINST (Singapore's ordinary resolution), where there
    is no base to take a fraction of and a tie fails.

  The second is not the first with a base of `for + against`: valid
  votes are only those two, but the statute compares the two tallies
  directly, and writing it as `for > (for+against)/2` would give the
  same answer only because the algebra happens to agree -- and would
  quietly stop agreeing the moment a statute weighted them
  differently."
  [{:keys [axis base fraction comparison]} base-count in-favour against]
  (let [integral? (contains? integral-axes axis)]
    (if (contains? #{:opposition-at-most :opposition-less-than} comparison)
      ;; Measured on the AGAINST tally. `:in-favour` is carried for the
      ;; record but takes no part in the verdict -- the statute does not
      ;; mention it, and inferring it would be arithmetic the law did
      ;; not authorise.
      (let [ag (double (or against 0))
            cap (/ (* (double base-count) (:numer fraction)) (:denom fraction))]
        {:axis axis
         :counted-against base
         :base base-count
         :in-favour in-favour
         :opposed against
         :fraction fraction
         :comparison comparison
         :max-opposition cap
         :required nil
         :met? (if (= :opposition-at-most comparison) (<= ag cap) (< ag cap))
         :on-boundary? (== ag cap)})
    (if (= :more-than-opposed comparison)
      (let [a (double (or against 0))]
        {:axis axis
         :counted-against base
         :base base-count
         :in-favour in-favour
         :opposed against
         :fraction nil
         :comparison comparison
         :required (if integral? (inc a) a)
         :met? (> (double in-favour) a)
         :on-boundary? (== (double in-favour) a)})
      {:axis axis
       :counted-against base
       :base base-count
       :in-favour in-favour
       :fraction fraction
       :comparison comparison
       :required (required base-count fraction comparison integral?)
       :met? (meets? in-favour base-count fraction comparison)
       :on-boundary? (on-boundary? in-favour base-count fraction)}))))

;; ----------------------------- tally -----------------------------

(defn- fallback-for
  "Some statutes let a failed resolution be re-voted at once under a
  lower majority (FRA article 25-1). This reports only that the
  statutory PRECONDITION is met. It never applies the fallback: the
  second ballot is an event that either happened or did not, and
  inferring it would manufacture a vote."
  [rule total in-favour passed?]
  (when-let [{:keys [threshold axes] :as fb} (:fallback rule)]
    (when-not passed?
      (when (every? (fn [axis] (meets? (get in-favour axis) (get total axis) threshold :at-least))
                    axes)
        {:available? true
         :article (:article fb)
         :to (:to fb)
         :threshold threshold
         :note (:note fb)}))))

(defn tally
  "Judge one resolution. Returns a full, auditable verdict -- never a
  bare boolean, because 'it failed' is only useful next to WHICH axis
  failed and against WHICH denominator.

  `input`:
    :total       {axis -> count}   -- every member
    :attending   {axis -> count}   -- present (incl. proxies and
                                      written votes where the statute
                                      counts them as attendance, e.g.
                                      JPN 第39条第2項)
    :cast        {axis -> count}   -- votes actually cast (abstentions
                                      excluded); required by rules
                                      counting `abgegebene Stimmen` or
                                      `voix exprimées`
    :in-favour   {axis -> count}
    :against     {axis -> count}   -- required by rules that compare the
                                      two tallies directly rather than
                                      taking a fraction of a base
    :notice-days-elapsed n         -- required where the statute makes a
                                      notice period part of the
                                      definition of the resolution
    :exclusion   {:court-ordered? bool :count-by-axis {axis -> n}}
    :relaxation-conditions #{..}
    :bylaw-override {:fraction {..} :recorded? bool :provision \"..\"
                     :instrument :agreement|:bylaw|:resolution}"
  [rule input]
  (when (nil? rule)
    (throw (ex-info "resolution/tally: no statutory rule -- the caller must HOLD, not guess" {})))
  (validate! rule input)
  (let [[total exclusion-note] (excluded-base (:total input) (:exclusion input))
        counts {:total total :attending (:attending input) :cast (:cast input)}
        eff (effective-fraction rule input)
        axes-spec (normalize-axes rule (:fraction eff))
        quorum (when-let [q (:quorum rule)]
                 ;; Two shapes. The default is CONJUNCTIVE: every axis
                 ;; must be met (JPN 第17条第1項 needs a majority of the
                 ;; owners AND of the voting rights to attend). NSW
                 ;; Schedule 1 clause 17(2) is DISJUNCTIVE: a quorum
                 ;; exists if a quarter of the persons OR a quarter of
                 ;; the aggregate unit entitlement is present. Folding
                 ;; the second into the first would refuse meetings the
                 ;; statute seats.
                 (let [any? (= :any (:mode q))
                       specs (if any?
                               (mapv #(merge {:base :attending} %) (:disjuncts q))
                               (mapv (fn [axis] {:axis axis :base :attending
                                                 :fraction (:fraction q)
                                                 :comparison (:comparison q)})
                                     (:axes q)))
                       rs (mapv (fn [spec]
                                  (axis-result spec (get total (:axis spec))
                                               (get (:attending input) (:axis spec)) nil))
                                specs)]
                   {:required-of :total
                    :mode (if any? :any :all)
                    :article (:article q)
                    :fraction (:fraction q)
                    :comparison (:comparison q)
                    :axes rs
                    :met? (if any? (boolean (some :met? rs)) (every? :met? rs))}))
        axes (mapv (fn [spec]
                     (axis-result spec
                                  (get (get counts (:base spec)) (:axis spec))
                                  (get (:in-favour input) (:axis spec))
                                  (get (:against input) (:axis spec))))
                   axes-spec)
        notice (when-let [d (:notice-days rule)]
                 {:required-days d
                  :elapsed-days (:notice-days-elapsed input)
                  :met? (>= (double (:notice-days-elapsed input)) (double d))})
        quorum-ok? (or (nil? quorum) (:met? quorum))
        notice-ok? (or (nil? notice) (:met? notice))
        axes-ok? (every? :met? axes)
        passed? (and quorum-ok? notice-ok? axes-ok?)]
    {:passed? passed?
     :article (:article rule)
     :label (:label rule)
     :base (:base rule)
     :bases (vec (distinct (map :counted-against axes)))
     :comparison (:comparison rule)
     :effective-fraction eff
     :quorum quorum
     :notice notice
     :axes axes
     :exclusion exclusion-note
     :fallback (fallback-for rule total (:in-favour input) passed?)
     :on-boundary? (boolean (some :on-boundary? (concat axes (:axes quorum))))
     :failed-axes (mapv :axis (remove :met? axes))
     :reasons (cond-> []
                (not quorum-ok?) (conj :quorum-unmet)
                (not notice-ok?) (conj :notice-period-unmet)
                (not axes-ok?) (conj :threshold-unmet)
                (:rejected-override eff) (conj (get-in eff [:rejected-override :reason]))
                (and (:exclusion input) (not (:applied? exclusion-note))) (conj :exclusion-not-court-ordered))}))

(defn explain
  "One-paragraph human explanation of a `tally` verdict, for the audit
  ledger and the operator console. Prints the fraction AND the
  denominator PER AXIS, because under WEG § 21 Absatz 2 and loi 65-557
  article 26 those differ between the axes of a single rule -- a line
  that printed one fraction for the rule would be describing a rule
  that does not exist."
  [{:keys [passed? article label quorum notice axes on-boundary? fallback]}]
  (str label " (" article ")"
       (when quorum (str " / 定足数: " (if (:met? quorum) "充足" "不足")))
       (when notice (str " / 通知期間: " (:elapsed-days notice) "日 (要 " (:required-days notice) "日) "
                         (if (:met? notice) "○" "×")))
       " / 軸: "
       (str/join "、" (map (fn [a]
                             (if (contains? #{:opposition-at-most :opposition-less-than} (:comparison a))
                               (str (name (:axis a)) " 反対 " (:opposed a) "/" (:base a)
                                    " [" (get base-label (:counted-against a) "?") "]"
                                    " 上限 " (:max-opposition a)
                                    " (" (:numer (:fraction a)) "/" (:denom (:fraction a))
                                    (if (= :opposition-at-most (:comparison a)) "以下" "未満") ")"
                                    " " (if (:met? a) "○" "×"))
                             (if (= :more-than-opposed (:comparison a))
                               (str (name (:axis a)) " 賛成 " (:in-favour a)
                                    " 対 反対 " (:opposed a)
                                    " (基準無し・同数は否決) " (if (:met? a) "○" "×"))
                               (str (name (:axis a)) " " (:in-favour a) "/" (:base a)
                                    " [" (get base-label (:counted-against a) "?") "]"
                                    " 要 " (:required a)
                                    " (" (:numer (:fraction a)) "/" (:denom (:fraction a)) ")"
                                    " " (if (:met? a) "○" "×")))))
                           axes))
       " => " (if passed? "可決" "否決")
       (when on-boundary? " [要件ちょうどの軸あり -- 人的確認を推奨]")
       (when fallback (str " [" (:article fallback) " により同一総会での再決議が可能 -- 自動可決はしない]"))))
