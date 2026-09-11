(ns realty.kumiai.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.phase :as phase]))

(deftest actuation-never-auto-commits-at-any-phase
  ;; The invariant, asserted over EVERY phase rather than by reading the
  ;; table -- so adding a phase cannot quietly acquire the exemption.
  (doseq [p (keys phase/phases)]
    (is (false? (phase/auto-eligible? p :works/commission)) (str "phase " p))
    (is (false? (phase/auto-eligible? p :resolution/file)) (str "phase " p))))

(deftest a-governor-hold-survives-every-phase
  (doseq [p (keys phase/phases)]
    (is (= :hold (:disposition (phase/gate p {:op :reserve/simulate} :hold))))))

(deftest an-op-not-yet-enabled-in-this-phase-holds
  (is (= [:hold :phase-disabled]
         ((juxt :disposition :reason) (phase/gate 1 {:op :works/commission} :commit))))
  (is (= [:hold :phase-disabled]
         ((juxt :disposition :reason) (phase/gate 0 {:op :association/intake} :commit)))))

(deftest an-enabled-but-non-auto-op-escalates-even-when-clean
  (is (= [:escalate :phase-approval]
         ((juxt :disposition :reason) (phase/gate 3 {:op :works/commission} :commit))))
  (is (= [:escalate :phase-approval]
         ((juxt :disposition :reason) (phase/gate 2 {:op :plan/assess} :commit)))))

(deftest the-two-auto-eligible-ops-commit-at-phase-three
  (is (= :commit (:disposition (phase/gate 3 {:op :association/intake} :commit))))
  (is (= :commit (:disposition (phase/gate 3 {:op :reserve/simulate} :commit)))))

(deftest verdict-mapping
  (is (= :hold (phase/verdict->disposition {:hard? true :escalate? true})))
  (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
  (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false}))))

(deftest every-write-op-is-permitted-somewhere
  (testing "phase 3 permits every write op, so none is unreachable"
    (is (= phase/write-ops (get-in phase/phases [3 :writes])))))
