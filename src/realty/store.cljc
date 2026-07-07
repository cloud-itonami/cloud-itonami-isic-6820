(ns realty.store
  "SSoT for the real-estate-fee-services actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  `cloud-itonami-isic-6511`'s `underwriting.store` / `cloud-itonami-
  isic-6512`'s `casualty.store` / `cloud-itonami-isic-6621`'s
  `adjustment.store` / `cloud-itonami-isic-6622`'s `intermediation.
  store` / `cloud-itonami-isic-6629`'s `auxiliary.store` / `cloud-
  itonami-isic-6520`'s `reinsurance.store` / `cloud-itonami-isic-6530`'s
  `pension.store` use:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/realty/store_contract_test.clj), which is the whole point: the
  actor, the Real-Estate Fee-Services Governor and the audit ledger
  never know which SSoT they run on.

  Like `pension.store`, this Store has NO separate `party` concept: a
  managed property IS its own core entity here, and a pending
  contract-to-execute is carried INLINE on the property record
  (`:pending-contract`) rather than in a separate collection -- there
  is exactly one contract pending execution per property at a time, so
  a separate collection would be an unused indirection. Executing a
  contract CLEARS `:pending-contract` to nil, which doubles as the
  double-execution guard: a repeat execution attempt naturally falls
  into the SAME 'no contract pending' check a never-had-one property
  would also trigger -- one accurate check for two occurrences of the
  same underlying fact, not a workaround.

  The ledger stays append-only on every backend: 'which management fee
  was paid for which property on what jurisdictional basis, which
  vendor/maintenance contract was executed on an owner's behalf,
  approved by whom' is always a query over an immutable log -- the
  audit trail a property owner trusting a manager needs, and the
  evidence an operator needs if a fee payment or a contract execution
  is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [realty.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (property [s id])
  (all-properties [s])
  (fee [s id])
  (assessment-of [s property-id] "committed jurisdiction disclosure/trust-account assessment, or nil")
  (ledger [s])
  (payment-history [s] "the append-only fee-payment history (realty.registry drafts)")
  (contract-history [s] "the append-only contract-execution history (realty.registry drafts)")
  (next-sequence [s jurisdiction] "next fee-payment-number sequence for a jurisdiction")
  (contract-sequence [s jurisdiction] "next contract-execution-number sequence for a jurisdiction")
  (fee-already-paid? [s fee-id] "has this fee already been paid?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-properties [s properties] "replace/seed the property directory (map id->property)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained property set covering both actuation
  lifecycles (fee payment, contract execution) so the actor + tests
  run offline."
  []
  {:properties
   {"property-1" {:id "property-1" :owner "Sakura Holdings" :address "1-2-3 Shibuya, Tokyo"
                  :property-type :residential :monthly-rent 200000 :management-fee-rate 0.08
                  :contract-authorization-limit 500000 :pending-contract nil
                  :jurisdiction "JPN" :status :under-management}
    "property-2" {:id "property-2" :owner "Atlantis Estates" :address "1 Main St, Atlantis"
                  :property-type :commercial :monthly-rent 500000 :management-fee-rate 0.06
                  :contract-authorization-limit 1000000 :pending-contract nil
                  :jurisdiction "ATL" :status :under-management}
    "property-3" {:id "property-3" :owner "田中 三郎" :address "4-5-6 Minato, Tokyo"
                  :property-type :residential :monthly-rent 150000 :management-fee-rate 0.08
                  :contract-authorization-limit 300000 :pending-contract nil
                  :jurisdiction "JPN" :status :intake}
    "property-4" {:id "property-4" :owner "Britannia Estates" :address "10 Baker St, London"
                  :property-type :residential :monthly-rent 300000 :management-fee-rate 0.10
                  :contract-authorization-limit 1000000
                  :pending-contract {:vendor "Acme Roofing" :contract-type :maintenance :contract-value 800000}
                  :jurisdiction "GBR" :status :under-management}
    "property-5" {:id "property-5" :owner "Britannia Estates" :address "22 Oxford Rd, London"
                  :property-type :commercial :monthly-rent 400000 :management-fee-rate 0.10
                  :contract-authorization-limit 500000
                  :pending-contract {:vendor "Acme Roofing" :contract-type :maintenance :contract-value 900000}
                  :jurisdiction "GBR" :status :under-management}
    "property-6" {:id "property-6" :owner "佐藤 商事" :address "7-8-9 Chiyoda, Tokyo"
                  :property-type :commercial :monthly-rent 250000 :management-fee-rate 0.07
                  :contract-authorization-limit 400000 :pending-contract nil
                  :jurisdiction "JPN" :status :under-management}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- pay-fee!
  "Backend-agnostic `:fee/mark-paid` -- looks up the fee + its property
  via the protocol, INDEPENDENTLY recomputes the fee amount via
  `registry/compute-management-fee` (never trusts the fee's own claimed
  amount -- the governor has already verified they match within
  tolerance, but the authoritative record persisted is always this
  vehicle's own math, the same discipline `auxiliary.store/finalize!`/
  `reinsurance.store/pay-recovery!` use), and returns
  {:result .. :fee-patch ..} for the caller to persist."
  [s fee-id]
  (let [f (fee s fee-id)
        p (property s (:property-id f))
        recomputed (registry/compute-management-fee p (:collected-rent f))
        seq-n (next-sequence s (:jurisdiction p))
        result (registry/register-fee-payment
                (:property-id f) fee-id (:collected-rent f) recomputed (:jurisdiction p) seq-n)]
    {:result result
     :fee-patch {:status :paid
                :fee-number (get result "fee_number")}}))

(defn- execute-contract!
  "Backend-agnostic `:contract/mark-executed` -- looks up the property
  and its pending contract via the protocol, drafts the contract-
  execution record, and returns {:result .. :property-patch ..} for the
  caller to persist. `:property-patch` CLEARS `:pending-contract` to
  nil -- see ns docstring for why this doubles as the double-execution
  guard instead of a separate check."
  [s property-id]
  (let [p (property s property-id)
        pc (:pending-contract p)
        seq-n (contract-sequence s (:jurisdiction p))
        result (registry/register-contract-execution
                property-id (:vendor pc) (:contract-type pc) (:contract-value pc) (:jurisdiction p) seq-n)]
    {:result result
     :property-patch {:pending-contract nil
                      :contract-number (get result "contract_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (property [_ id] (get-in @a [:properties id]))
  (all-properties [_] (sort-by :id (vals (:properties @a))))
  (fee [_ id] (get-in @a [:fees id]))
  (assessment-of [_ property-id] (get-in @a [:assessments property-id]))
  (ledger [_] (:ledger @a))
  (payment-history [_] (:payments @a))
  (contract-history [_] (:contracts @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (contract-sequence [_ jurisdiction] (get-in @a [:contract-sequences jurisdiction] 0))
  (fee-already-paid? [_ fee-id] (= :paid (get-in @a [:fees fee-id :status])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :property/upsert
      (swap! a update-in [:properties (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :fee/filed
      (swap! a assoc-in [:fees (:id payload)] payload)

      :fee/mark-paid
      (let [fee-id (first path)
            {:keys [result fee-patch]} (pay-fee! s fee-id)
            jurisdiction (:jurisdiction (property s (:property-id (fee s fee-id))))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update-in [:fees fee-id] merge fee-patch)
                       (update :payments registry/append result))))
        result)

      :contract/mark-executed
      (let [property-id (first path)
            {:keys [result property-patch]} (execute-contract! s property-id)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:contract-sequences (:jurisdiction (get-in state [:properties property-id]))] (fnil inc 0))
                       (update-in [:properties property-id] merge property-patch)
                       (update :contracts registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-properties [s properties] (when (seq properties) (swap! a assoc :properties properties)) s))

(defn seed-db
  "A MemStore seeded with the demo property set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :ledger [] :sequences {}
                           :fees {} :payments [] :contract-sequences {} :contracts []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (pending-contract, assessment payloads, ledger
  facts, payment/contract records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:property/id                {:db/unique :db.unique/identity}
   :fee/id                     {:db/unique :db.unique/identity}
   :assessment/property-id     {:db/unique :db.unique/identity}
   :ledger/seq                 {:db/unique :db.unique/identity}
   :payment/seq                {:db/unique :db.unique/identity}
   :contract/seq               {:db/unique :db.unique/identity}
   :sequence/jurisdiction      {:db/unique :db.unique/identity}
   :contract-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- property->tx
  "`:pending-contract` is written whenever the PATCH mentions the key at
  all (`contains?`, not truthiness) -- a legitimate value is `nil`
  ('no contract currently pending'), so a truthiness check would skip
  writing a genuine clear-to-nil. But it must NOT be written on a
  partial patch that never mentions the key at all (e.g. a bare
  `{:status :ready}` upsert) -- writing `(enc nil)` unconditionally on
  every patch would silently clobber an existing pending contract."
  [{:keys [id owner address property-type monthly-rent management-fee-rate
          contract-authorization-limit jurisdiction status contract-number] :as m}]
  (cond-> {:property/id id}
    owner                          (assoc :property/owner owner)
    address                        (assoc :property/address address)
    property-type                  (assoc :property/property-type property-type)
    monthly-rent                   (assoc :property/monthly-rent monthly-rent)
    management-fee-rate            (assoc :property/management-fee-rate management-fee-rate)
    contract-authorization-limit   (assoc :property/contract-authorization-limit contract-authorization-limit)
    (contains? m :pending-contract) (assoc :property/pending-contract (enc (:pending-contract m)))
    jurisdiction                   (assoc :property/jurisdiction jurisdiction)
    status                         (assoc :property/status status)
    contract-number                (assoc :property/contract-number contract-number)))

(def ^:private property-pull
  [:property/id :property/owner :property/address :property/property-type :property/monthly-rent
   :property/management-fee-rate :property/contract-authorization-limit :property/pending-contract
   :property/jurisdiction :property/status :property/contract-number])

(defn- pull->property [m]
  (when (:property/id m)
    {:id (:property/id m) :owner (:property/owner m) :address (:property/address m)
     :property-type (:property/property-type m) :monthly-rent (:property/monthly-rent m)
     :management-fee-rate (:property/management-fee-rate m)
     :contract-authorization-limit (:property/contract-authorization-limit m)
     :pending-contract (dec* (:property/pending-contract m))
     :jurisdiction (:property/jurisdiction m) :status (:property/status m)
     :contract-number (:property/contract-number m)}))

(defn- fee->tx [{:keys [id property-id collected-rent claimed-fee-amount status fee-number]}]
  (cond-> {:fee/id id}
    property-id          (assoc :fee/property-id property-id)
    collected-rent       (assoc :fee/collected-rent collected-rent)
    claimed-fee-amount   (assoc :fee/claimed-fee-amount claimed-fee-amount)
    status               (assoc :fee/status status)
    fee-number           (assoc :fee/fee-number fee-number)))

(def ^:private fee-pull
  [:fee/id :fee/property-id :fee/collected-rent :fee/claimed-fee-amount :fee/status :fee/fee-number])

(defn- pull->fee [m]
  (when (:fee/id m)
    {:id (:fee/id m) :property-id (:fee/property-id m)
     :collected-rent (:fee/collected-rent m) :claimed-fee-amount (:fee/claimed-fee-amount m)
     :status (:fee/status m) :fee-number (:fee/fee-number m)}))

(defrecord DatomicStore [conn]
  Store
  (property [_ id]
    (pull->property (d/pull (d/db conn) property-pull [:property/id id])))
  (all-properties [_]
    (->> (d/q '[:find [?id ...] :where [?e :property/id ?id]] (d/db conn))
         (map #(pull->property (d/pull (d/db conn) property-pull [:property/id %])))
         (sort-by :id)))
  (fee [_ id]
    (pull->fee (d/pull (d/db conn) fee-pull [:fee/id id])))
  (assessment-of [_ property-id]
    (dec* (d/q '[:find ?p . :in $ ?pid
                :where [?a :assessment/property-id ?pid] [?a :assessment/payload ?p]]
              (d/db conn) property-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (payment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :payment/seq ?s] [?e :payment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (contract-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :contract/seq ?s] [?e :contract/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (contract-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :contract-sequence/jurisdiction ?j] [?e :contract-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (fee-already-paid? [s fee-id]
    (= :paid (:status (fee s fee-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :property/upsert
      (d/transact! conn [(property->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/property-id (first path) :assessment/payload (enc payload)}])

      :fee/filed
      (d/transact! conn [(fee->tx payload)])

      :fee/mark-paid
      (let [fee-id (first path)
            {:keys [result fee-patch]} (pay-fee! s fee-id)
            jurisdiction (:jurisdiction (property s (:property-id (fee s fee-id))))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(fee->tx (assoc fee-patch :id fee-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      {:payment/seq (count (payment-history s)) :payment/record (enc (get result "record"))}])
        result)

      :contract/mark-executed
      (let [property-id (first path)
            {:keys [result property-patch]} (execute-contract! s property-id)
            jurisdiction (:jurisdiction (property s property-id))
            next-n (inc (contract-sequence s jurisdiction))]
        (d/transact! conn
                     [(property->tx (assoc property-patch :id property-id))
                      {:contract-sequence/jurisdiction jurisdiction :contract-sequence/next next-n}
                      {:contract/seq (count (contract-history s)) :contract/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-properties [s properties]
    (when (seq properties) (d/transact! conn (mapv property->tx (vals properties)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:properties ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [properties]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-properties s properties))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo property set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
