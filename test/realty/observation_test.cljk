(ns realty.observation-test
  "Deterministic contract tests for `realty.observation` (fee-observation/1).

  Every fixture below is SYNTHETIC and marked as such: the publication
  references, jurisdiction keys, amounts and requirement counts are invented
  for the contract tests and represent NO real publication, NO real
  regulator decision and NO real fee schedule. The receipt URLs are the
  public provenance URLs the catalog itself already cites (`realty.facts`);
  the content-hashes are sha256 over the synthetic fixture strings
  (computed once, hardcoded). No network, no I/O, no model — the whole file
  runs offline and deterministically."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.facts :as facts]
            [realty.observation :as obs]))

;; --- helpers -----------------------------------------------------------------

(defn- refusal-of
  "The :refusal/code a thunk raises, or nil if it returned normally."
  [thunk]
  (try (thunk) nil (catch Exception e (obs/refusal-code e))))

;; Synthetic sha256 digests over the synthetic fixture strings (computed once
;; outside the test with shasum -a 256, hardcoded here).
(def ^:private ny-dos-hash
  "9d8a562b4d2ab991b7a891622a7696b05a7798820d5cef4f45ba208817c3b2bf")
(def ^:private dluhc-hash
  "74334ce117c840349deeff7d3da28932fa4534ec8a5ab4fa7bf0cbf0f34971ee")
(def ^:private mlit-fee-hash
  "b661a382d794270289a6c46add0fcdb84fda346a3d5a61ac9579848da645d99a")

