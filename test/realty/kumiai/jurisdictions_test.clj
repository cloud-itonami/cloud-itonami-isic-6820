(ns realty.kumiai.jurisdictions-test
  "The non-Japanese statutory rules, and the model shapes they forced.

  Every rule is exercised with an input that clears it and one that
  does not. Where a jurisdiction's shape differs from Japan's, the
  fixture is built so that an implementation carrying only Japan's
  shape would FAIL these tests rather than pass them:

    - a `:cast` denominator is only distinguishable from `:attending`
      and `:total` when the three differ, so the shared ballot has
      abstentions AND absentees;
    - a per-axis fraction is only distinguishable from a rule-level one
      when the two axes need different fractions AND the tally clears
      one but not the other;
    - a per-axis DENOMINATOR is only distinguishable when the two axes
      are counted against different bases and the answer depends on it."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.facts :as facts]
            [realty.kumiai.resolution :as r]))

;; 100 members / 10,000 voices. 60 attend. 50 actually vote (10 abstain).
;; 26 vote in favour.
;;
;;   of the votes CAST      26/50  -> 52%  passes a simple majority
;;   of those ATTENDING     26/60  -> 43%  fails
;;   of ALL members         26/100 -> 26%  fails
;;
;; One ballot, three answers, and only the statute says which is right.
(def shared-ballot
  {:total     {:owners 100 :voting-rights 10000 :co-ownership-shares 10000}
   :attending {:owners 60 :voting-rights 6000 :co-ownership-shares 6000}
   :cast      {:owners 50 :voting-rights 5000 :co-ownership-shares 5000}
   :in-favour {:owners 26 :voting-rights 2600 :co-ownership-shares 2600}})

(deftest the-same-ballot-gets-different-answers-in-different-jurisdictions
  (testing "DEU § 25 Abs. 1 counts the votes cast -- it passes"
    (let [v (r/tally (facts/resolution-rule "DEU" :ordinary) shared-ballot)]
      (is (true? (:passed? v)))
      (is (= [:cast] (:bases v)))))
  (testing "FRA art. 24 also counts the votes cast -- it passes"
    (is (true? (:passed? (r/tally (facts/resolution-rule "FRA" :ordinary) shared-ballot)))))
  (testing "FRA art. 25 counts ALL owners' voices -- it fails"
    (let [v (r/tally (facts/resolution-rule "FRA" :absolute-majority) shared-ballot)]
      (is (false? (:passed? v)))
      (is (= [:total] (:bases v)))))
  (testing "JPN 第39条第1項 counts the attending -- it fails"
    (is (false? (:passed? (r/tally (facts/resolution-rule "JPN" :ordinary) shared-ballot)))))
  (testing "ESP 17.7 first call counts everyone, second call the attending -- both fail here"
    (is (false? (:passed? (r/tally (facts/resolution-rule "ESP" :ordinary-first-call) shared-ballot))))
    (is (false? (:passed? (r/tally (facts/resolution-rule "ESP" :ordinary-second-call) shared-ballot))))))

;; ----------------------------- DEU -----------------------------

(deftest germany-decides-on-the-votes-cast-not-on-attendance
  (let [rule (facts/resolution-rule "DEU" :ordinary)]
    (is (= :cast (:base rule)))
    (is (nil? (:quorum rule)) "the 2020 WEMoG reform removed the Beschlussfähigkeit quorum")
    (testing "26 of 50 cast passes; 25 of 50 does not (Mehrheit is strict)"
      (is (true? (:passed? (r/tally rule shared-ballot))))
      (is (false? (:passed? (r/tally rule (assoc-in shared-ballot [:in-favour :owners] 25))))))))

(deftest germany-needs-the-cast-counts-and-says-so
  (is (thrown? Exception
               (r/tally (facts/resolution-rule "DEU" :ordinary) (dissoc shared-ballot :cast)))))

