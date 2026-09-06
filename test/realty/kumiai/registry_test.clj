(ns realty.kumiai.registry-test
  (:require [clojure.test :refer [deftest is]]
            [realty.kumiai.registry :as r]))

(def verdict
  {:passed? true :article "建物の区分所有等に関する法律 第39条第1項" :base :attending
   :effective-fraction {:fraction {:numer 1 :denom 2} :source :statutory}
   :quorum nil :on-boundary? false
   :axes [{:axis :owners :base 44 :in-favour 30 :required 23.0 :met? true}
          {:axis :voting-rights :base 3080 :in-favour 2100 :required 1540.0 :met? true}]})

(deftest resolution-records-are-drafts-not-certified-minutes
  (let [res (r/register-resolution "association-1" "res-1" :ordinary 150000000 "JPN" verdict 0)]
    (is (nil? (get-in res ["certificate" "proof"])))
    (is (= false (get-in res ["certificate" "issued_by_registry"])))
    (is (= "draft-unsigned" (get-in res ["certificate" "status"])))))

(deftest a-resolution-record-preserves-the-denominator-it-was-counted-against
  ;; 'passed' without the base it was counted on is not auditable, and
  ;; the base is exactly what changed on 2026-04-01.
  (let [rec (get (r/register-resolution "association-1" "res-1" :ordinary 150000000 "JPN" verdict 7) "record")]
    (is (= "JPN-RES-000007" (get rec "record_id")))
    (is (= true (get rec "passed")))
    (is (= "attending" (get rec "counted_against")))
    (is (= "建物の区分所有等に関する法律 第39条第1項" (get rec "statutory_basis")))
    (is (= "1/2" (get rec "effective_fraction")))
    (is (= "statutory" (get rec "fraction_source")))
    (is (= 2 (count (get rec "axes"))))
    (is (= true (get rec "immutable")))))

(deftest resolution-validation-rules
  (is (thrown? Exception (r/register-resolution "" "res-1" :ordinary 1 "JPN" verdict 0)))
  (is (thrown? Exception (r/register-resolution "a" "" :ordinary 1 "JPN" verdict 0)))
  (is (thrown? Exception (r/register-resolution "a" "res-1" "ordinary" 1 "JPN" verdict 0)))
  (is (thrown? Exception (r/register-resolution "a" "res-1" :ordinary -1 "JPN" verdict 0)))
  (is (thrown? Exception (r/register-resolution "a" "res-1" :ordinary 1 "" verdict 0)))
  (is (thrown? Exception (r/register-resolution "a" "res-1" :ordinary 1 "JPN" nil 0)))
  (is (thrown? Exception (r/register-resolution "a" "res-1" :ordinary 1 "JPN" verdict -1))))

(deftest works-orders-name-the-minute-that-authorised-them
  (let [rec (get (r/register-works-order "association-1" "works-1" "南工務店" 120000000 "JPN" "JPN-RES-000000" 3) "record")]
    (is (= "JPN-WRK-000003" (get rec "record_id")))
    (is (= "JPN-RES-000000" (get rec "authorised_by")))
    (is (= 120000000 (get rec "contract_value")))))

(deftest a-works-order-with-no-minute-cannot-be-written-at-all
  (is (thrown? Exception (r/register-works-order "a" "w" "c" 1 "JPN" nil 0)))
  (is (thrown? Exception (r/register-works-order "a" "w" "c" 1 "JPN" "" 0)))
  (is (thrown? Exception (r/register-works-order "a" "w" "" 1 "JPN" "R" 0)))
  (is (thrown? Exception (r/register-works-order "a" "w" "c" -1 "JPN" "R" 0))))

(deftest append-never-mutates-history
  (let [h [{"record_id" "x"}]
        res (r/register-works-order "a" "w" "c" 1 "JPN" "R" 0)]
    (is (= 2 (count (r/append h res))))
    (is (= 1 (count h)))))
