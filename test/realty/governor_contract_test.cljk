(ns realty.governor-contract-test
  "The governor contract as executable tests -- the real-estate-fee-
  services analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    Realty-Fee-LLM never pays a fee or executes a contract the Real-
    Estate Fee-Services Governor would reject, `:fee/pay`/`:contract/
    execute` NEVER auto-commit at any phase, `:property/intake`/
    `:fee/file` (no capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [realty.store :as store]
            [realty.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :property-manager :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess-property1!
  "Walks property-1 through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject "property-1"} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- file-fee!
  [actor tid fee-id property-id collected-rent claimed-fee-amount]
  (exec-op actor tid {:op :fee/file :subject fee-id :property-id property-id
                      :collected-rent collected-rent :claimed-fee-amount claimed-fee-amount} operator))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :property/intake :subject "property-1"
                   :patch {:id "property-1" :status :ready}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :ready (:status (store/property db "property-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "property-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "property-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "property-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "property-1")) "no assessment written"))))

(deftest fee-file-against-property-not-under-management-is-held
  (testing "a fee filed for a property never taken under management -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (file-fee! actor "t4" "fee-1" "property-3" 150000 12000)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:property-not-under-management} (-> (store/ledger db) first :basis)))
      (is (nil? (store/fee db "fee-1")) "no fee written"))))

(deftest fee-file-against-property-under-management-auto-commits
  (testing ":fee/file moves no capital yet -- auto-eligible at phase 3, once the property is under management"
    (let [[db actor] (fresh)
          res (file-fee! actor "t5" "fee-1" "property-1" 200000 16000)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= :filed (:status (store/fee db "fee-1"))) "SSoT actually updated"))))

(deftest fee-pay-without-assessment-is-held
  (testing "fee/pay before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          _ (file-fee! actor "t6pre" "fee-1" "property-1" 200000 16000)
          res (exec-op actor "t6" {:op :fee/pay :subject "fee-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) last :basis))))))

(deftest fee-pay-with-missing-fee-is-held
  (testing "paying a fee id that was never filed -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :fee/pay :subject "fee-999"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:fee-missing} (-> (store/ledger db) first :basis)))
      (is (empty? (store/payment-history db))))))

(deftest fee-pay-mismatch-is-held
  (testing "a fee whose claimed amount does not match this actor's own independent recompute -> HOLD"
    (let [[db actor] (fresh)
          _ (assess-property1! actor "t8pre")
          _ (file-fee! actor "t8file" "fee-1" "property-1" 200000 99999)
          res (exec-op actor "t8" {:op :fee/pay :subject "fee-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:fee-calculation-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/payment-history db))))))

(deftest fee-pay-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, correctly-computed fee still ALWAYS interrupts for human approval -- actuation/pay-fee is never auto"
    (let [[db actor] (fresh)
          _ (assess-property1! actor "t9pre")
          _ (file-fee! actor "t9file" "fee-1" "property-1" 200000 16000)
          r1 (exec-op actor "t9" {:op :fee/pay :subject "fee-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, fee-payment record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (= :paid (:status (store/fee db "fee-1"))))
          (is (= 1 (count (store/payment-history db))) "one draft payment record")))))
  (testing "reject -> hold, nothing paid"
    (let [[db actor] (fresh)
          _ (assess-property1! actor "t10pre")
          _ (file-fee! actor "t10file" "fee-1" "property-1" 200000 16000)
          _ (exec-op actor "t10" {:op :fee/pay :subject "fee-1"} operator)
          r2 (g/run* actor {:approval {:status :rejected :by "op-1"}}
                     {:thread-id "t10" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/payment-history db)) "nothing paid on reject"))))

(deftest fee-pay-double-payment-is-held
  (testing "paying the same fee twice -> HOLD on the second attempt, even though the figures match cleanly"
    (let [[db actor] (fresh)
          _ (assess-property1! actor "t11pre")
          _ (file-fee! actor "t11file" "fee-1" "property-1" 200000 16000)
          _ (exec-op actor "t11a" {:op :fee/pay :subject "fee-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :fee/pay :subject "fee-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:double-payment} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/payment-history db))) "still only the one earlier payment"))))

(deftest contract-execute-with-no-pending-contract-is-held
  (testing "executing a contract for a property with no pending contract -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t12" {:op :contract/execute :subject "property-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:contract-missing} (-> (store/ledger db) first :basis)))
      (is (empty? (store/contract-history db))))))

(deftest contract-execute-exceeding-authorization-is-held
  (testing "a pending contract whose value exceeds the property's own authorization limit -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t13" {:op :contract/execute :subject "property-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:contract-exceeds-authorization} (-> (store/ledger db) first :basis)))
      (is (empty? (store/contract-history db))))))

(deftest contract-execute-always-escalates-then-human-decides
  (testing "a clean, within-authorization contract still ALWAYS interrupts for human approval -- actuation/execute-contract is never auto"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t14" {:op :contract/execute :subject "property-4"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, contract-execution record drafted, pending-contract cleared"
        (let [r2 (approve! actor "t14")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (nil? (:pending-contract (store/property db "property-4"))))
          (is (= 1 (count (store/contract-history db))) "one draft execution record")))))
  (testing "reject -> hold, nothing executed"
    (let [[db actor] (fresh)
          _ (exec-op actor "t15" {:op :contract/execute :subject "property-4"} operator)
          r2 (g/run* actor {:approval {:status :rejected :by "op-1"}}
                     {:thread-id "t15" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/contract-history db)) "nothing executed on reject")
      (is (some? (:pending-contract (store/property db "property-4"))) "pending-contract NOT cleared on reject"))))

(deftest contract-execute-double-execution-is-held
  (testing "executing the SAME property's contract twice -> HOLD on the second attempt via the SAME contract-missing check (pending-contract cleared by the first execution)"
    (let [[db actor] (fresh)
          _ (exec-op actor "t16a" {:op :contract/execute :subject "property-4"} operator)
          _ (approve! actor "t16a")
          res (exec-op actor "t16" {:op :contract/execute :subject "property-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/contract-history db))) "still only the one earlier execution"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :property/intake :subject "property-1"
                          :patch {:id "property-1" :status :ready}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "property-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
