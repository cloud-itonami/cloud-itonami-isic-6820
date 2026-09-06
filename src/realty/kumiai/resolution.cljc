(ns realty.kumiai.resolution
  "Pure judgement of ONE general-meeting resolution against ONE
  statutory rule from `realty.kumiai.facts`.

  This is the namespace the Condominium-Association Governor uses to
  answer, independently of anything the advisor claimed: did this
  resolution actually pass?

  Three things make that question harder than 'count the yes votes',
  and all three are the kind of thing an LLM will get confidently
  wrong:

  1. THE DENOMINATOR IS PART OF THE LAW. 第39条第1項 counts the
     ATTENDING members; 第62条第1項 counts ALL members. Since the
     令和7年改正 came into force (2026-04-01) the ordinary resolution --
     the one a major-repair works order runs on -- moved from the total
     base to the attending base. Counting a passed resolution against
     the old base reports `fail` for something that lawfully passed,
     and the arithmetic looks impeccable either way.

  2. EVERY AXIS MUST CLEAR THE BAR INDEPENDENTLY. 区分所有者の頭数 AND
     議決権 -- and for 第64条の6/第64条の7 also the value of the share
     in the land-use right. A resolution that clears voting rights and
     misses heads has NOT passed. Counting one axis is the single most
     common real-world error, because voting rights are the number that
     is easy to obtain.

  3. A QUORUM IS A SEPARATE STAGE. 第17条第1項 / 第31条第1項 /
     第61条第5項 require an attendance threshold BEFORE the
     supermajority is counted at all. A meeting that reached the
     supermajority of a thin attendance did not pass anything.

  Comparisons are exact: fractions are carried as {:numer :denom} and
  compared by cross-multiplication, never as floating-point ratios, and
  never as ClojureScript-unreadable Ratio literals. `過半数` is a
  STRICT majority (`:greater-than`); `N分のM以上` is `:at-least`. A
  tally that lands EXACTLY on the line is flagged `:on-boundary?` so a
  human sees it -- one miscounted proxy form flips such a vote."
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
  integer answer; continuous axes (voting rights expressed as floor
  area) get the exact real threshold."
  [base {:keys [numer denom]} comparison integral?]
  (let [exact (/ (* (double base) numer) denom)]
    (if integral?
      (if (= :greater-than comparison)
        (inc (Math/floor exact))
        (Math/ceil exact))
      exact)))

;; ----------------------------- effective fraction -----------------------------

(defn- bylaw-permits?
  "May the association's own 規約 move this rule's threshold to
  `fraction`? `:bylaw` says what the statute allows:

    :none                -- no bylaw may change it (第31条第1項)
    :lower-to-above-half -- may be lowered, but only to a fraction
                            strictly above 1/2 (第17条第1項)
    :raise-only          -- may only be raised (the quorum rules)
    :any                 -- 別段の定め permitted (第39条第1項)"
  [{:keys [bylaw] :as rule} {:keys [numer denom] :as fraction}]
  (let [statutory (:fraction rule)
        lower? (< (* (double numer) (:denom statutory)) (* (double (:numer statutory)) denom))
        above-half? (> (* 2.0 numer) denom)]
    (case bylaw
      :none false
      :any true
      :raise-only (not lower?)
      :lower-to-above-half (and (or lower? (= fraction statutory)) above-half?)
      false)))

(defn effective-fraction
  "The fraction that actually governs this vote, plus why.

  Two things can move it off the statutory default, and they are NOT
  symmetric:

    - a statutory RELAXATION (第62条第2項 各号) applies when the
      building meets one of the listed physical conditions. It is a
      fact about the building, so it must be evidenced, not asserted.
    - a BYLAW override applies only where the statute lets a 規約 move
      it AND the association actually recorded such a provision. A
      claimed override with `:recorded? false` is refused here rather
      than silently honoured -- otherwise 'our bylaws say two-thirds'
      becomes an unfalsifiable way to lower any bar."
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

      (not (bylaw-permits? rule (:fraction bylaw-override)))
      (assoc base-source :fraction base-fraction
             :rejected-override {:reason :bylaw-override-not-permitted
                                 :claimed (:fraction bylaw-override)
                                 :allowed (:bylaw rule)})

      :else
      {:fraction (:fraction bylaw-override)
       :source :bylaw
       :article (:article rule)
       :provision (:provision bylaw-override)})))

;; ----------------------------- tally -----------------------------