(deftest germany-separates-doing-the-work-from-who-pays-for-it
  ;; § 20 Abs. 1 approves the bauliche Veränderung on a simple majority
  ;; of the votes cast. § 21 Abs. 2 Nr. 1 is what puts the cost on
  ;; EVERY owner instead of only the yes-voters, and it needs more than
  ;; two thirds of the votes cast AND more than half of ALL shares.
  (let [ballot {:total     {:owners 100 :voting-rights 10000 :co-ownership-shares 10000}
                :attending {:owners 80 :voting-rights 8000 :co-ownership-shares 8000}
                :cast      {:owners 60 :voting-rights 6000 :co-ownership-shares 6000}
                :in-favour {:owners 45 :voting-rights 7000 :co-ownership-shares 4000}}
        work (r/tally (facts/resolution-rule "DEU" :structural-alteration) ballot)
        cost (r/tally (facts/resolution-rule "DEU" :structural-alteration-cost-allocation) ballot)]
    (is (true? (:passed? work)) "45 of the 60 votes cast is a clear majority: the works are approved")
    (is (false? (:passed? cost))
        "but 4,000 of ALL 10,000 shares is not more than half, so the cost falls on the yes-voters under § 21 Absatz 3")
    (is (= [:co-ownership-shares] (:failed-axes cost)))))

(deftest germany-counts-the-two-axes-against-different-denominators
  ;; The fixture is the point: the votes axis is measured against the
  ;; votes CAST (60) and clears two thirds at 45; the shares axis is
  ;; measured against ALL shares (10,000) and misses half at 4,000.
  ;; A model that used one denominator for both would put the shares
  ;; axis at 4,000/6,000 = 67% and wrongly pass the rule.
  (let [rule (facts/resolution-rule "DEU" :structural-alteration-cost-allocation)
        ballot {:total     {:owners 100 :co-ownership-shares 10000}
                :attending {:owners 80 :co-ownership-shares 8000}
                :cast      {:owners 60 :co-ownership-shares 6000}
                :in-favour {:owners 45 :co-ownership-shares 4000}}
        v (r/tally rule ballot)]
    (is (false? (:passed? v)))
    (is (= [:co-ownership-shares] (:failed-axes v)))
    (is (= [:cast :total] (:bases v)))
    (is (= :cast (:counted-against (first (:axes v)))))
    (is (= :total (:counted-against (second (:axes v)))))
    (is (= 60 (:base (first (:axes v)))) "the votes axis is measured against the 60 votes cast")
    (is (= 10000 (:base (second (:axes v)))) "the shares axis against all 10,000 shares")
    (testing "and it passes once the shares axis actually clears half of ALL shares"
      (is (true? (:passed? (r/tally rule (assoc-in ballot [:in-favour :co-ownership-shares] 5001))))))
    (testing "while exactly half of all shares is not MORE than half"
      (is (false? (:passed? (r/tally rule (assoc-in ballot [:in-favour :co-ownership-shares] 5000))))))))

(deftest germany-permits-a-different-voting-principle-only-by-agreement
  ;; WEG § 25 is dispositiv, but only a Vereinbarung can change it --
  ;; not a resolution of the meeting.
  (let [rule (facts/resolution-rule "DEU" :ordinary)
        ballot (assoc-in shared-ballot [:in-favour :owners] 20)]
    (testing "a recorded AGREEMENT lowering the bar applies"
      (is (true? (:passed? (r/tally rule (assoc ballot :bylaw-override
                                                {:fraction {:numer 1 :denom 3} :recorded? true
                                                 :instrument :agreement}))))))
    (testing "the same override claimed as a mere resolution is refused"
      (let [v (r/tally rule (assoc ballot :bylaw-override
                                   {:fraction {:numer 1 :denom 3} :recorded? true
                                    :instrument :resolution}))]
        (is (false? (:passed? v)))
        (is (= :bylaw-override-not-permitted (get-in v [:effective-fraction :rejected-override :reason])))))))