(defn- ny-dos-receipt []
  (obs/receipt
   {:receipt/id (str "receipt:" (subs ny-dos-hash 0 16) ":2026-09-01")
    :receipt/source-url "https://dos.ny.gov/professional-licensing"
    :receipt/source-class :official-regulator
    :receipt/source-language "en"
    :receipt/issuing-entity "NY Department of State — Division of Licensing Services"
    :receipt/jurisdiction "USA-NY"
    :receipt/content-hash ny-dos-hash
    :receipt/observed-at "2026-09-01"
    :receipt/asserted-at "2026-08-28"
    :receipt/method :verbatim-citation
    :receipt/note "SYNTHETIC FIXTURE — not a real regulator publication; the
                   URL is the catalog's own provenance citation"}))

(defn- mlit-fee-receipt []
  (obs/receipt
   {:receipt/id (str "receipt:" (subs mlit-fee-hash 0 16) ":2026-09-01")
    :receipt/source-url "https://www.mlit.go.jp/"
    :receipt/source-class :official-regulator
    :receipt/source-language "ja"
    :receipt/issuing-entity "国土交通省（MLIT）"
    :receipt/jurisdiction "JPN"
    :receipt/content-hash mlit-fee-hash
    :receipt/observed-at "2026-09-01"
    :receipt/asserted-at "2026-08-30"
    :receipt/method :official-api
    :receipt/note "SYNTHETIC FIXTURE — not a real fee disclosure"}))

(defn- dluhc-receipt []
  (obs/receipt
   {:receipt/id (str "receipt:" (subs dluhc-hash 0 16) ":2026-09-01")
    :receipt/source-url "https://www.gov.uk/government/organisations/department-for-levelling-up-housing-and-communities"
    :receipt/source-class :official-government-portal
    :receipt/source-language "en"
    :receipt/issuing-entity "DLUHC (client money protection / property agents)"
    :receipt/jurisdiction "GBR"
    :receipt/content-hash dluhc-hash
    :receipt/observed-at "2026-09-01"
    :receipt/asserted-at "2026-08-28"
    :receipt/method :verbatim-citation
    :receipt/note "SYNTHETIC FIXTURE — not a real CMP publication"}))

(defn- jpn-subject []
  (obs/subject {:subject/id "JPN"
                :subject/scope :national}))

(defn- ny-subject []
  (obs/subject {:subject/id "USA-NY"
                :subject/scope :sub-national-exemplar}))

(defn- gbr-subject []
  (obs/subject {:subject/id "GBR"
                :subject/scope :national}))

(defn- ny-fee-schedule-event []
  {:event/type :fee-schedule-republished
   :event/publication-ref "NY-DOS-SYNTH-2026-081"
   :event/asserted-at "2026-08-30"
   :event/publisher "NY Department of State — Division of Licensing Services"
   :event/subject-id "USA-NY"})

(defn- jpn-requirement-event []
  {:event/type :requirement-revised
   :event/publication-ref "MLIT-SYNTH-2026-074"
   :event/asserted-at "2026-08-30"
   :event/publisher "国土交通省（MLIT）"
   :event/subject-id "JPN"})

(defn- ny-licence-fee-figure []
  (obs/figure
   {:figure/kind :disclosed-fee
    :figure/raw "License fee: USD 550 [synthetic]"
    :figure/amount 550
    :figure/currency "USD"
    :figure/nominal-at "2026-08-30"
    :figure/event-ref "NY-DOS-SYNTH-2026-081"
    :figure/source (:receipt/id (ny-dos-receipt))}))

(defn- ny-evidence-count-figure []
  (obs/figure
   {:figure/kind :requirement-count
    :figure/raw "Required evidence items: 4 [synthetic]"
    :figure/value 4
    :figure/source (:receipt/id (ny-dos-receipt))}))

(defn- jpn-fee-figure []
  (obs/figure
   {:figure/kind :disclosed-fee
    :figure/raw "手数料 55,000円（令和8年8月30日時点）［合成値］"
    :figure/amount 55000
    :figure/currency "JPY"
    :figure/nominal-at "2026-08-30"
    :figure/event-ref "MLIT-SYNTH-2026-074"
    :figure/source (:receipt/id (mlit-fee-receipt))}))

(defn- ny-observation
  "First generation: one sub-national exemplar jurisdiction, one republished
  fee schedule, licence fee + evidence count, receipts for both figures."
  []
  (obs/observation
   {:obs/id "obs:USA-NY:2026-09-01"
    :obs/jurisdiction "USA-NY"
    :obs/subject (ny-subject)
    :obs/window {:from "2026-08-01" :to "2026-08-31"}
    :obs/events [(ny-fee-schedule-event)]
    :obs/receipts [(ny-dos-receipt)]
    :obs/figures [(ny-licence-fee-figure) (ny-evidence-count-figure)]
    :obs/missingness #{}
    :obs/recorded-at "2026-09-01"}))

(defn- jpn-observation []
  (obs/observation
   {:obs/id "obs:JPN:2026-09-01"
    :obs/jurisdiction "JPN"
    :obs/subject (jpn-subject)
    :obs/window {:from "2026-08-01" :to "2026-08-31"}
    :obs/events [(jpn-requirement-event)]
    :obs/receipts [(mlit-fee-receipt)]
    :obs/figures [(jpn-fee-figure)]
    :obs/missingness #{}
    :obs/recorded-at "2026-09-01"}))

;; --- 1. source receipt -------------------------------------------------------

(deftest receipt-freezes-a-source-reading
  (let [r (ny-dos-receipt)]
    (is (= "receipt:9d8a562b4d2ab991:2026-09-01" (:receipt/id r)))
    (is (= "fee-observation/1" (:receipt/contract-version r)))
    (is (= :official-regulator (:receipt/source-class r)))
    (is (map? r))))

(deftest receipt-refuses-rumors-and-impossible-readings
  (testing "no content-hash is a rumor"
    (is (= :receipt/bad-content-hash
           (refusal-of #(obs/receipt
                         (assoc (ny-dos-receipt)
                                :receipt/content-hash "deadbeef"))))))
  (testing "http is not https"
    (is (= :receipt/url-not-https
           (refusal-of #(obs/receipt
                         (assoc (ny-dos-receipt)
                                :receipt/source-url
                                "http://dos.ny.gov/professional-licensing"))))))
  (testing "unknown source class is refused, not guessed"
    (is (= :receipt/unknown-source-class
           (refusal-of #(obs/receipt
                         (assoc (ny-dos-receipt)
                                :receipt/source-class :news-report))))))
  (testing "a reading before the source's own assertion is impossible"
    (is (= :receipt/observed-before-asserted
           (refusal-of #(obs/receipt (assoc (ny-dos-receipt)
                                            :receipt/observed-at "2026-08-01"
                                            :receipt/id "receipt:9d8a562b4d2ab991:2026-08-01")))))))

