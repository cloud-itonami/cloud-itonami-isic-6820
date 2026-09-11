(ns realty.kumiai.governor-contract-test
  "The Condominium-Association Governor's contract.

  Every HARD rule is asserted in BOTH directions: an input that fires
  it, and a neighbouring input that does not. A rule that only ever
  fires -- or a rule the test only ever sees fire -- is
  indistinguishable from a rule that returns `true`, and the clean case
  is what tells them apart.

  Proposals come from the real mock advisor rather than being
  hand-written, so the test exercises the pair (advisor, governor) the
  actor actually runs."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.governor :as gov]
            [realty.kumiai.kumiaillm :as llm]
            [realty.kumiai.store :as store]))

(def ctx {:actor-id "op-1" :actor-role :association-manager :phase 3})
(def flat {:cost-escalation 0.0 :delay-years 0})
(def rising {:cost-escalation 0.05 :delay-years 0})
(def rising-late {:cost-escalation 0.05 :delay-years 2})

(def passing-tally
  {:total     {:owners 70 :voting-rights 4900}
   :attending {:owners 44 :voting-rights 3080}
   :in-favour {:owners 30 :voting-rights 2100}})

(defn- check [db request]
  (gov/check request ctx (llm/infer db request) db))

(defn- rules [v] (set (map :rule (:violations v))))

(defn- assessed!
  "Commit the jurisdiction evidence checklist, as the actor does before
  any works order is possible."
  [db id]
  (let [p (llm/infer db {:op :plan/assess :subject id})]
    (store/commit-record! db {:effect :assessment/set :path [id] :payload (:value p)}))
  db)

(defn- projected!
  [db id assumptions]
  (let [p (llm/infer db {:op :reserve/simulate :subject id :assumptions assumptions})]
    (store/commit-record! db {:effect :projection/set :path [id] :payload (:value p)}))
  db)

(defn- resolved!
  [db {:keys [id budget funding kind] :or {kind :ordinary budget 150000000 funding 0}}]
  (let [p (llm/infer db {:op :resolution/file :subject id :association-id "association-1"
                         :resolution-kind kind :budget-amount budget
                         :funding-amount funding :tally passing-tally})]
    (store/commit-record! db {:effect :resolution/filed :path ["association-1"] :payload (:value p)}))
  db)

(defn- pending!
  [db value]
  (store/commit-record! db {:effect :association/upsert :path ["association-1"]
                            :value {:id "association-1" :pending-works value}})
  db)

(defn- ready-to-commission
  "An association-1 that is clean all the way to the works order: an
  assessment on file, a 0%-escalation projection on file (so the
  reserve funds the works), a passed resolution with a 150,000,000
  budget, and a 120,000,000 package pending."
  []
  (-> (store/seed-db)
      (assessed! "association-1")
      (projected! "association-1" flat)
      (resolved! {:id "res-1"})
      (pending! {:id "works-1" :contractor "南工務店" :contract-value 120000000
                 :resolution-id "res-1"})))

;; ----------------------------- the clean cases -----------------------------

(deftest a-clean-simulation-passes-every-hard-check
  (let [v (check (store/seed-db) {:op :reserve/simulate :subject "association-1" :assumptions rising})]
    (is (false? (:hard? v)))
    (is (empty? (:violations v)))
    (is (true? (:ok? v)))))

(deftest a-clean-resolution-passes-every-hard-check
  (let [v (check (store/seed-db) {:op :resolution/file :subject "res-1"
                                  :association-id "association-1" :resolution-kind :ordinary
                                  :budget-amount 150000000 :tally passing-tally})]
    (is (false? (:hard? v)))
    (is (empty? (:violations v)))))

(deftest a-clean-works-order-clears-the-hard-checks-and-still-escalates
  ;; Both halves matter: nothing HARD fires, AND it never auto-commits.
  (let [v (check (ready-to-commission) {:op :works/commission :subject "association-1"})]
    (is (false? (:hard? v)))
    (is (empty? (:violations v)))
    (is (true? (:high-stakes? v)))
    (is (true? (:escalate? v)))
    (is (false? (:ok? v)))))

;; ----------------------------- HARD: spec basis and status -----------------------------

(deftest a-jurisdiction-with-no-spec-basis-is-refused
  (is (contains? (rules (check (store/seed-db) {:op :plan/assess :subject "association-2"}))
                 :no-spec-basis))
  (testing "and a known jurisdiction is not"
    (is (not (contains? (rules (check (store/seed-db) {:op :plan/assess :subject "association-1"}))
                        :no-spec-basis)))))

