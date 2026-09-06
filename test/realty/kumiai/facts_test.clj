(ns realty.kumiai.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.facts :as f]))

(deftest coverage-separates-covered-from-judgeable
  ;; Reporting only "N jurisdictions covered" would overstate this
  ;; actor: only some of them can have a resolution judged at all. The
  ;; test asserts the two numbers are not the same, so collapsing them
  ;; breaks a test rather than passing one.
  (let [c (f/coverage)]
    (is (= (count f/catalog) (:covered c)))
    (is (= ["DEU" "ESP" "FRA" "JPN" "SGP"] (:with-statutory-resolution-thresholds c)))
    (is (< (count (:with-statutory-resolution-thresholds c)) (:covered c)))))

(deftest coverage-splits-unreadable-from-nonexistent
  ;; The load-bearing distinction. USA-NY and GBR have no national
  ;; table to read; SGP, AUS-NSW, ITA and CHN have one this actor could
  ;; not fetch. Both end up without `:resolutions`, and the governor
  ;; holds either way -- but reporting them as one category would say
  ;; that reading harder cannot help, which is false for four of them.
  (let [c (f/coverage)
        by (:without-thresholds-by-reason c)]
    (is (= ["AUS-NSW" "CHN" "ITA"] (:unreadable-sources c)))
    (is (= ["USA-NY"] (:no-statutory-threshold-table by)))
    (is (= ["GBR"] (:no-unit-owner-vote by)))
    (is (not-any? #{:unstated} (keys by)))))

(deftest an-unreadable-source-records-the-attempt-not-a-guess
  ;; A remembered threshold and a fetched one must not be storable in
  ;; the same field, so the unverified entries use a DIFFERENT key.
  (doseq [j ["AUS-NSW" "ITA" "CHN"]]
    (let [b (f/spec-basis j)]
      (is (nil? (:legal-basis b)) j)
      (is (some? (:legal-basis-unverified b)) j)
      (is (nil? (:resolutions b)) j)
      (is (some? (get-in b [:source-attempt :status])) j)
      (is (= "2026-09-06" (get-in b [:source-attempt :on])) j))))

(deftest a-bot-challenge-is-recorded-as-a-decision-not-a-gap
  ;; NSW is unverified because defeating a bot-detection interstitial is
  ;; something this workspace does not do -- not because nobody tried.
  ;; The reason keyword is separate so the two never collapse.
  (let [b (f/spec-basis "AUS-NSW")]
    (is (= :source-behind-bot-challenge (:unverified-reason b)))
    (is (= :cloudflare-interstitial (get-in b [:source-attempt :challenge])))
    (is (nil? (:resolutions b))))
  (is (not= (f/unverified-reason "AUS-NSW") (f/unverified-reason "ITA"))))

(deftest a-two-hundred-response-is-not-a-successful-read
  ;; ITA and CHN answered 200 with a navigation frame and a news page.
  ;; Recording those as reachable would be the "measured nothing looks
  ;; like measured fine" failure in its purest form.
  (doseq [j ["ITA" "CHN"]]
    (is (= 200 (get-in (f/spec-basis j) [:source-attempt :status])) j)
    (is (= :source-unreachable (f/unverified-reason j)) j)))

(deftest an-unknown-jurisdiction-has-no-spec-basis
  (is (nil? (f/spec-basis "ATL")))
  (is (= [] (f/evidence-checklist "ATL")))
  (is (nil? (f/required-evidence-satisfied? "ATL" ["anything"]))))

(deftest a-jurisdiction-with-a-spec-basis-can-still-have-no-threshold-rule
  (is (some? (f/spec-basis "USA-NY")))
  (is (nil? (f/resolution-rule "USA-NY" :ordinary)))
  (is (nil? (f/resolution-rule "GBR" :common-area-major-change)))
  (is (nil? (f/resolution-rule "AUS-NSW" :ordinary))))

(deftest only-japan-carries-a-reserve-benchmark
  ;; The MLIT bands are Japanese and apply nowhere else. Germany's
  ;; Erhaltungsrücklage has no statutory minimum, Spain's fondo de
  ;; reserva is a percentage of the budget rather than a rate per square
  ;; metre, and France's fonds de travaux publishes no scale.
  (is (some? (f/reserve-guideline "JPN")))
  (doseq [j ["DEU" "ESP" "FRA" "USA-NY" "GBR" "SGP"]]
    (is (nil? (f/reserve-guideline j)) j)))

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
  (is (re-find #"規約/宣言に依る" (f/jurisdiction-summary "USA-NY")))
  (is (re-find #"leasehold" (f/jurisdiction-summary "GBR")))
  (is (re-find #"一次資料を取得できていない" (f/jurisdiction-summary "ITA")))
  (is (re-find #"bot 検出の背後" (f/jurisdiction-summary "AUS-NSW")))
  (is (re-find #"決議要件 7 種" (f/jurisdiction-summary "SGP")))
  (is (re-find #"NO SPEC-BASIS" (f/jurisdiction-summary "ATL"))))
