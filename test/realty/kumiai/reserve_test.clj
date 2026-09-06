(ns realty.kumiai.reserve-test
  "Reserve arithmetic.

  Two anchors are load-bearing:

  1. The guideline's OWN worked example. 国土交通省「マンションの修繕
     積立金に関するガイドライン」(令和6年6月改定) computes a model case
     -- 70 units, 4,900㎡ total exclusive area, 70,000,000 opening
     balance, 264,600,000 collected over 30 years, 90,000,000 of
     transfers, 30 mechanical parking spaces of type 3段(ピット2段)昇降式
     -- and states the answers: Z ≒ 241 円/㎡・月, parking add ≒ 36,
     band 206〜356. Reproducing those numbers is what proves the
     transcription in `facts`, not just the arithmetic here.

  2. The delay trap, in both directions. A two-year slip pushes the
     year-28 works past a thirty-year horizon; the projected deficit
     goes DOWN and the plan looks healthier. The test asserts both the
     misleading improvement and the `:unmeasured-outflow` that
     contradicts it -- because a test that only checked the deficit
     would certify the bug."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.reserve :as r]
            [realty.kumiai.store :as store]))

(def guideline-model-case
  "The association in `store/demo-data`, which is dimensioned to be the
  guideline's own worked example."
  (get-in (store/demo-data) [:associations "association-1"]))

(def plan (get-in (store/demo-data) [:plans "association-1"]))

(def flat {:cost-escalation 0.0 :delay-years 0})
(def rising {:cost-escalation 0.05 :delay-years 0})
(def rising-late {:cost-escalation 0.05 :delay-years 2})

;; ----------------------------- guideline anchors -----------------------------

(deftest z-reproduces-the-guidelines-own-worked-example
  (let [{:keys [z contributions-total transfers-total months]} (r/guideline-average guideline-model-case plan)]
    (is (= 264600000.0 contributions-total))
    (is (= 90000000.0 transfers-total))
    (is (= 360 months))
    (is (= 241 (Math/round z)))))

(deftest mechanical-parking-add-on-reproduces-the-guidelines-figure
  (let [band (r/benchmark-band "JPN" guideline-model-case)]
    (is (= 5840 (:parking-unit-cost band)))
    (is (= 36 (Math/round (:parking-add band))))
    (is (= 206 (Math/round (:low band))))
    (is (= 356 (Math/round (:high band))))
    (is (= :gross-floor-area (:basis band)))))

(deftest the-model-case-sits-inside-the-band
  (is (= :within (:position (r/assess-against-benchmark "JPN" guideline-model-case plan))))
  (testing "and a materially thinner contribution does not"
    (is (= :below (:position (r/assess-against-benchmark
                              "JPN" (assoc guideline-model-case :monthly-per-m2 40) plan))))))

(deftest a-tall-building-uses-the-high-rise-row-not-the-floor-area-row
  (is (= :high-rise (:basis (r/benchmark-band "JPN" (assoc guideline-model-case :floors 20)))))
  (is (= :gross-floor-area (:basis (r/benchmark-band "JPN" (assoc guideline-model-case :floors 19))))))

(deftest a-jurisdiction-with-no-guideline-gets-no-invented-benchmark
  (is (nil? (r/benchmark-band "ATL" guideline-model-case)))
  (is (nil? (r/assess-against-benchmark "ATL" guideline-model-case plan))))

;; ----------------------------- staged increase -----------------------------

(deftest a-level-plan-trivially-conforms
  (let [v (r/staged-increase-verdict "JPN" guideline-model-case plan)]
    (is (true? (:level? v)))
    (is (true? (:conforms? v)))))

(deftest staged-schedules-are-judged-against-the-guidelines-06-and-11
  ;; D = 146.67; 0.6 x D = 88 <= 130, and 1.1 x D = 161.3 >= 160.
  (let [gentle (assoc (dissoc guideline-model-case :monthly-per-m2)
                      :schedule [{:from-year 0 :monthly-per-m2 130}
                                 {:from-year 10 :monthly-per-m2 150}
                                 {:from-year 20 :monthly-per-m2 160}])
        steep  (assoc (dissoc guideline-model-case :monthly-per-m2)
                      :schedule [{:from-year 0 :monthly-per-m2 60}
                                 {:from-year 20 :monthly-per-m2 300}])]
    (is (true? (:conforms? (r/staged-increase-verdict "JPN" gentle plan))))
    (let [v (r/staged-increase-verdict "JPN" steep plan)]
      (is (false? (:conforms? v)))
      (is (false? (:initial-ok? v)))
      (is (false? (:final-ok? v))))
    (testing "a final step above 1.1 x D fails even when the ramp looks gentle"
      (let [too-high (assoc (dissoc guideline-model-case :monthly-per-m2)
                            :schedule [{:from-year 0 :monthly-per-m2 120}
                                       {:from-year 10 :monthly-per-m2 155}
                                       {:from-year 20 :monthly-per-m2 175}])
            v (r/staged-increase-verdict "JPN" too-high plan)]
        (is (true? (:initial-ok? v)))
        (is (false? (:final-ok? v)))
        (is (false? (:conforms? v)))))))