(deftest germany-requires-three-quarters-of-the-cast-for-a-virtual-meeting
  (let [rule (facts/resolution-rule "DEU" :virtual-meeting)]
    (is (true? (:passed? (r/tally rule (assoc-in shared-ballot [:in-favour :owners] 38)))))
    (is (false? (:passed? (r/tally rule (assoc-in shared-ballot [:in-favour :owners] 37)))))
    (testing "37.5 of 50 is not reachable, so 38 is the boundary and 37 misses"
      (is (= 38.0 (:required (first (:axes (r/tally rule shared-ballot)))))))))

;; ----------------------------- ESP -----------------------------

(deftest spain-moves-the-denominator-between-the-first-and-second-call
  ;; 30 owners / 3,100 quotas in favour out of 100 owners / 10,000
  ;; quotas, with 55 owners / 6,000 quotas attending. Fails on the
  ;; first call, passes on the second -- same meeting, same ballot.
  (let [ballot {:total     {:owners 100 :voting-rights 10000}
                :attending {:owners 55 :voting-rights 6000}
                :in-favour {:owners 30 :voting-rights 3100}}]
    (is (false? (:passed? (r/tally (facts/resolution-rule "ESP" :ordinary-first-call) ballot))))
    (is (true? (:passed? (r/tally (facts/resolution-rule "ESP" :ordinary-second-call) ballot))))))

(deftest spain-counts-owners-and-quotas-independently
  (let [rule (facts/resolution-rule "ESP" :common-services)
        ballot {:total {:owners 100 :voting-rights 10000}
                :in-favour {:owners 62 :voting-rights 5500}}
        v (r/tally rule ballot)]
    (is (false? (:passed? v)) "three fifths of owners but not of quotas")
    (is (= [:voting-rights] (:failed-axes v)))
    (is (true? (:passed? (r/tally rule (assoc-in ballot [:in-favour :voting-rights] 6000)))))))

(deftest spains-fractions-run-from-a-third-to-unanimity
  (let [ballot {:total {:owners 100 :voting-rights 10000}
                :in-favour {:owners 34 :voting-rights 3400}}]
    (is (true? (:passed? (r/tally (facts/resolution-rule "ESP" :telecom-or-energy-infrastructure) ballot))))
    (is (false? (:passed? (r/tally (facts/resolution-rule "ESP" :improvements) ballot))))
    (is (false? (:passed? (r/tally (facts/resolution-rule "ESP" :bylaw-amendment) ballot))))
    (testing "unanimity means every owner and every quota"
      (is (true? (:passed? (r/tally (facts/resolution-rule "ESP" :bylaw-amendment)
                                    {:total {:owners 100 :voting-rights 10000}
                                     :in-favour {:owners 100 :voting-rights 10000}}))))
      (is (false? (:passed? (r/tally (facts/resolution-rule "ESP" :bylaw-amendment)
                                     {:total {:owners 100 :voting-rights 10000}
                                      :in-favour {:owners 99 :voting-rights 10000}})))))))

