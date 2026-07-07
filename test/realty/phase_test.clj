(ns realty.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:fee/pay`/`:contract/execute` must NEVER be a member of
  any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.phase :as phase]))

(deftest fee-pay-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real fee payment"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :fee/pay))
          (str "phase " n " must not auto-commit :fee/pay")))))

(deftest contract-execute-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-executes a real contract"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :contract/execute))
          (str "phase " n " must not auto-commit :contract/execute")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":property/intake and :fee/file move no capital -- auto-eligible"
    (is (= #{:property/intake :fee/file} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :property/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :fee/pay} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :contract/execute} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :property/intake} :commit)))))
