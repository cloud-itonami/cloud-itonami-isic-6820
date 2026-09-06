(ns realty.kumiai.store-contract-test
  "The kumiai Store contract, run against BOTH backends. Proving
  MemStore and the Datomic-backed (langchain.db) store satisfy the same
  contract is what makes 'swap the SSoT' a configuration change rather
  than a rewrite -- the same pattern `realty.store-contract-test`
  establishes for the fee-services actor."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.kumiai.resolution :as res]
            [realty.kumiai.facts :as facts]
            [realty.kumiai.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(def verdict
  (res/tally (facts/resolution-rule "JPN" :ordinary)
             {:total     {:owners 70 :voting-rights 4900}
              :attending {:owners 44 :voting-rights 3080}
              :in-favour {:owners 30 :voting-rights 2100}}))

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "さくら台マンション管理組合" (:name (store/association s "association-1"))))
      (is (= "JPN" (:jurisdiction (store/association s "association-1"))))
      (is (= :under-management (:status (store/association s "association-1"))))
      (is (= :intake (:status (store/association s "association-3"))))
      (is (= 4900 (:total-exclusive-area (store/association s "association-1"))))
      (is (= {:type "3段(ピット2段)昇降式" :spaces 30}
             (:mechanical-parking (store/association s "association-1"))))
      (is (nil? (:pending-works (store/association s "association-1"))))
      (is (= ["association-1" "association-2" "association-3" "association-4" "association-5"]
             (mapv :id (store/all-associations s))))
      (is (= 5 (count (:works (store/plan-of s "association-1")))))
      (is (= 30 (:horizon-years (store/plan-of s "association-1"))))
      (is (nil? (store/plan-of s "association-2")))
      (is (nil? (store/resolution s "res-1")))
      (is (nil? (store/assessment-of s "association-1")))
      (is (nil? (store/projection-of s "association-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/resolution-history s)))
      (is (= [] (store/works-history s)))
      (is (zero? (store/resolution-sequence s "JPN")))
      (is (zero? (store/works-sequence s "JPN")))
      (is (false? (store/works-already-commissioned? s "works-1"))))))

(deftest write-parity
  (doseq [[label s] (backends)]
    (testing label
      (store/commit-record! s {:effect :assessment/set :path ["association-1"]
                               :payload {:jurisdiction "JPN" :checklist ["a"]}})
      (is (= ["a"] (:checklist (store/assessment-of s "association-1"))))

      (store/commit-record! s {:effect :projection/set :path ["association-1"]
                               :payload {:assumptions {:cost-escalation 0.05} :deficit 123.0}})
      (is (= 123.0 (:deficit (store/projection-of s "association-1"))))

      (store/commit-record! s {:effect :resolution/filed :path ["association-1"]
                               :payload {:id "res-1" :association-id "association-1" :kind :ordinary
                                         :budget-amount 150000000 :funding-amount 0 :verdict verdict}})
      (is (= :passed (:status (store/resolution s "res-1"))))
      (is (= "JPN-RES-000000" (:resolution-number (store/resolution s "res-1"))))
      (is (= 1 (store/resolution-sequence s "JPN")))
      (is (= 1 (count (store/resolution-history s))))
      (is (= "attending" (get (first (store/resolution-history s)) "counted_against")))

      (store/commit-record! s {:effect :association/upsert :path ["association-1"]
                               :value {:id "association-1"
                                       :pending-works {:id "works-1" :contractor "南工務店"
                                                       :contract-value 120000000
                                                       :resolution-id "res-1"}}})
      (is (= "南工務店" (:contractor (:pending-works (store/association s "association-1")))))

      (testing "a partial patch that never mentions :pending-works leaves it alone"
        (store/commit-record! s {:effect :association/upsert :path ["association-1"]
                                 :value {:id "association-1" :name "さくら台マンション管理組合"}})
        (is (some? (:pending-works (store/association s "association-1")))))

      (store/commit-record! s {:effect :works/mark-commissioned :path ["association-1"]})
      (testing "commissioning clears the pending package -- the double-commissioning guard"
        (is (nil? (:pending-works (store/association s "association-1")))))
      (is (= "JPN-WRK-000000" (:works-number (store/association s "association-1"))))
      (is (= 1 (store/works-sequence s "JPN")))
      (is (= 1 (count (store/works-history s))))
      (is (= "JPN-RES-000000" (get (first (store/works-history s)) "authorised_by")))
      (is (true? (store/works-already-commissioned? s "works-1")))

      (store/append-ledger! s {:t :committed :op :works/commission})
      (is (= 1 (count (store/ledger s)))))))

(deftest a-failed-resolution-is-recorded-as-failed
  (doseq [[label s] (backends)]
    (testing label
      (let [failing (res/tally (facts/resolution-rule "JPN" :ordinary)
                               {:total     {:owners 70 :voting-rights 4900}
                                :attending {:owners 44 :voting-rights 3080}
                                :in-favour {:owners 10 :voting-rights 700}})]
        (store/commit-record! s {:effect :resolution/filed :path ["association-1"]
                                 :payload {:id "res-f" :association-id "association-1" :kind :ordinary
                                           :budget-amount 1 :verdict failing}})
        (is (= :failed (:status (store/resolution s "res-f"))))
        (is (= false (get (first (store/resolution-history s)) "passed")))))))

(deftest the-ledger-is-append-only-on-both-backends
  (doseq [[label s] (backends)]
    (testing label
      (store/append-ledger! s {:t :a})
      (store/append-ledger! s {:t :b})
      (is (= [:a :b] (mapv :t (store/ledger s)))))))
