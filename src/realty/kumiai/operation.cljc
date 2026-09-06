(ns realty.kumiai.operation
  "OperationActor for the condominium-association actor -- one
  association operation = one supervised actor run, expressed as a
  langgraph-clj StateGraph. Same shape as `realty.operation`: the
  advisor (Kumiai-LLM) is sealed into `:advise`, its proposal is ALWAYS
  routed through the Condominium-Association Governor (`:govern`) and
  the rollout phase gate (`:decide`) before anything commits.

  Everything is injected, so each is a swap rather than a rewrite:
    - the Store    (MemStore | DatomicStore)  -- `store` arg
    - the Advisor  (mock | real LLM)          -- :advisor opt
    - the Phase    (0->3)                     -- :phase in ctx

  `interrupt-before #{:request-approval}` hands the decision to a human
  officer of the association. `:works/commission` ALWAYS reaches that
  node when the governor is clean -- see `realty.kumiai.phase`."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [realty.kumiai.kumiaillm :as kumiaillm]
            [realty.kumiai.governor :as governor]
            [realty.kumiai.phase :as phase]
            [realty.kumiai.store :as store]))

(defn- commit-fact [request context proposal]
  {:t           :committed
   :op          (:op request)
   :actor       (:actor-id context)
   :subject     (:subject request)
   :disposition :commit
   :basis       (:cites proposal)
   :summary     (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(or (:association-id request) (:subject request))]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any
  `realty.kumiai.store/Store`)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (kumiaillm/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (kumiaillm/-advise advisor store request)]
            {:proposal p :audit [(kumiaillm/trace request p)]})))

      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:subject request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :actuation
                                          (seq (:advisories verdict)) :advisory
                                          :else :low-confidence))
                        :advisories (mapv :advisory (:advisories verdict))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal) :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal record]}]
          (store/commit-record! store record)
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