(deftest contribution-rate-steps-at-the-scheduled-year
  (let [a (assoc (dissoc guideline-model-case :monthly-per-m2)
                 :schedule [{:from-year 0 :monthly-per-m2 100}
                            {:from-year 5 :monthly-per-m2 200}])]
    (is (= 100 (r/contribution-rate a 4)))
    (is (= 200 (r/contribution-rate a 5)))
    (is (= 200 (r/contribution-rate a 30)))))

;; ----------------------------- escalation and slippage -----------------------------

(deftest escalation-compounds-from-the-base-year-to-the-year-actually-worked
  (let [w {:id "w" :year 10 :base-cost 100000000}]
    (is (= 100000000.0 (r/escalated-cost w flat)))
    (is (= (Math/round (* 100000000.0 (Math/pow 1.05 10)))
           (Math/round (r/escalated-cost w rising))))
    (testing "a delay moves the exponent, not just the year"
      (is (= (Math/round (* 100000000.0 (Math/pow 1.05 12)))
             (Math/round (r/escalated-cost w rising-late)))))))

(deftest the-plan-is-funded-flat-and-short-when-costs-rise
  (let [flat-s (r/shortfall (r/project guideline-model-case plan flat))
        rise-s (r/shortfall (r/project guideline-model-case plan rising))]
    (is (true? (:funded? flat-s)))
    (is (zero? (:deficit flat-s)))
    (is (false? (:funded? rise-s)))
    (is (pos? (:deficit rise-s)))))

(deftest delaying-works-past-the-horizon-makes-the-deficit-look-smaller
  ;; The misleading improvement, asserted so that removing the
  ;; `:unmeasured-outflow` guard would break a test rather than pass one.
  (let [rise (r/shortfall (r/project guideline-model-case plan rising))
        late (r/shortfall (r/project guideline-model-case plan rising-late))]
    (is (< (:deficit late) (:deficit rise))
        "a two-year slip makes the projected deficit smaller -- this is the trap")
    (testing "and the contradiction is reported rather than swallowed"
      (is (pos? (:unmeasured-outflow late)))
      (is (= ["w5"] (:deferred-works late)))
      (is (false? (:funded? late)))))
  (testing "nothing falls off the end when nothing is delayed"
    (let [rise (r/shortfall (r/project guideline-model-case plan rising))]
      (is (zero? (:unmeasured-outflow rise)))
      (is (empty? (:deferred-works rise))))))

(deftest a-plan-that-cannot-be-funded-by-contributions-says-so
  ;; With the year-28 works pushed past the horizon, no contribution
  ;; rate makes the plan whole -- and the solver returns that fact
  ;; rather than a number.
  (let [res (r/required-contribution guideline-model-case plan rising-late)]
    (is (false? (:solvable? res)))
    (is (= :outflow-beyond-horizon (:reason res)))))

(deftest the-solved-contribution-is-the-smallest-one-that-works
  ;; Both directions: at the answer the plan is funded, a yen below it
  ;; is not. A solver that returned any large number would pass the
  ;; first assertion and fail the second.
  (let [{:keys [monthly-per-m2 solvable? increase-ratio]} (r/required-contribution guideline-model-case plan rising)
        at   (assoc guideline-model-case :monthly-per-m2 monthly-per-m2)
        just-below (assoc guideline-model-case :monthly-per-m2 (- monthly-per-m2 1.0))]
    (is (true? solvable?))
    (is (> increase-ratio 1.0))
    (is (true? (:funded? (r/shortfall (r/project at plan rising)))))
    (is (false? (:funded? (r/shortfall (r/project just-below plan rising)))))))

(deftest an-association-with-no-floor-area-gets-no-rate
  (is (nil? (r/required-contribution (assoc guideline-model-case :total-exclusive-area 0) plan rising))))

;; ----------------------------- horizon preconditions -----------------------------

(deftest a-short-plan-is-not-comparable-with-the-benchmark
  (let [short-plan {:horizon-years 15 :new-build? false
                    :works [{:id "a" :year 5 :base-cost 70000000 :major-repair? true}]}
        c (r/plan-conforms-to-horizon? "JPN" short-plan)]
    (is (false? (:conforms? c)))
    (is (false? (:horizon-ok? c)))
    (is (false? (:cycles-ok? c)))
    (is (= 25 (:required-horizon-years c))))
  (testing "and the demo plan is"
    (is (true? (:conforms? (r/plan-conforms-to-horizon? "JPN" plan))))))

(deftest a-new-build-must-plan-thirty-years-not-twenty-five
  (is (= 30 (:required-horizon-years (r/plan-conforms-to-horizon? "JPN" (assoc plan :new-build? true)))))
  (is (= 25 (:required-horizon-years (r/plan-conforms-to-horizon? "JPN" plan)))))

;; ----------------------------- sensitivity -----------------------------

(deftest sensitivity-covers-the-grid-and-worsens-monotonically-in-escalation
  (let [rows (r/sensitivity guideline-model-case plan {} [0.0 0.03 0.05] [0])]
    (is (= 3 (count rows)))
    (is (true? (:funded? (first rows))))
    (is (apply < (map :deficit rows)))))

(deftest sensitivity-refuses-to-sweep-a-delay-it-cannot-actually-move
  ;; A per-work delay overrides the global one, so sweeping
  ;; `:delay-years` would leave those works still and understate the
  ;; table without saying so.
  (is (thrown? Exception
               (r/sensitivity guideline-model-case plan {:per-work-delay {"w1" 3}} [0.0 0.05] [0 2]))))
