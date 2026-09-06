(ns realty.kumiai.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.facts :as f]))

(deftest coverage-reports-two-numbers-and-they-differ
  ;; Reporting only "4 jurisdictions covered" would overstate this
  ;; actor fourfold: only one of them can have a resolution judged at
  ;; all. The second number is the honest one, so the test asserts they
  ;; are not the same.
  (let [c (f/coverage)]
    (is (= 4 (:covered c)))
    (is (= ["JPN"] (:with-statutory-resolution-thresholds c)))
    (is (= ["DEU" "GBR" "USA-NY"] (:without-statutory-resolution-thresholds c)))
    (is (not= (:covered c) (count (:with-statutory-resolution-thresholds c))))))

(deftest an-unknown-jurisdiction-has-no-spec-basis
  (is (nil? (f/spec-basis "ATL")))
  (is (= [] (f/evidence-checklist "ATL")))
  (is (nil? (f/required-evidence-satisfied? "ATL" ["anything"]))))

(deftest a-jurisdiction-with-a-spec-basis-can-still-have-no-threshold-rule
  (is (some? (f/spec-basis "USA-NY")))
  (is (nil? (f/resolution-rule "USA-NY" :ordinary)))
  (is (nil? (f/resolution-rule "GBR" :common-area-major-change)))
  (is (nil? (f/resolution-rule "DEU" :ordinary))))

(deftest japanese-rules-carry-the-denominator-the-statute-counts
  (testing "ordinary resolutions count the attending, since 2026-04-01"
    (is (= :attending (:base (f/resolution-rule "JPN" :ordinary))))
    (is (= "建物の区分所有等に関する法律 第39条第1項" (:article (f/resolution-rule "JPN" :ordinary)))))
  (testing "rebuilding still counts the total"
    (is (= :total (:base (f/resolution-rule "JPN" :reconstruction)))))
  (testing "the two-stage rules carry a quorum and the rest do not"
    (is (some? (:quorum (f/resolution-rule "JPN" :common-area-major-change))))
    (is (some? (:quorum (f/resolution-rule "JPN" :bylaw-amendment))))
    (is (some? (:quorum (f/resolution-rule "JPN" :restoration))))
    (is (nil? (:quorum (f/resolution-rule "JPN" :ordinary))))
    (is (nil? (:quorum (f/resolution-rule "JPN" :reconstruction))))))

(deftest fractions-are-transcribed-from-the-statute
  (is (= {:numer 1 :denom 2} (:fraction (f/resolution-rule "JPN" :ordinary))))
  (is (= :greater-than (:comparison (f/resolution-rule "JPN" :ordinary))))
  (is (= {:numer 3 :denom 4} (:fraction (f/resolution-rule "JPN" :common-area-major-change))))
  (is (= {:numer 2 :denom 3} (:fraction (f/resolution-rule "JPN" :restoration))))
  (is (= {:numer 4 :denom 5} (:fraction (f/resolution-rule "JPN" :reconstruction))))
  (is (= {:numer 3 :denom 4} (get-in (f/resolution-rule "JPN" :reconstruction) [:relaxed :fraction]))))

(deftest land-sale-resolutions-count-three-axes
  (is (= [:owners :voting-rights :land-use-right-value]
         (:axes (f/resolution-rule "JPN" :building-and-site-sale))))
  (is (= [:owners :voting-rights :land-use-right-value]
         (:axes (f/resolution-rule "JPN" :demolition-and-site-sale))))
  (is (= [:owners :voting-rights] (:axes (f/resolution-rule "JPN" :demolition)))))

(deftest bylaw-latitude-differs-per-provision
  (is (= :lower-to-above-half (:bylaw (f/resolution-rule "JPN" :common-area-major-change))))
  (is (= :none (:bylaw (f/resolution-rule "JPN" :bylaw-amendment))))
  (is (= :any (:bylaw (f/resolution-rule "JPN" :ordinary)))))

(deftest the-five-statutory-relaxation-conditions-are-listed
  (is (= 5 (count (f/relaxation-conditions "JPN" :reconstruction))))
  (is (some #{:seismic-deficient} (f/relaxation-conditions "JPN" :reconstruction))))

(deftest the-reserve-guideline-carries-its-edition-and-source
  (let [g (f/reserve-guideline "JPN")]
    (is (= "令和6年6月改定" (:edition g)))
    (is (re-find #"mlit\.go\.jp" (:provenance g)))
    (is (= 366 (:sample-size g)))
    (is (= {:new 30 :existing 25} (:plan-horizon-years g)))
    (is (= 2 (:major-repair-cycles-min g)))
    (is (= 0.6 (get-in g [:staged-increase :initial-min-ratio])))
    (is (= 1.1 (get-in g [:staged-increase :final-max-ratio]))))
  (is (nil? (f/reserve-guideline "USA-NY"))))

(deftest evidence-is-satisfied-only-when-every-item-is-present
  (let [need (f/evidence-checklist "JPN")]
    (is (true? (f/required-evidence-satisfied? "JPN" need)))
    (is (false? (f/required-evidence-satisfied? "JPN" (rest need))))))

(deftest jurisdiction-summary-says-when-thresholds-are-unavailable
  (is (re-find #"決議要件 9 種" (f/jurisdiction-summary "JPN")))
  (is (re-find #"判定不能" (f/jurisdiction-summary "USA-NY")))
  (is (re-find #"NO SPEC-BASIS" (f/jurisdiction-summary "ATL"))))