(deftest spains-deemed-consent-is-recorded-but-never-applied
  ;; Article 17.8 counts a properly summoned absentee who does not
  ;; dissent within 30 days as a vote in favour. Whether notice was
  ;; proper and whether 30 days have run are facts about the world.
  (let [dc (facts/deemed-consent "ESP")]
    (is (= :absent-owners-counted-in-favour (:effect dc)))
    (is (false? (:auto-applied? dc)))
    (is (= 3 (count (:requires dc))))
    (is (re-find #"17\.8" (:article dc))))
  (is (nil? (facts/deemed-consent "JPN"))))

;; ----------------------------- FRA -----------------------------

(deftest france-article-26-needs-different-fractions-on-its-two-axes
  ;; More than half the MEMBERS and at least two thirds of the VOICES.
  ;; 51 of 100 members clears the first; 6,000 of 10,000 voices misses
  ;; the second. A single-fraction model would either pass both or fail
  ;; both.
  (let [rule (facts/resolution-rule "FRA" :double-majority)
        ballot {:total {:owners 100 :voting-rights 10000}
                :in-favour {:owners 51 :voting-rights 6000}}
        v (r/tally rule ballot)]
    (is (false? (:passed? v)))
    (is (= [:voting-rights] (:failed-axes v)))
    (is (true? (:passed? (r/tally rule (assoc-in ballot [:in-favour :voting-rights] 6667)))))
    (testing "and exactly two thirds of the voices is enough, since the statute says AU MOINS"
      (is (true? (:passed? (r/tally rule (-> ballot
                                             (assoc-in [:total :voting-rights] 9000)
                                             (assoc-in [:in-favour :voting-rights] 6000)))))))
    (testing "while exactly half the members is not MORE than half"
      (is (false? (:passed? (r/tally rule (-> ballot
                                              (assoc-in [:in-favour :owners] 50)
                                              (assoc-in [:in-favour :voting-rights] 9000)))))))))

(deftest france-reports-the-article-25-1-second-ballot-without-taking-it
  (let [rule (facts/resolution-rule "FRA" :absolute-majority)
        reached-a-third {:total {:owners 100 :voting-rights 10000}
                         :in-favour {:owners 40 :voting-rights 4000}}
        well-short {:total {:owners 100 :voting-rights 10000}
                    :in-favour {:owners 20 :voting-rights 2000}}]
    (let [v (r/tally rule reached-a-third)]
      (is (false? (:passed? v)) "it did NOT pass -- the fallback is not an outcome")
      (is (true? (:available? (:fallback v))))
      (is (= :ordinary (:to (:fallback v))))
      (is (re-find #"25-1" (:article (:fallback v)))))
    (testing "below a third there is no second ballot to offer"
      (is (nil? (:fallback (r/tally rule well-short)))))
    (testing "and a resolution that passed outright is not offered one either"
      (is (nil? (:fallback (r/tally rule {:total {:owners 100 :voting-rights 10000}
                                          :in-favour {:owners 60 :voting-rights 6000}})))))))

(deftest france-counts-only-voices-not-heads-at-articles-24-and-25
  (is (= [:voting-rights] (:axes (facts/resolution-rule "FRA" :ordinary))))
  (is (= [:voting-rights] (:axes (facts/resolution-rule "FRA" :absolute-majority))))
  (testing "article 26 is the one that counts members as well"
    (is (= [:owners :voting-rights]
           (mapv :axis (:axes (facts/resolution-rule "FRA" :double-majority)))))))

;; ----------------------------- refusals -----------------------------

(deftest a-jurisdiction-whose-source-was-unreachable-still-refuses-to-answer
  ;; SGP and NSW DO have statutory thresholds. Not having read them is
  ;; not a licence to guess.
  (doseq [j ["SGP" "AUS-NSW" "ITA" "CHN"]]
    (is (nil? (facts/resolution-rule j :ordinary)) j)
    (is (thrown? Exception (r/tally (facts/resolution-rule j :ordinary) shared-ballot)) j)))

(deftest a-ballot-recording-more-votes-than-attendance-is-refused
  (is (thrown? Exception
               (r/tally (facts/resolution-rule "DEU" :ordinary)
                        (assoc shared-ballot :cast {:owners 70 :voting-rights 7000
                                                    :co-ownership-shares 7000})))))

(deftest explain-names-the-denominator-of-every-axis
  (let [line (r/explain (r/tally (facts/resolution-rule "DEU" :structural-alteration-cost-allocation)
                                 {:total {:owners 100 :co-ownership-shares 10000}
                                  :attending {:owners 80 :co-ownership-shares 8000}
                                  :cast {:owners 60 :co-ownership-shares 6000}
                                  :in-favour {:owners 45 :co-ownership-shares 4000}}))]
    (is (re-find #"投票" line))
    (is (re-find #"総数" line))
    (is (re-find #"2/3" line))
    (is (re-find #"1/2" line))))
