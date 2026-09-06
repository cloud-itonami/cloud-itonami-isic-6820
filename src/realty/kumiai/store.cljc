(ns realty.kumiai.store
  "SSoT for the condominium-association actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  `realty.store` uses, with the same two implementations
  (`MemStore` and a `langchain.db` `DatomicStore`) proved against the
  same contract test.

  Entity shape, and why it is shaped this way:

    association -- the 管理組合. Carries its funding parameters
                   (contribution rate or staged schedule, opening
                   balance, transfers, floor areas, parking) because
                   those ARE the association's own facts, not the
                   plan's.
    plan        -- the 長期修繕計画. One per association at a time.
    resolution  -- one general-meeting resolution, with the tally it
                   was judged on. Append-only: a resolution is not
                   edited, a later meeting supersedes it.
    pending-works -- carried INLINE on the association, exactly like
                   `realty.store`'s `:pending-contract`: there is one
                   works package awaiting commissioning at a time, so a
                   separate collection would be an unused indirection.
                   Commissioning CLEARS it, which doubles as the
                   double-commissioning guard -- a second attempt falls
                   into the same 'no works pending' check a never-had-
                   one association would trigger.

  The ledger stays append-only on every backend: 'which works were
  commissioned, against which resolution, counted against which
  statutory denominator, approved by whom' is a query over an immutable
  log. For an association spending other owners' reserve contributions
  that log IS the accountability."
  (:require [realty.kumiai.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (association [s id])
  (all-associations [s])
  (plan-of [s association-id] "the association's long-term repair plan, or nil")
  (resolution [s id])
  (assessment-of [s association-id] "committed plan/jurisdiction assessment, or nil")
  (projection-of [s association-id] "committed reserve projection, or nil")
  (ledger [s])
  (resolution-history [s])
  (works-history [s])
  (resolution-sequence [s jurisdiction])
  (works-sequence [s jurisdiction])
  (works-already-commissioned? [s works-id])
  (commit-record! [s record])
  (append-ledger! [s fact])
  (with-associations [s associations])
  (with-plans [s plans]))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained association set. `association-1` is the
  worked case: a real-shaped 70-unit block whose plan is fully funded
  at zero escalation and is NOT funded once construction costs rise --
  which is the whole question this extension exists to answer."
  []
  {:associations
   {"association-1"
    {:id "association-1" :name "さくら台マンション管理組合"
     :jurisdiction "JPN" :status :under-management
     :units 70 :total-exclusive-area 4900 :gross-floor-area 7000 :floors 12
     :opening-balance 70000000
     :monthly-per-m2 150
     :other-transfers-annual 3000000
     :mechanical-parking {:type "3段(ピット2段)昇降式" :spaces 30}
     :membership {:owners 70 :voting-rights 4900}
     :pending-works nil}

    "association-2"
    {:id "association-2" :name "Atlantis Towers Owners Association"
     :jurisdiction "ATL" :status :under-management
     :units 40 :total-exclusive-area 3000 :gross-floor-area 4200 :floors 8
     :opening-balance 20000000 :monthly-per-m2 200 :other-transfers-annual 0
     :membership {:owners 40 :voting-rights 3000}
     :pending-works nil}

    "association-3"
    {:id "association-3" :name "みなと第一マンション管理組合"
     :jurisdiction "JPN" :status :intake
     :units 48 :total-exclusive-area 3400 :gross-floor-area 4800 :floors 9
     :opening-balance 12000000 :monthly-per-m2 90 :other-transfers-annual 0
     :membership {:owners 48 :voting-rights 3400}
     :pending-works nil}

    "association-4"
    {:id "association-4" :name "Harborview Condominium Board"
     :jurisdiction "USA-NY" :status :under-management
     :units 55 :total-exclusive-area 5200 :gross-floor-area 7400 :floors 11
     :opening-balance 40000000 :monthly-per-m2 180 :other-transfers-annual 0
     :membership {:owners 55 :voting-rights 5200}
     :pending-works nil}

    "association-5"
    {:id "association-5" :name "北谷ハイツ管理組合"
     :jurisdiction "JPN" :status :under-management
     :units 32 :total-exclusive-area 2200 :gross-floor-area 3100 :floors 6
     :opening-balance 18000000 :monthly-per-m2 130 :other-transfers-annual 0
     :membership {:owners 32 :voting-rights 2200}
     :pending-works nil}

    ;; The reserve projection is jurisdiction-agnostic -- escalation and
    ;; slippage do not care which statute the building sits under. Only
    ;; the BENCHMARK is Japanese, so `assess-against-benchmark` returns
    ;; nil for these three and the governor does not pretend otherwise.
    ;; `:membership` carries whichever axes that jurisdiction counts.
    "association-6"
    {:id "association-6" :name "WEG Lindenhof 12"
     :jurisdiction "DEU" :status :under-management
     :units 48 :total-exclusive-area 3600 :gross-floor-area 5100 :floors 7
     :opening-balance 240000 :monthly-per-m2 2.1 :other-transfers-annual 0
     :membership {:owners 48 :co-ownership-shares 1000}
     :pending-works nil}

    "association-7"
    {:id "association-7" :name "Comunidad de Propietarios Ronda del Mar 8"
     :jurisdiction "ESP" :status :under-management
     :units 36 :total-exclusive-area 2900 :gross-floor-area 4100 :floors 6
     :opening-balance 90000 :monthly-per-m2 1.4 :other-transfers-annual 0
     :membership {:owners 36 :voting-rights 100}
     :pending-works nil}

    "association-8"
    {:id "association-8" :name "Syndicat des copropriétaires 14 rue Lafitte"
     :jurisdiction "FRA" :status :under-management
     :units 60 :total-exclusive-area 4200 :gross-floor-area 5900 :floors 8
     :opening-balance 310000 :monthly-per-m2 1.8 :other-transfers-annual 0
     :membership {:owners 60 :voting-rights 10000}
     :pending-works nil}}

   :plans
   {"association-1"
    ;; Tuned so the SAME plan is funded at 0% escalation and short at
    ;; 5% -- and so that a two-year slip pushes `w5` (year 28) past the
    ;; thirty-year horizon, which makes the projected deficit SMALLER
    ;; than the undelayed case. That is the trap `reserve/shortfall`'s
    ;; `:unmeasured-outflow` and the governor's `:unmeasured-outflow`
    ;; check exist for: the plan did not get cheaper, it stopped being
    ;; measured.
    {:id "plan-1" :association-id "association-1" :horizon-years 30 :new-build? false
     :works [{:id "w1" :label "第1回大規模修繕 (外壁・防水・鉄部)" :year 6  :base-cost 110000000 :major-repair? true}
             {:id "w2" :label "給水管更生"                        :year 11 :base-cost 30000000}
             {:id "w3" :label "第2回大規模修繕"                    :year 18 :base-cost 125000000 :major-repair? true}
             {:id "w4" :label "エレベーター更新"                   :year 22 :base-cost 30000000}
             {:id "w5" :label "第3回大規模修繕"                    :year 28 :base-cost 60000000 :major-repair? true}]}
    "association-3"
    {:id "plan-3" :association-id "association-3" :horizon-years 15 :new-build? false
     :works [{:id "p3w1" :label "第1回大規模修繕" :year 3 :base-cost 90000000 :major-repair? true}]}
    ;; Under management, but the plan is shorter than the guideline's own
    ;; sample horizon and carries one major-repair cycle instead of two.
    ;; It cannot be compared with the published benchmark band at all.
    "association-5"
    {:id "plan-5" :association-id "association-5" :horizon-years 15 :new-build? false
     :works [{:id "p5w1" :label "第1回大規模修繕" :year 5 :base-cost 70000000 :major-repair? true}]}
    "association-4"
    {:id "plan-4" :association-id "association-4" :horizon-years 30 :new-build? false
     :works [{:id "p4w1" :label "Facade and roof -- cycle 1" :year 5  :base-cost 150000000 :major-repair? true}
             {:id "p4w2" :label "Facade and roof -- cycle 2" :year 20 :base-cost 190000000 :major-repair? true}]}
    "association-6"
    {:id "plan-6" :association-id "association-6" :horizon-years 25 :new-build? false
     :works [{:id "p6w1" :label "Fassade und Dach -- Erhaltung" :year 6  :base-cost 900000 :major-repair? true}
             {:id "p6w2" :label "Heizungsanlage" :year 14 :base-cost 420000}
             {:id "p6w3" :label "Fassade -- zweiter Zyklus" :year 22 :base-cost 1000000 :major-repair? true}]}
    "association-7"
    {:id "plan-7" :association-id "association-7" :horizon-years 20 :new-build? false
     :works [{:id "p7w1" :label "Rehabilitación de fachada" :year 5 :base-cost 480000 :major-repair? true}
             {:id "p7w2" :label "Sustitución del ascensor" :year 13 :base-cost 260000}]}
    "association-8"
    {:id "plan-8" :association-id "association-8" :horizon-years 25 :new-build? false
     :works [{:id "p8w1" :label "Ravalement de façade" :year 7 :base-cost 1100000 :major-repair? true}
             {:id "p8w2" :label "Réfection de la toiture" :year 16 :base-cost 700000 :major-repair? true}]}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- commission-works!
  "Backend-agnostic `:works/mark-commissioned`. Looks up the
  association, its pending works package and the resolution that
  authorised it, and drafts the works-order record. Returns
  {:result .. :association-patch ..}; the patch CLEARS `:pending-works`
  -- see ns docstring for why that doubles as the double-commissioning
  guard rather than needing a separate check."
  [s association-id]
  (let [a (association s association-id)
        pw (:pending-works a)
        r (resolution s (:resolution-id pw))
        seq-n (works-sequence s (:jurisdiction a))
        result (registry/register-works-order
                association-id (:id pw) (:contractor pw) (:contract-value pw)
                (:jurisdiction a) (:resolution-number r) seq-n)]
    {:result result
     :association-patch {:pending-works nil
                         :works-number (get result "works_number")}}))

;; ----------------------------- MemStore -----------------------------

(defrecord MemStore [a]
  Store
  (association [_ id] (get-in @a [:associations id]))
  (all-associations [_] (sort-by :id (vals (:associations @a))))
  (plan-of [_ association-id] (get-in @a [:plans association-id]))
  (resolution [_ id] (get-in @a [:resolutions id]))
  (assessment-of [_ association-id] (get-in @a [:assessments association-id]))
  (projection-of [_ association-id] (get-in @a [:projections association-id]))
  (ledger [_] (:ledger @a))
  (resolution-history [_] (:resolution-records @a))
  (works-history [_] (:works-records @a))
  (resolution-sequence [_ j] (get-in @a [:resolution-sequences j] 0))
  (works-sequence [_ j] (get-in @a [:works-sequences j] 0))
  (works-already-commissioned? [_ works-id]
    (boolean (some #(= works-id (get % "works_id")) (:works-records @a))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :association/upsert
      (swap! a update-in [:associations (:id value)] merge value)

      :plan/upsert
      (swap! a assoc-in [:plans (:association-id payload)] payload)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :projection/set
      (swap! a assoc-in [:projections (first path)] payload)

      :resolution/filed
      (let [j (:jurisdiction (association s (:association-id payload)))
            seq-n (resolution-sequence s j)
            result (registry/register-resolution
                    (:association-id payload) (:id payload) (:kind payload)
                    (:budget-amount payload) j (:verdict payload) seq-n)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:resolution-sequences j] (fnil inc 0))
                       (assoc-in [:resolutions (:id payload)]
                                 (assoc payload :resolution-number (get result "resolution_number")
                                        :status (if (:passed? (:verdict payload)) :passed :failed)))
                       (update :resolution-records registry/append result))))
        result)

      :works/mark-commissioned
      (let [association-id (first path)
            {:keys [result association-patch]} (commission-works! s association-id)
            j (:jurisdiction (association s association-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:works-sequences j] (fnil inc 0))
                       (update-in [:associations association-id] merge association-patch)
                       (update :works-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-associations [s m] (when (seq m) (swap! a assoc :associations m)) s)
  (with-plans [s m] (when (seq m) (swap! a assoc :plans m)) s))

(defn seed-db
  "A MemStore seeded with the demo association set."
  []
  (->MemStore (atom (merge (demo-data)
                           {:assessments {} :projections {} :resolutions {}
                            :ledger [] :resolution-records [] :works-records []
                            :resolution-sequences {} :works-sequences {}}))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  {:association/id            {:db/unique :db.unique/identity}
   :plan/association-id       {:db/unique :db.unique/identity}
   :resolution/id             {:db/unique :db.unique/identity}
   :assessment/association-id {:db/unique :db.unique/identity}
   :projection/association-id {:db/unique :db.unique/identity}
   :ledger/seq                {:db/unique :db.unique/identity}
   :resrec/seq                {:db/unique :db.unique/identity}
   :workrec/seq               {:db/unique :db.unique/identity}
   :resseq/jurisdiction       {:db/unique :db.unique/identity}
   :workseq/jurisdiction      {:db/unique :db.unique/identity}})

(defn- association->tx
  "`:pending-works` is written whenever the PATCH mentions the key at
  all (`contains?`, not truthiness) -- `nil` is a legitimate value
  ('nothing pending'), so a truthiness test would skip a genuine
  clear-to-nil. But it must not be written on a partial patch that
  never mentions it, which would clobber a real pending package."
  [{:keys [id name jurisdiction status units total-exclusive-area gross-floor-area floors
           opening-balance monthly-per-m2 schedule other-transfers-annual mechanical-parking
           membership bylaw-overrides works-number] :as m}]
  (cond-> {:association/id id}
    name                            (assoc :association/name name)
    jurisdiction                    (assoc :association/jurisdiction jurisdiction)
    status                          (assoc :association/status status)
    units                           (assoc :association/units units)
    total-exclusive-area            (assoc :association/total-exclusive-area total-exclusive-area)
    gross-floor-area                (assoc :association/gross-floor-area gross-floor-area)
    floors                          (assoc :association/floors floors)
    opening-balance                 (assoc :association/opening-balance opening-balance)
    monthly-per-m2                  (assoc :association/monthly-per-m2 monthly-per-m2)
    schedule                        (assoc :association/schedule (ls/enc schedule))
    other-transfers-annual          (assoc :association/other-transfers-annual other-transfers-annual)
    mechanical-parking              (assoc :association/mechanical-parking (ls/enc mechanical-parking))
    membership                      (assoc :association/membership (ls/enc membership))
    bylaw-overrides                 (assoc :association/bylaw-overrides (ls/enc bylaw-overrides))
    works-number                    (assoc :association/works-number works-number)
    (contains? m :pending-works)    (assoc :association/pending-works (ls/enc (:pending-works m)))))

(def ^:private association-pull
  [:association/id :association/name :association/jurisdiction :association/status
   :association/units :association/total-exclusive-area :association/gross-floor-area
   :association/floors :association/opening-balance :association/monthly-per-m2
   :association/schedule :association/other-transfers-annual :association/mechanical-parking
   :association/membership :association/bylaw-overrides :association/pending-works
   :association/works-number])

(defn- pull->association [m]
  (when (:association/id m)
    {:id (:association/id m) :name (:association/name m)
     :jurisdiction (:association/jurisdiction m) :status (:association/status m)
     :units (:association/units m)
     :total-exclusive-area (:association/total-exclusive-area m)
     :gross-floor-area (:association/gross-floor-area m) :floors (:association/floors m)
     :opening-balance (:association/opening-balance m)
     :monthly-per-m2 (:association/monthly-per-m2 m)
     :schedule (ls/dec* (:association/schedule m))
     :other-transfers-annual (:association/other-transfers-annual m)
     :mechanical-parking (ls/dec* (:association/mechanical-parking m))
     :membership (ls/dec* (:association/membership m))
     :bylaw-overrides (ls/dec* (:association/bylaw-overrides m))
     :pending-works (ls/dec* (:association/pending-works m))
     :works-number (:association/works-number m)}))

(defrecord DatomicStore [conn]
  Store
  (association [_ id]
    (pull->association (d/pull (d/db conn) association-pull [:association/id id])))
  (all-associations [_]
    (->> (d/q '[:find [?id ...] :where [?e :association/id ?id]] (d/db conn))
         (map #(pull->association (d/pull (d/db conn) association-pull [:association/id %])))
         (sort-by :id)))
  (plan-of [_ association-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?a
                   :where [?e :plan/association-id ?a] [?e :plan/payload ?p]]
                 (d/db conn) association-id)))
  (resolution [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?i
                   :where [?e :resolution/id ?i] [?e :resolution/payload ?p]]
                 (d/db conn) id)))
  (assessment-of [_ association-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?a
                   :where [?e :assessment/association-id ?a] [?e :assessment/payload ?p]]
                 (d/db conn) association-id)))
  (projection-of [_ association-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?a
                   :where [?e :projection/association-id ?a] [?e :projection/payload ?p]]
                 (d/db conn) association-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first) (mapv (comp ls/dec* second))))
  (resolution-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :resrec/seq ?s] [?e :resrec/record ?r]] (d/db conn))
         (sort-by first) (mapv (comp ls/dec* second))))
  (works-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :workrec/seq ?s] [?e :workrec/record ?r]] (d/db conn))
         (sort-by first) (mapv (comp ls/dec* second))))
  (resolution-sequence [_ j]
    (or (d/q '[:find ?n . :in $ ?j :where [?e :resseq/jurisdiction ?j] [?e :resseq/next ?n]]
            (d/db conn) j) 0))
  (works-sequence [_ j]
    (or (d/q '[:find ?n . :in $ ?j :where [?e :workseq/jurisdiction ?j] [?e :workseq/next ?n]]
            (d/db conn) j) 0))
  (works-already-commissioned? [s works-id]
    (boolean (some #(= works-id (get % "works_id")) (works-history s))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :association/upsert
      (d/transact! conn [(association->tx value)])

      :plan/upsert
      (d/transact! conn [{:plan/association-id (:association-id payload) :plan/payload (ls/enc payload)}])

      :assessment/set
      (d/transact! conn [{:assessment/association-id (first path) :assessment/payload (ls/enc payload)}])

      :projection/set
      (d/transact! conn [{:projection/association-id (first path) :projection/payload (ls/enc payload)}])

      :resolution/filed
      (let [j (:jurisdiction (association s (:association-id payload)))
            seq-n (resolution-sequence s j)
            result (registry/register-resolution
                    (:association-id payload) (:id payload) (:kind payload)
                    (:budget-amount payload) j (:verdict payload) seq-n)
            stored (assoc payload :resolution-number (get result "resolution_number")
                          :status (if (:passed? (:verdict payload)) :passed :failed))]
        (d/transact! conn
                     [{:resolution/id (:id payload) :resolution/payload (ls/enc stored)}
                      {:resseq/jurisdiction j :resseq/next (inc seq-n)}
                      {:resrec/seq (count (resolution-history s)) :resrec/record (ls/enc (get result "record"))}])
        result)

      :works/mark-commissioned
      (let [association-id (first path)
            {:keys [result association-patch]} (commission-works! s association-id)
            j (:jurisdiction (association s association-id))]
        (d/transact! conn
                     [(association->tx (assoc association-patch :id association-id))
                      {:workseq/jurisdiction j :workseq/next (inc (works-sequence s j))}
                      {:workrec/seq (count (works-history s)) :workrec/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
    fact)
  (with-associations [s m]
    (when (seq m) (d/transact! conn (mapv association->tx (vals m)))) s)
  (with-plans [s m]
    (when (seq m)
      (d/transact! conn (mapv (fn [[aid p]] {:plan/association-id aid :plan/payload (ls/enc p)}) m)))
    s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [associations plans]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-plans (with-associations s associations) plans))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo set -- the Datomic-backed analog
  of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