(deftest receipt-edited-after-freezing-is-refused-not-rebranded
  (let [r (ny-dos-receipt)
        edited (assoc r :receipt/content-hash dluhc-hash)]
    (is (= :receipt/stale-id (refusal-of #(obs/receipt edited))))))

;; --- 2+4. typed subject, figures, bases --------------------------------------

(deftest observation-freezes-verbatim-figures-with-their-bases
  (let [o (ny-observation)]
    (is (= "fee-observation/1" (:obs/contract-version o)))
    (is (= 1 (count (:obs/receipts o))))
    (is (= #{:disclosed-fee :requirement-count} (set (map :figure/kind (:obs/figures o)))))
    (is (= "550" (re-find #"550" (:figure/raw (ny-licence-fee-figure)))))
    (is (= "USD" (:figure/currency (ny-licence-fee-figure))))
    (is (= "2026-08-30" (:figure/nominal-at (ny-licence-fee-figure))))
    (is (= 4 (:figure/value (ny-evidence-count-figure))))))

(deftest monetary-figures-need-currency-and-their-own-date
  (is (= :figure/monetary-without-currency
         (refusal-of #(obs/figure (dissoc (ny-licence-fee-figure) :figure/currency)))))
  (is (= :figure/monetary-without-currency
         (refusal-of #(obs/figure (assoc (ny-licence-fee-figure) :figure/currency "usD")))))
  (is (= :figure/monetary-without-nominal-at
         (refusal-of #(obs/figure (dissoc (ny-licence-fee-figure) :figure/nominal-at))))))

(deftest requirement-counts-are-non-negative-integers
  (is (= :figure/bad-value
         (refusal-of #(obs/figure (assoc (ny-evidence-count-figure) :figure/value -1)))))
  (is (= :figure/bad-value
         (refusal-of #(obs/figure (assoc (ny-evidence-count-figure) :figure/value 2.5))))))

;; --- privacy boundaries (refused BY CONSTRUCTION) ----------------------------

(deftest street-addresses-cannot-enter-an-observation
  (is (= :subject/address-refused
         (refusal-of #(obs/subject (assoc (ny-subject)
                                          :subject/address
                                          "1-1-1 Chiyoda [synthetic example]"))))))

(deftest party-data-cannot-enter-an-observation
  (is (= :event/party-data-refused
         (refusal-of #(obs/observation
                       (assoc (ny-observation)
                              :obs/events [(assoc (ny-fee-schedule-event)
                                                  :event/parties
                                                  ["landlord A" "tenant B"])]))))))

;; --- entity separation -------------------------------------------------------

(deftest events-from-another-subject-are-refused
  (is (= :observation/cross-subject-event
         (refusal-of #(obs/observation
                       (assoc (ny-observation)
                              :obs/events [(assoc (ny-fee-schedule-event)
                                                  :event/subject-id
                                                  "JPN")]))))))

(deftest receipts-from-another-jurisdiction-are-refused
  (is (= :observation/cross-jurisdiction-receipt
         (refusal-of #(obs/observation
                       (assoc (jpn-observation)
                              :obs/receipts [(ny-dos-receipt)]))))))

(deftest cross-subject-refresh-is-refused-at-append-time
  (let [history (obs/observe [] (ny-observation))]
    (is (= :observation/refresh-of-cross-subject
           (refusal-of #(obs/refresh history
                                     "obs:USA-NY:2026-09-01"
                                     (jpn-observation)))))))

;; --- 3. windows --------------------------------------------------------------

(deftest windows-are-validated
  (is (= :observation/window-inverted
         (refusal-of #(obs/observation
                       (assoc (ny-observation)
                              :obs/window {:from "2026-08-31" :to "2026-08-01"})))))
  (is (= :observation/bad-window
         (refusal-of #(obs/observation
                       (assoc (ny-observation) :obs/window {:from "2026-08"})))))
  (testing "a published change asserted outside the window is refused"
    (is (= :observation/event-outside-window
           (refusal-of #(obs/observation
                         (assoc (ny-observation)
                                :obs/events [(assoc (ny-fee-schedule-event)
                                                    :event/asserted-at
                                                    "2026-09-15")])))))))

(deftest only-publication-acts-are-observable-events
  (testing "a rent change is a market act, not a publication act — refused"
    (is (= :observation/unknown-event-type
           (refusal-of #(obs/observation
                         (assoc (ny-observation)
                                :obs/events [(assoc (ny-fee-schedule-event)
                                                    :event/type
                                                    :rent-increased)])))))))

;; --- 6. missingness / coverage honesty ---------------------------------------

(deftest missingness-flags-are-closed-vocabulary
  (is (= :observation/unknown-missingness-flag
         (refusal-of #(obs/observation
                       (assoc (ny-observation)
                              :obs/missingness #{:market-hotness}))))))

(deftest a-fee-schedule-republication-without-a-fee-figure-must-declare-the-gap
  (is (= :observation/silence-claims-completeness
         (refusal-of #(obs/observation
                       (-> (ny-observation)
                           (assoc :obs/figures [(ny-evidence-count-figure)])))))))

(deftest a-jurisdiction-without-spec-basis-must-carry-the-gap-flag
  (let [base (ny-observation)
        atl (obs/observation
             (assoc base
                    :obs/id "obs:ATL:2026-09-01"
                    :obs/jurisdiction "ATL"
                    :obs/receipts [(obs/receipt
                                    (assoc (ny-dos-receipt)
                                           :receipt/jurisdiction "ATL"
                                           :receipt/issuing-entity
                                           "synthetic test regulator"))]
                    :obs/figures [(ny-evidence-count-figure)]
                    :obs/missingness #{:jurisdiction-spec-basis-absent
                                       :fee-schedule-unavailable}))]
    (is (some? atl))
    (is (= :observation/silence-claims-completeness
           (refusal-of #(obs/observation
                         (assoc atl :obs/missingness #{})))))))

;; --- 8. refresh history + verbatim delta --------------------------------------

(deftest duplicate-observation-ids-are-refused
  (let [history (obs/observe [] (ny-observation))]
    (is (= :history/duplicate-observation-id
           (refusal-of #(obs/observe history (ny-observation)))))))

(deftest not-an-observation-is-refused
  (is (= :history/not-an-observation
         (refusal-of #(obs/observe [] {:obs/id "junk"})))))

(deftest a-subject-cannot-be-re-scoped
  (let [history (obs/observe [] (ny-observation))
        re-scoped (obs/observation
                   (assoc (ny-observation)
                          :obs/id "obs:USA-NY:2026-09-02"
                          :obs/recorded-at "2026-09-02"
                          :obs/subject (assoc (ny-subject)
                                              :subject/scope :national)))]
    (is (= :history/subject-scope-conflict
           (refusal-of #(obs/observe history re-scoped))))))

(defn- ny-observation-2
  "Second generation: same subject, wider window, a trust-account amendment
  added, new receipt for the re-read."
  []
  (obs/observation
   {:obs/id "obs:USA-NY:2026-09-02"
    :obs/jurisdiction "USA-NY"
    :obs/subject (ny-subject)
    :obs/window {:from "2026-08-01" :to "2026-09-02"}
    :obs/events [(ny-fee-schedule-event)
                 {:event/type :trust-account-rule-amended
                  :event/publication-ref "NY-DOS-SYNTH-2026-093"
                  :event/asserted-at "2026-09-01"
                  :event/publisher "NY Department of State — Division of Licensing Services"
                  :event/subject-id "USA-NY"}]
    :obs/receipts [(ny-dos-receipt)]
    :obs/figures [(ny-licence-fee-figure) (ny-evidence-count-figure)]
    :obs/missingness #{}
    :obs/recorded-at "2026-09-02"}))

(deftest temporal-refresh-links-and-deltas
  (let [h (-> [] (obs/observe (ny-observation)) (obs/refresh "obs:USA-NY:2026-09-01" (ny-observation-2)))
        g2 (last h)
        d (obs/refresh-delta (first h) g2)]
    (is (= "obs:USA-NY:2026-09-01" (:obs/refresh-of g2)))
    (is (= :changed (:delta/kind d)))
    (is (= 1 (count (:delta/added-events d))))
    (is (= :trust-account-rule-amended (:event/type (first (:delta/added-events d)))))
    (is (= 0 (count (:delta/changed-figures d))))
    (is (= ["receipt:9d8a562b4d2ab991:2026-09-01"]
           (:delta/prior-receipts d)))
    (is (= (:delta/prior-receipts d) (:delta/next-receipts d)))))

(deftest changed-figures-are-carried-in-full-both-sides-no-difference-computed
  (let [h1 (obs/observe [] (ny-observation))
        variant (obs/observation
                 (assoc (ny-observation)
                        :obs/id "obs:USA-NY:2026-09-02"
                        :obs/recorded-at "2026-09-02"
                        :obs/figures [(obs/figure
                                       (assoc (ny-licence-fee-figure)
                                              :figure/amount 650))]))
        d (obs/refresh-delta (first h1) variant)
        cf (first (:delta/changed-figures d))]
    (is (= 1 (count (:delta/changed-figures d))))
    (is (= 550 (get-in cf [:delta/prior :figure/amount])))
    (is (= 650 (get-in cf [:delta/next :figure/amount])))
    (testing "no numeric difference key exists anywhere in the delta"
      (is (not (contains? d :delta/amount-difference)))
      (is (nil? (some #(re-find #"difference|total|sum|average"
                                (name (key %)))
                      d))))
    (is (string? (:delta/comparability-note d)))))

(deftest unchanged-refresh-deltas-say-unchanged
  (let [h (-> []
              (obs/observe (ny-observation))
              (obs/refresh "obs:USA-NY:2026-09-01"
                           (ny-observation-2)))
        g3 (obs/observation
            (assoc (ny-observation-2)
                   :obs/id "obs:USA-NY:2026-09-03"
                   :obs/recorded-at "2026-09-03"))
        h2 (obs/refresh h "obs:USA-NY:2026-09-02" g3)
        d (obs/refresh-delta (second h2) (last h2))]
    (is (= :unchanged (:delta/kind d)))
    (is (empty? (:delta/added-events d)))
    (is (empty? (:delta/gap-added d)))))

(deftest delta-refuses-cross-subject-comparison
  (is (= :delta/cross-subject
         (refusal-of #(obs/refresh-delta (ny-observation) (jpn-observation))))))

;; --- 7. derived observations (counts only) -------------------------------------

(deftest window-observation-is-counts-only
  (let [w (obs/window-observation (ny-observation))]
    (is (= {:fee-schedule-republished 1} (:derived/event-counts w)))
    (is (= {:disclosed-fee 1 :requirement-count 1} (:derived/figure-counts w)))
    (is (= ["NY-DOS-SYNTH-2026-081"] (:derived/publication-refs w)))
    (is (true? (:derived/no-model w)))
    (testing "no amounts, no fees, no valuations in the derived shape"
      (is (nil? (some #(or (re-find #"amount" (name %))
                           (re-find #"fee" (name %))
                           (re-find #"value" (name %)))
                      (keys w)))))))

(deftest coverage-observation-counts-the-catalog-honestly
  (let [c (obs/coverage-observation "2026-09-03")]
    (is (= (count facts/catalog) (:coverage/jurisdictions-with-spec-basis c)))
    (is (pos? (:coverage/jurisdictions-with-spec-basis c)))
    (is (= (:covered (facts/coverage))
           (:coverage/jurisdictions-with-spec-basis c)))
    (is (= (reduce + (map #(count (facts/evidence-checklist %)) (keys facts/catalog)))
           (:coverage/evidence-checklist-items c)))
    (is (true? (:coverage/no-model c)))
    (is (string? (:coverage/note c)))))

;; --- 9. hyakka proposal (SHAPE only; nothing transmitted) ----------------------

(deftest hyakka-proposal-carries-claims-bases-and-boundaries
  (let [p (obs/hyakka-proposal (ny-observation))]
    (is (= "fudosan" (:proposal/corpus p)))
    (is (= "network-awai/app-hyakka" (:proposal/target p)))
    (is (true? (:proposal/no-model p)))
    (is (true? (:proposal/props-unregistered p)))
    (is (= 2 (count (:proposal/figure-claims p))))
    (is (= 1 (count (:proposal/subject-claims p))))
    (is (pos? (count (:proposal/epistemic-boundaries p))))
    (is (pos? (count (:proposal/privacy-boundaries p))))
    (is (= [] (:proposal/gaps p)))
    (let [pc (first (filter #(= "fudosan.prop/disclosed-fee-observation"
                                (:claim/prop %))
                            (:proposal/figure-claims p)))]
      (is (= 550 (:claim/amount pc)))
      (is (= "USD" (:claim/currency pc)))
      (is (= "2026-08-30" (:claim/nominal-at pc)))
      (is (string? (:claim/value-verbatim pc)))
      (is (string? (:claim/receipt-id pc))))
    (is (= [] (:proposal/source-class-unmapped p)))))

(deftest hyakka-proposal-flags-unmapped-receipt-classes
  (let [portal (obs/observation
                (assoc (ny-observation)
                       :obs/id "obs:USA-NY:2026-09-04"
                       :obs/recorded-at "2026-09-04"
                       :obs/missingness #{:fee-schedule-unavailable}))
        p (obs/hyakka-proposal portal)]
    (is (some? p))))

(deftest hyakka-proposal-flags-unmapped-receipt-classes-gbr
  (let [gbr (obs/observation
             (-> (jpn-observation)
                 (assoc :obs/id "obs:GBR:2026-09-04"
                        :obs/jurisdiction "GBR"
                        :obs/subject (gbr-subject)
                        :obs/receipts [(dluhc-receipt)]
                        :obs/figures []
                        :obs/missingness #{:fee-schedule-unavailable})
                 (assoc :obs/events [])))
        p (obs/hyakka-proposal gbr)]
    (is (= ["official-government-portal"] (:proposal/source-class-unmapped p)))))

;; --- 10. query / readback ------------------------------------------------------

(deftest readback-returns-the-latest-at-or-before-as-of
  (let [h (-> []
              (obs/observe (ny-observation))
              (obs/refresh "obs:USA-NY:2026-09-01"
                           (ny-observation-2)))]
    (is (= "obs:USA-NY:2026-09-02"
           (:obs/id (obs/readback h "USA-NY" "2026-09-02"))))
    (is (= "obs:USA-NY:2026-09-01"
           (:obs/id (obs/readback h "USA-NY" "2026-09-01"))))))

(deftest readback-miss-is-a-miss
  (let [h (obs/observe [] (ny-observation))]
    (is (nil? (obs/readback h "JPN" "2026-09-02")))
    (is (nil? (obs/readback h "USA-NY" "2026-08-31")))))

(deftest readback-refuses-tampered-receipts
  (let [h (obs/observe [] (ny-observation))
        tampered (update-in h [0 :obs/receipts 0] assoc
                            :receipt/content-hash dluhc-hash)]
    (is (= :receipt/stale-id
           (refusal-of #(obs/readback tampered "USA-NY" "2026-09-02"))))
    (is (= :readback/tampered-receipt
           (refusal-of #(obs/readback (update-in h [0 :obs/receipts 0] dissoc
                                                  :receipt/contract-version)
                                       "USA-NY" "2026-09-02"))))))

(deftest readback-chain-walks-oldest-first-with-aligned-deltas
  (let [h (-> []
              (obs/observe (ny-observation))
              (obs/refresh "obs:USA-NY:2026-09-01"
                           (ny-observation-2)))
        {:keys [chain deltas]} (obs/readback-chain h "USA-NY")]
    (is (= 2 (count chain)))
    (is (= ["obs:USA-NY:2026-09-01"
            "obs:USA-NY:2026-09-02"]
           (mapv :obs/id chain)))
    (is (= 1 (count deltas)))
    (is (= :changed (:delta/kind (first deltas))))
    (is (= (:obs/id (second chain)) (:delta/next-id (first deltas))))))

(deftest readback-chain-refuses-broken-cyclic-and-cross-subject-lineage
  (let [broken (obs/observation
                (assoc (ny-observation-2) :obs/refresh-of "obs:ghost"))
        hb (obs/observe [] broken)
        ;; true cycle: a refreshes b, b refreshes a
        a (obs/observation
           (assoc (ny-observation)
                  :obs/refresh-of "obs:USA-NY:2026-09-02"))
        b (obs/observation
           (assoc (ny-observation-2) :obs/refresh-of
                  "obs:USA-NY:2026-09-01"))
        hc (-> [] (obs/observe a) (obs/observe b))
        ;; cross-subject chain element: the JPN generation claims to refresh
        ;; the USA-NY generation
        cross (obs/observation
               (assoc (jpn-observation)
                      :obs/id "obs:JPN:2026-09-03"
                      :obs/recorded-at "2026-09-03"
                      :obs/refresh-of "obs:USA-NY:2026-09-01"))
        hx (-> [] (obs/observe (ny-observation)) (obs/observe cross))]
    (is (= :readback/broken-lineage
           (refusal-of #(obs/readback-chain hb "USA-NY"))))
    (is (= :readback/cyclic-lineage
           (refusal-of #(obs/readback-chain hc "USA-NY"))))
    (is (= :readback/chain-cross-subject
           (refusal-of #(obs/readback-chain hx "JPN"))))))

;; --- 5. determinism ------------------------------------------------------------

(deftest the-contract-is-deterministic
  (let [o1 (ny-observation) o2 (ny-observation)]
    (is (= o1 o2))
    (is (= (obs/window-observation o1) (obs/window-observation o2)))
    (is (= (obs/hyakka-proposal o1) (obs/hyakka-proposal o2)))
    (is (= (obs/coverage-observation "2026-09-03")
           (obs/coverage-observation "2026-09-03")))))
