(ns realty.kumiai.resolution-test
  "The vote arithmetic, tested in BOTH directions.

  A test that only asserts the passing case cannot tell a working rule
  from a rule that returns `true`. Every rule here is exercised with an
  input that clears it and an input that does not, and the denominator
  cases are built so that the SAME ballot gives opposite answers on the
  two bases -- otherwise a wrong denominator would keep agreeing with
  the right one and the test would never notice."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.facts :as facts]
            [realty.kumiai.resolution :as r]))

(def ordinary (facts/resolution-rule "JPN" :ordinary))
(def major-change (facts/resolution-rule "JPN" :common-area-major-change))
(def rebuild (facts/resolution-rule "JPN" :reconstruction))
(def site-sale (facts/resolution-rule "JPN" :building-and-site-sale))
(def bylaw (facts/resolution-rule "JPN" :bylaw-amendment))

;; 30 of 70 owners in favour, 44 attending. Passes on the attending
;; base (30 > 22), fails on the total base (30 < 35.000...).
(def contested
  {:total     {:owners 70 :voting-rights 4900}
   :attending {:owners 44 :voting-rights 3080}
   :in-favour {:owners 30 :voting-rights 2100}})

;; ----------------------------- exact fraction comparison -----------------------------

(deftest majority-is-strict-and-supermajority-is-inclusive
  (testing "過半数 is a strict majority: exactly half fails"
    (is (false? (r/meets? 50 100 {:numer 1 :denom 2} :greater-than)))
    (is (true?  (r/meets? 51 100 {:numer 1 :denom 2} :greater-than))))
  (testing "N分のM以上 includes the boundary"
    (is (true?  (r/meets? 75 100 {:numer 3 :denom 4} :at-least)))
    (is (false? (r/meets? 74 100 {:numer 3 :denom 4} :at-least)))))

(deftest thirds-are-exact-not-floating-point
  ;; 66 of 99 is exactly two thirds. A float ratio would make this
  ;; comparison depend on rounding.
  (is (true? (r/meets? 66 99 {:numer 2 :denom 3} :at-least)))
  (is (false? (r/meets? 65 99 {:numer 2 :denom 3} :at-least)))
  (is (true? (r/on-boundary? 66 99 {:numer 2 :denom 3}))))

(deftest required-rounds-the-right-way-per-axis
  (testing "head counts are whole people"
    (is (= 36.0 (r/required 70 {:numer 1 :denom 2} :greater-than true)))
    (is (= 53.0 (r/required 70 {:numer 3 :denom 4} :at-least true))))
  (testing "voting rights are continuous (apportioned by floor area, 第38条)"
    (is (= 2450.0 (r/required 4900 {:numer 1 :denom 2} :greater-than false)))))

;; ----------------------------- the denominator -----------------------------

(deftest the-same-ballot-passes-on-attending-and-fails-on-total
  ;; This is the pair the whole extension turns on. If the two bases
  ;; agreed here, a wrong-denominator implementation would pass this
  ;; test, which is why the fixture is built to disagree.
  (let [statutory (r/tally ordinary contested)
        pre-reform (r/tally (assoc ordinary :base :total) contested)]
    (is (true? (:passed? statutory)))
    (is (= :attending (:base statutory)))
    (is (false? (:passed? pre-reform)))
    (is (= :total (:base pre-reform)))))

(deftest rebuilding-still-counts-the-total-base
  ;; 第62条第1項 did NOT move to the attending base in the 令和7年改正.
  (is (= :total (:base (r/tally rebuild {:total {:owners 70 :voting-rights 4900}
                                         :in-favour {:owners 56 :voting-rights 3920}}))))
  (is (true? (:passed? (r/tally rebuild {:total {:owners 70 :voting-rights 4900}
                                         :in-favour {:owners 56 :voting-rights 3920}}))))
  (is (false? (:passed? (r/tally rebuild {:total {:owners 70 :voting-rights 4900}
                                          :in-favour {:owners 55 :voting-rights 3920}})))))