(deftest an-association-not-under-management-is-refused
  (let [v (check (store/seed-db) {:op :plan/assess :subject "association-3"})]
    (is (contains? (rules v) :association-not-under-management))
    (testing "and it is the ONLY rule that fires -- the generic spec-basis rule is guarded on it"
      (is (= #{:association-not-under-management} (rules v))))))

;; ----------------------------- HARD: resolutions -----------------------------

(deftest an-unverified-threshold-holds-instead-of-defaulting
  (let [v (check (store/seed-db) {:op :resolution/file :subject "res-x"
                                  :association-id "association-4"
                                  :resolution-kind :common-area-major-change
                                  :budget-amount 1
                                  :tally {:total {:owners 55 :voting-rights 5200}
                                          :attending {:owners 40 :voting-rights 3800}
                                          :in-favour {:owners 35 :voting-rights 3300}}})]
    (is (contains? (rules v) :threshold-unverified))
    (testing "and the generic citation rule does not pile on top of it"
      (is (= #{:threshold-unverified} (rules v))))))

(deftest counting-against-the-wrong-denominator-is-caught
  ;; The advisor's proposal is well-formed, confident and arithmetically
  ;; sound. Only the recompute finds it.
  (let [v (check (store/seed-db) {:op :resolution/file :subject "res-2"
                                  :association-id "association-1" :resolution-kind :ordinary
                                  :budget-amount 150000000 :tally passing-tally
                                  :count-total? true})]
    (is (true? (:hard? v)))
    (is (contains? (rules v) :resolution-tally-mismatch))))

(deftest a-structurally-impossible-tally-is-a-violation-not-a-crash
  (let [v (check (store/seed-db) {:op :resolution/file :subject "res-3"
                                  :association-id "association-1" :resolution-kind :ordinary
                                  :budget-amount 1
                                  :tally {:total {:owners 70 :voting-rights 4900}
                                          :attending {:owners 44 :voting-rights 3080}
                                          :in-favour {:owners 60 :voting-rights 4200}}})]
    (is (contains? (rules v) :tally-malformed))))

;; ----------------------------- HARD: plan and projection -----------------------------

(deftest a-plan-below-the-guidelines-preconditions-is-refused
  (is (contains? (rules (check (store/seed-db) {:op :reserve/simulate :subject "association-5"
                                                :assumptions flat}))
                 :plan-not-comparable))
  (testing "and a conforming plan is not"
    (is (not (contains? (rules (check (store/seed-db) {:op :reserve/simulate :subject "association-1"
                                                       :assumptions flat}))
                        :plan-not-comparable)))))

(deftest a-claimed-deficit-that-does-not-survive-recompute-is-refused
  (is (contains? (rules (check (store/seed-db) {:op :reserve/simulate :subject "association-1"
                                                :assumptions rising :claim-deficit 1}))
                 :simulation-mismatch))
  (testing "and a claim within a yen of the recompute is not"
    (is (not (contains? (rules (check (store/seed-db) {:op :reserve/simulate :subject "association-1"
                                                       :assumptions rising}))
                        :simulation-mismatch)))))

(deftest works-slipping-past-the-horizon-is-refused
  (let [v (check (store/seed-db) {:op :reserve/simulate :subject "association-1"
                                  :assumptions rising-late})]
    (is (contains? (rules v) :unmeasured-outflow))
    (testing "the same escalation without the slip is clean"
      (is (not (contains? (rules (check (store/seed-db) {:op :reserve/simulate :subject "association-1"
                                                         :assumptions rising}))
                          :unmeasured-outflow))))))

;; ----------------------------- HARD: works orders -----------------------------

(deftest commissioning-with-no-projection-on-file-is-refused
  (let [db (-> (store/seed-db)
               (assessed! "association-1")
               (resolved! {:id "res-1"})
               (pending! {:id "works-1" :contractor "南工務店" :contract-value 120000000
                          :resolution-id "res-1"}))]
    (is (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                   :no-projection-on-file))))

(deftest commissioning-with-nothing-pending-is-refused
  (let [db (-> (store/seed-db) (assessed! "association-1") (projected! "association-1" flat))]
    (is (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                   :works-not-authorised))))

(deftest commissioning-against-a-resolution-that-failed-is-refused
  (let [db (-> (store/seed-db)
               (assessed! "association-1")
               (projected! "association-1" flat))
        failing (llm/infer db {:op :resolution/file :subject "res-f" :association-id "association-1"
                               :resolution-kind :ordinary :budget-amount 150000000
                               :tally {:total {:owners 70 :voting-rights 4900}
                                       :attending {:owners 44 :voting-rights 3080}
                                       :in-favour {:owners 10 :voting-rights 700}}})]
    (store/commit-record! db {:effect :resolution/filed :path ["association-1"] :payload (:value failing)})
    (pending! db {:id "works-1" :contractor "南工務店" :contract-value 1 :resolution-id "res-f"})
    (is (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                   :works-not-authorised))))

(deftest an-order-above-the-voted-budget-is-refused
  (let [db (-> (store/seed-db)
               (assessed! "association-1")
               (projected! "association-1" flat)
               (resolved! {:id "res-1" :budget 150000000})
               (pending! {:id "works-9" :contractor "南工務店" :contract-value 200000000
                          :resolution-id "res-1"}))]
    (is (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                   :works-exceeds-resolution-budget)))
  (testing "and an order at the budget is not"
    (let [db (-> (store/seed-db)
                 (assessed! "association-1")
                 (projected! "association-1" flat)
                 (resolved! {:id "res-1" :budget 150000000})
                 (pending! {:id "works-9" :contractor "南工務店" :contract-value 150000000
                            :resolution-id "res-1"}))]
      (is (not (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                          :works-exceeds-resolution-budget))))))

(deftest an-order-the-reserve-cannot-fund-is-refused
  ;; Same association, same works, same resolution. Only the projection
  ;; that the board committed to the ledger differs.
  (let [with-rising (-> (store/seed-db)
                        (assessed! "association-1")
                        (projected! "association-1" rising)
                        (resolved! {:id "res-1"})
                        (pending! {:id "works-1" :contractor "南工務店" :contract-value 120000000
                                   :resolution-id "res-1"}))]
    (is (contains? (rules (check with-rising {:op :works/commission :subject "association-1"}))
                   :reserve-cannot-fund-works)))
  (testing "a resolution carrying enough borrowing or levy clears it"
    (let [db (-> (store/seed-db)
                 (assessed! "association-1")
                 (projected! "association-1" rising)
                 (resolved! {:id "res-1" :funding 500000000})
                 (pending! {:id "works-1" :contractor "南工務店" :contract-value 120000000
                            :resolution-id "res-1"}))]
      (is (not (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                          :reserve-cannot-fund-works))))))

(deftest commissioning-without-the-jurisdictions-evidence-is-refused
  (let [db (-> (store/seed-db)
               (projected! "association-1" flat)
               (resolved! {:id "res-1"})
               (pending! {:id "works-1" :contractor "南工務店" :contract-value 120000000
                          :resolution-id "res-1"}))]
    (is (contains? (rules (check db {:op :works/commission :subject "association-1"}))
                   :evidence-incomplete))))

;; ----------------------------- SOFT -----------------------------

(deftest a-vote-exactly-on-the-line-escalates-without-holding
  (let [db (store/seed-db)
        v (check db {:op :resolution/file :subject "res-b" :association-id "association-1"
                     :resolution-kind :common-area-major-change :budget-amount 1
                     :tally {:total     {:owners 70 :voting-rights 4900}
                             :attending {:owners 40 :voting-rights 2800}
                             :in-favour {:owners 30 :voting-rights 2100}}})]
    (is (false? (:hard? v)))
    (is (true? (:escalate? v)))
    (is (some #{:resolution-on-boundary} (map :advisory (:advisories v))))))

(deftest a-reserve-outside-the-guideline-band-escalates-and-does-not-hold
  ;; The guideline itself says a level outside the band is not
  ;; automatically improper, so this must not be a HARD rule.
  (let [db (store/seed-db)]
    (store/commit-record! db {:effect :association/upsert :path ["association-1"]
                              :value {:id "association-1" :monthly-per-m2 40}})
    (let [v (check db {:op :reserve/simulate :subject "association-1" :assumptions flat})]
      (is (false? (:hard? v)))
      (is (true? (:escalate? v)))
      (is (some #{:outside-guideline-band} (map :advisory (:advisories v)))))))

(deftest low-confidence-escalates
  (is (true? (:escalate? (gov/check {:op :reserve/simulate :subject "association-1" :assumptions flat}
                                    ctx
                                    {:cites ["x"] :confidence 0.1 :value {}}
                                    (store/seed-db))))))

(deftest hold-facts-name-the-rules-that-fired
  (let [v (check (store/seed-db) {:op :plan/assess :subject "association-2"})
        f (gov/hold-fact {:op :plan/assess :subject "association-2"} ctx v)]
    (is (= :governor-hold (:t f)))
    (is (= :hold (:disposition f)))
    (is (= [:no-spec-basis] (:basis f)))))