(def ^:private integral-axes
  "Axes counted in whole people. `:voting-rights` is NOT one: it is
  apportioned by exclusive floor area under 第38条, so it is a
  continuous quantity. `:land-use-right-value` is a value, likewise."
  #{:owners})

(defn- validate! [rule {:keys [total attending in-favour]}]
  (doseq [axis (:axes rule)]
    (let [t (get total axis) a (get attending axis) f (get in-favour axis)]
      (when (nil? t) (throw (ex-info (str "resolution: total is missing axis " axis) {:axis axis})))
      (when (nil? f) (throw (ex-info (str "resolution: in-favour is missing axis " axis) {:axis axis})))
      (when (neg? (double t)) (throw (ex-info "resolution: counts must be >= 0" {:axis axis})))
      (when (and a (> (double a) (double t)))
        (throw (ex-info (str "resolution: attending exceeds total on axis " axis) {:axis axis})))
      (when (> (double f) (double (or a t)))
        (throw (ex-info (str "resolution: in-favour exceeds the base on axis " axis) {:axis axis})))))
  (when (and (:quorum rule) (nil? attending))
    (throw (ex-info "resolution: this rule has a quorum, so attendance is required" {:article (:article rule)})))
  (when (and (= :attending (:base rule)) (nil? attending))
    (throw (ex-info "resolution: this rule counts the attending base, so attendance is required"
                    {:article (:article rule)}))))

(defn- excluded-base
  "第38条の2 lets a court exclude an owner whose identity or whereabouts
  cannot be established from the denominator of EVERY resolution. That
  is a court's act, not the association's: an exclusion asserted
  without `:court-ordered? true` is ignored here (and reported), so it
  cannot be used to shrink a denominator into a pass."
  [total {:keys [count-by-axis court-ordered?] :as exclusion}]
  (if (and exclusion court-ordered? (map? count-by-axis))
    [(reduce-kv (fn [m axis v] (update m axis #(- (double (or % 0)) (double v)))) total count-by-axis)
     {:applied? true :article "建物の区分所有等に関する法律 第38条の2" :count-by-axis count-by-axis}]
    [total (when exclusion {:applied? false :reason :not-court-ordered})]))

(defn- axis-result [axis base-count in-favour fraction comparison]
  (let [integral? (contains? integral-axes axis)]
    {:axis axis
     :base base-count
     :in-favour in-favour
     :required (required base-count fraction comparison integral?)
     :met? (meets? in-favour base-count fraction comparison)
     :on-boundary? (on-boundary? in-favour base-count fraction)}))

(defn tally
  "Judge one resolution. Returns a full, auditable verdict -- never a
  bare boolean, because 'it failed' is only useful next to WHICH axis
  failed and against WHICH denominator.

  `input`:
    :total       {axis -> count}   -- every member
    :attending   {axis -> count}   -- present (incl. proxies and
                                      written votes: 第39条第2項 counts
                                      both as attendance)
    :in-favour   {axis -> count}
    :exclusion   {:court-ordered? bool :count-by-axis {axis -> n}}
    :relaxation-conditions #{..}
    :bylaw-override {:fraction {..} :recorded? bool :provision \"..\"}"
  [rule input]
  (when (nil? rule)
    (throw (ex-info "resolution/tally: no statutory rule -- the caller must HOLD, not guess" {})))
  (validate! rule input)
  (let [[total exclusion-note] (excluded-base (:total input) (:exclusion input))
        attending (:attending input)
        eff (effective-fraction rule input)
        fraction (:fraction eff)
        comparison (:comparison rule)
        base-map (if (= :attending (:base rule)) attending total)
        quorum (when-let [q (:quorum rule)]
                 (let [rs (mapv (fn [axis]
                                  (axis-result axis (get total axis) (get attending axis)
                                               (:fraction q) (:comparison q)))
                                (:axes q))]
                   {:required-of :total
                    :fraction (:fraction q)
                    :comparison (:comparison q)
                    :axes rs
                    :met? (every? :met? rs)}))
        axes (mapv (fn [axis]
                     (axis-result axis (get base-map axis) (get (:in-favour input) axis)
                                  fraction comparison))
                   (:axes rule))
        quorum-ok? (or (nil? quorum) (:met? quorum))
        axes-ok? (every? :met? axes)]
    {:passed? (and quorum-ok? axes-ok?)
     :article (:article rule)
     :label (:label rule)
     :base (:base rule)
     :comparison comparison
     :effective-fraction eff
     :quorum quorum
     :axes axes
     :exclusion exclusion-note
     :on-boundary? (boolean (some :on-boundary? (concat axes (:axes quorum))))
     :failed-axes (mapv :axis (remove :met? axes))
     :reasons (cond-> []
                (not quorum-ok?) (conj :quorum-unmet)
                (not axes-ok?) (conj :threshold-unmet)
                (:rejected-override eff) (conj (get-in eff [:rejected-override :reason]))
                (and (:exclusion input) (not (:applied? exclusion-note))) (conj :exclusion-not-court-ordered))}))

(defn explain
  "One-paragraph human explanation of a `tally` verdict, for the audit
  ledger and the operator console."
  [{:keys [passed? article label base effective-fraction quorum axes on-boundary?]}]
  (str label " (" article ") -- 母数: "
       (if (= :attending base) "出席者" "総数")
       " / 要件: " (:numer (:fraction effective-fraction)) "/" (:denom (:fraction effective-fraction))
       " (" (name (:source effective-fraction)) ")"
       (when quorum (str " / 定足数: " (if (:met? quorum) "充足" "不足")))
       " / 軸: "
       (str/join "、" (map (fn [a]
                             (str (name (:axis a)) " " (:in-favour a) "/" (:base a)
                                  " (要 " (:required a) ") " (if (:met? a) "○" "×")))
                           axes))
       " => " (if passed? "可決" "否決")
       (when on-boundary? " [要件ちょうどの軸あり -- 人的確認を推奨]")))