(deftest attending-base-rules-demand-attendance-data
  (is (thrown? Exception (r/tally ordinary {:total {:owners 70 :voting-rights 4900}
                                            :in-favour {:owners 40 :voting-rights 2800}}))))

;; ----------------------------- every axis independently -----------------------------

(deftest one-axis-clearing-is-not-enough
  ;; Voting rights sail past three quarters; heads do not. The
  ;; resolution has not passed, and the failing axis is named.
  (let [v (r/tally major-change
                   {:total     {:owners 70 :voting-rights 4900}
                    :attending {:owners 40 :voting-rights 2800}
                    :in-favour {:owners 20 :voting-rights 2600}})]
    (is (false? (:passed? v)))
    (is (= [:owners] (:failed-axes v)))))

(deftest land-sale-counts-a-third-axis
  (let [base {:total     {:owners 70 :voting-rights 4900 :land-use-right-value 1000}
              :in-favour {:owners 56 :voting-rights 3920 :land-use-right-value 900}}]
    (is (true? (:passed? (r/tally site-sale base))))
    (testing "the land-use-right share alone can sink it"
      (is (false? (:passed? (r/tally site-sale (assoc-in base [:in-favour :land-use-right-value] 700))))))
    (testing "and it must be supplied at all"
      (is (thrown? Exception (r/tally site-sale (update base :in-favour dissoc :land-use-right-value)))))))

;; ----------------------------- quorum -----------------------------

(deftest quorum-is-a-separate-stage
  ;; A thin meeting that is unanimous among those present has still not
  ;; passed a 第17条第1項 resolution.
  (let [thin (r/tally major-change
                      {:total     {:owners 70 :voting-rights 4900}
                       :attending {:owners 30 :voting-rights 2100}
                       :in-favour {:owners 30 :voting-rights 2100}})]
    (is (false? (:passed? thin)))
    (is (false? (:met? (:quorum thin))))
    (is (= [:quorum-unmet] (:reasons thin))))
  (let [full (r/tally major-change
                      {:total     {:owners 70 :voting-rights 4900}
                       :attending {:owners 40 :voting-rights 2800}
                       :in-favour {:owners 30 :voting-rights 2100}})]
    (is (true? (:passed? full)))
    (is (true? (:met? (:quorum full))))))

(deftest ordinary-resolutions-have-no-quorum
  (is (nil? (:quorum (r/tally ordinary contested)))))

;; ----------------------------- bylaw overrides -----------------------------

(def major-change-two-thirds
  {:total     {:owners 70 :voting-rights 4900}
   :attending {:owners 40 :voting-rights 2800}
   :in-favour {:owners 27 :voting-rights 1890}})  ; 27/40 -> above 2/3, below 3/4

(deftest a-recorded-and-permitted-bylaw-override-applies
  (let [v (r/tally major-change (assoc major-change-two-thirds
                                       :bylaw-override {:fraction {:numer 2 :denom 3}
                                                        :recorded? true
                                                        :provision "管理規約第47条"}))]
    (is (true? (:passed? v)))
    (is (= :bylaw (get-in v [:effective-fraction :source])))))

(deftest an-unrecorded-override-is-refused-not-honoured
  (let [v (r/tally major-change (assoc major-change-two-thirds
                                       :bylaw-override {:fraction {:numer 2 :denom 3}
                                                        :recorded? false}))]
    (is (false? (:passed? v)))
    (is (= :statutory (get-in v [:effective-fraction :source])))
    (is (= :bylaw-override-not-recorded (get-in v [:effective-fraction :rejected-override :reason])))))

