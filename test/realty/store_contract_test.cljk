(ns realty.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Holdings" (:owner (store/property s "property-1"))))
      (is (= "JPN" (:jurisdiction (store/property s "property-1"))))
      (is (= 0.08 (:management-fee-rate (store/property s "property-1"))))
      (is (nil? (:pending-contract (store/property s "property-1"))))
      (is (= "Acme Roofing" (:vendor (:pending-contract (store/property s "property-4")))))
      (is (= ["property-1" "property-2" "property-3" "property-4" "property-5" "property-6"]
             (mapv :id (store/all-properties s))))
      (is (nil? (store/fee s "fee-1")))
      (is (nil? (store/assessment-of s "property-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/payment-history s)))
      (is (= [] (store/contract-history s)))
      (is (zero? (store/next-sequence s "JPN")))
      (is (zero? (store/contract-sequence s "JPN")))
      (is (false? (store/fee-already-paid? s "fee-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields (including the untouched pending-contract)"
        (store/commit-record! s {:effect :property/upsert
                                 :value {:id "property-4" :status :ready}})
        (is (= :ready (:status (store/property s "property-4"))))
        (is (= "Acme Roofing" (:vendor (:pending-contract (store/property s "property-4"))))
            "pending-contract preserved by a patch that never mentions it"))
      (testing "assessment payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["property-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "property-1"))))
      (testing "fee filing writes a plain fee record (no draft/certificate -- filing moves no capital)"
        (store/commit-record! s {:effect :fee/filed
                                 :payload {:id "fee-1" :property-id "property-1"
                                          :collected-rent 200000 :claimed-fee-amount 16000 :status :filed}})
        (is (= :filed (:status (store/fee s "fee-1"))))
        (is (= 200000 (:collected-rent (store/fee s "fee-1")))))
      (testing "fee payment drafts a payment record with THIS actor's own recomputed amount, not the claimed one, and advances the sequence"
        (store/commit-record! s {:effect :fee/mark-paid :path ["fee-1"]})
        (is (= "JPN-FEE-000000" (get (first (store/payment-history s)) "record_id")))
        (is (= "fee-payment-draft" (get (first (store/payment-history s)) "kind")))
        (is (= 16000.0 (get (first (store/payment-history s)) "paid_amount"))
            "8% of 200,000 recomputed independently, matching the (correct) claimed amount here")
        (is (= :paid (:status (store/fee s "fee-1"))))
        (is (= 1 (count (store/payment-history s))))
        (is (= 1 (store/next-sequence s "JPN")))
        (is (true? (store/fee-already-paid? s "fee-1")))
        (is (false? (store/fee-already-paid? s "fee-2"))))
      (testing "contract execution drafts a contract record, CLEARS the property's pending-contract, and advances the contract sequence"
        (store/commit-record! s {:effect :contract/mark-executed :path ["property-4"]})
        (is (= "GBR-CTR-000000" (get (first (store/contract-history s)) "record_id")))
        (is (= "contract-execution-draft" (get (first (store/contract-history s)) "kind")))
        (is (nil? (:pending-contract (store/property s "property-4"))) "pending-contract cleared after execution")
        (is (= 1 (count (store/contract-history s))))
        (is (= 1 (store/contract-sequence s "GBR"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/property s "nope")))
    (is (= [] (store/all-properties s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/payment-history s)))
    (is (= [] (store/contract-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (is (zero? (store/contract-sequence s "JPN")))
    (store/with-properties s {"x" {:id "x" :owner "o" :jurisdiction "JPN"
                                   :property-type :residential :monthly-rent 100000
                                   :management-fee-rate 0.08 :contract-authorization-limit 100000
                                   :pending-contract nil :status :under-management}})
    (is (= "o" (:owner (store/property s "x"))))
    (is (nil? (:pending-contract (store/property s "x"))))))
