(ns realty.registry-test
  (:require [clojure.test :refer [deftest is]]
            [realty.registry :as r]))

;; ----------------------------- compute-management-fee -----------------------------

(deftest management-fee-is-a-fixed-percentage-of-collected-rent
  (is (= 16000.0 (r/compute-management-fee {:management-fee-rate 0.08} 200000)))
  (is (= 0.0 (r/compute-management-fee {:management-fee-rate 0.08} 0))))

(deftest compute-management-fee-validation-rules
  (is (thrown? Exception (r/compute-management-fee {:management-fee-rate 0.08} -1)))
  (is (thrown? Exception (r/compute-management-fee {:management-fee-rate 1.5} 100)))
  (is (thrown? Exception (r/compute-management-fee {:management-fee-rate nil} 100)))
  (is (thrown? Exception (r/compute-management-fee {} 100))))

;; ----------------------------- register-fee-payment -----------------------------

(deftest fee-payment-is-a-draft-not-a-real-payment
  (let [result (r/register-fee-payment "property-1" "fee-1" 200000 16000 "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest fee-payment-assigns-fee-number
  (let [result (r/register-fee-payment "property-1" "fee-1" 200000 16000 "JPN" 7)]
    (is (= (get result "fee_number") "JPN-FEE-000007"))
    (is (= (get-in result ["record" "property_id"]) "property-1"))
    (is (= (get-in result ["record" "fee_id"]) "fee-1"))
    (is (= (get-in result ["record" "paid_amount"]) 16000))
    (is (= (get-in result ["record" "kind"]) "fee-payment-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest fee-payment-validation-rules
  (is (thrown? Exception (r/register-fee-payment "" "fee-1" 200000 16000 "JPN" 0)))
  (is (thrown? Exception (r/register-fee-payment "property-1" "" 200000 16000 "JPN" 0)))
  (is (thrown? Exception (r/register-fee-payment "property-1" "fee-1" -1 16000 "JPN" 0)))
  (is (thrown? Exception (r/register-fee-payment "property-1" "fee-1" 200000 -1 "JPN" 0)))
  (is (thrown? Exception (r/register-fee-payment "property-1" "fee-1" 200000 16000 "" 0)))
  (is (thrown? Exception (r/register-fee-payment "property-1" "fee-1" 200000 16000 "JPN" -1))))

(deftest payment-history-is-append-only
  (let [p1 (r/register-fee-payment "property-1" "fee-1" 200000 16000 "JPN" 0)
        hist (r/append [] p1)
        p2 (r/register-fee-payment "property-1" "fee-2" 200000 16000 "JPN" 1)
        hist2 (r/append hist p2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-FEE-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-FEE-000001" (get-in hist2 [1 "record_id"])))))

;; ----------------------------- register-contract-execution -----------------------------

(deftest contract-execution-is-a-draft-not-a-real-execution
  (let [result (r/register-contract-execution "property-4" "Acme Roofing" :maintenance 800000 "GBR" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest contract-execution-assigns-contract-number
  (let [result (r/register-contract-execution "property-4" "Acme Roofing" :maintenance 800000 "GBR" 7)]
    (is (= (get result "contract_number") "GBR-CTR-000007"))
    (is (= (get-in result ["record" "property_id"]) "property-4"))
    (is (= (get-in result ["record" "vendor"]) "Acme Roofing"))
    (is (= (get-in result ["record" "contract_type"]) "maintenance"))
    (is (= (get-in result ["record" "kind"]) "contract-execution-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest contract-execution-validation-rules
  (is (thrown? Exception (r/register-contract-execution "" "Acme Roofing" :maintenance 800000 "GBR" 0)))
  (is (thrown? Exception (r/register-contract-execution "property-4" "" :maintenance 800000 "GBR" 0)))
  (is (thrown? Exception (r/register-contract-execution "property-4" "Acme Roofing" :maintenance -1 "GBR" 0)))
  (is (thrown? Exception (r/register-contract-execution "property-4" "Acme Roofing" :maintenance 800000 "" 0)))
  (is (thrown? Exception (r/register-contract-execution "property-4" "Acme Roofing" :maintenance 800000 "GBR" -1))))

(deftest contract-history-is-append-only
  (let [c1 (r/register-contract-execution "property-4" "Acme Roofing" :maintenance 800000 "GBR" 0)
        hist (r/append [] c1)
        c2 (r/register-contract-execution "property-4" "Acme Roofing" :maintenance 200000 "GBR" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "GBR-CTR-000000" (get-in hist2 [0 "record_id"])))
    (is (= "GBR-CTR-000001" (get-in hist2 [1 "record_id"])))))