(deftest an-override-the-statute-does-not-permit-is-refused
  (testing "第31条第1項 admits no lowering at all"
    (let [v (r/tally bylaw (assoc major-change-two-thirds
                                  :bylaw-override {:fraction {:numer 2 :denom 3} :recorded? true}))]
      (is (false? (:passed? v)))
      (is (= :bylaw-override-not-permitted (get-in v [:effective-fraction :rejected-override :reason])))))
  (testing "第17条第1項 permits lowering only to a fraction above one half"
    (let [v (r/tally major-change (assoc major-change-two-thirds
                                         :bylaw-override {:fraction {:numer 1 :denom 2} :recorded? true}))]
      (is (= :bylaw-override-not-permitted (get-in v [:effective-fraction :rejected-override :reason]))))))

;; ----------------------------- statutory relaxation -----------------------------

(deftest rebuilding-relaxes-only-on-a-listed-condition
  (let [three-quarters {:total {:owners 70 :voting-rights 4900}
                        :in-favour {:owners 53 :voting-rights 3675}}]
    (is (false? (:passed? (r/tally rebuild three-quarters))))
    (is (true? (:passed? (r/tally rebuild (assoc three-quarters :relaxation-conditions #{:seismic-deficient})))))
    (testing "an unlisted condition does not relax anything"
      (is (false? (:passed? (r/tally rebuild (assoc three-quarters :relaxation-conditions #{:owners-would-prefer-it}))))))))

;; ----------------------------- 第38条の2 exclusion -----------------------------

(deftest excluding-untraceable-owners-requires-a-court-order
  (let [input {:total {:owners 70 :voting-rights 4900}
               :in-favour {:owners 50 :voting-rights 3500}
               :exclusion {:court-ordered? true :count-by-axis {:owners 4 :voting-rights 280}}}]
    ;; 50 of 70 misses three quarters (needs 53); 50 of the 66 left after
    ;; a court excludes four clears it (needs 50). The fixture is built
    ;; so the exclusion CHANGES the answer -- otherwise a version that
    ;; ignored the exclusion entirely would pass this test.
    (testing "with the court's order the denominator shrinks and it passes"
      (is (true? (:passed? (r/tally rebuild (assoc input :relaxation-conditions #{:seismic-deficient}))))))
    (testing "without it the same claim is ignored and reported"
      (let [v (r/tally rebuild (-> input
                                   (assoc :relaxation-conditions #{:seismic-deficient})
                                   (assoc-in [:exclusion :court-ordered?] false)))]
        (is (false? (:passed? v)))
        (is (some #{:exclusion-not-court-ordered} (:reasons v)))))))

;; ----------------------------- boundary flag -----------------------------

(deftest landing-exactly-on-the-line-is-flagged
  (let [v (r/tally major-change {:total     {:owners 80 :voting-rights 8000}
                                 :attending {:owners 60 :voting-rights 6000}
                                 :in-favour {:owners 45 :voting-rights 4500}})]
    (is (true? (:passed? v)))
    (is (true? (:on-boundary? v))))
  (let [v (r/tally major-change {:total     {:owners 80 :voting-rights 8000}
                                 :attending {:owners 60 :voting-rights 6000}
                                 :in-favour {:owners 46 :voting-rights 4600}})]
    (is (false? (:on-boundary? v)))))

;; ----------------------------- structural refusal -----------------------------

(deftest structurally-impossible-tallies-throw-rather-than-answer
  (is (thrown? Exception (r/tally ordinary {:total {:owners 70 :voting-rights 4900}
                                            :attending {:owners 80 :voting-rights 5600}
                                            :in-favour {:owners 40 :voting-rights 2800}})))
  (is (thrown? Exception (r/tally ordinary {:total {:owners 70 :voting-rights 4900}
                                            :attending {:owners 44 :voting-rights 3080}
                                            :in-favour {:owners 50 :voting-rights 3500}})))
  (is (thrown? Exception (r/tally nil contested))))

(deftest explain-names-the-denominator-it-counted
  (is (re-find #"出席者" (r/explain (r/tally ordinary contested))))
  (is (re-find #"総数" (r/explain (r/tally rebuild {:total {:owners 70 :voting-rights 4900}
                                                    :in-favour {:owners 56 :voting-rights 3920}})))))
