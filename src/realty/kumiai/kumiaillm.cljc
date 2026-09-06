(ns realty.kumiai.kumiaillm
  "Kumiai-LLM client -- the *contained intelligence node* for the
  condominium-association actor.

  It normalizes association intake, drafts a per-jurisdiction plan/
  evidence checklist, drafts a reserve-shortfall projection, drafts a
  general-meeting resolution tally, and drafts the major-repair works
  order. It is a smart-but-untrusted advisor: it returns a PROPOSAL,
  never a committed record and never a real contract. Everything is
  censored by `realty.kumiai.governor` first, and
  `:works/commission` never auto-commits at any phase.

  Deterministic mock, so the actor graph and the governor contract run
  offline. Two request flags inject the two failure modes this actor
  exists to catch, exactly as `realty.realtyfeellm`'s `:no-spec?` does:

    :count-total?  -- tally an ORDINARY resolution against the total
                      membership, the pre-2026-04-01 denominator. The
                      arithmetic is impeccable and the answer is wrong.
    :claim-deficit -- assert a reserve deficit the projection does not
                      support.

  Both produce confident, well-formed, plausible proposals. That is the
  point: neither is detectable from the proposal itself, only by
  recomputing it."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [realty.kumiai.facts :as facts]
            [realty.kumiai.reserve :as reserve]
            [realty.kumiai.resolution :as resolution]
            [realty.kumiai.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  [_db {:keys [patch]}]
  {:summary    (str "管理組合レコード更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :association/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-plan
  "Per-jurisdiction evidence checklist plus the guideline position of
  the current reserve level. `:no-spec?` injects the fabrication this
  actor must refuse."
  [db {:keys [subject no-spec?]}]
  (let [a (store/association db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis iso3)
        plan (store/plan-of db subject)
        assessed (when (and sb plan) (reserve/assess-against-benchmark iso3 a plan))]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "realty.kumiai.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件"
                        (when assessed
                          (str " / 積立金水準 " (Math/round (:z assessed)) "円/㎡・月 は目安の幅に対し "
                               (name (:position assessed)))))
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)
                    :guideline assessed}
       :stake      nil
       :confidence 0.9})))

(defn- propose-simulation
  "Draft the reserve-shortfall projection under the requested cost
  escalation and schedule slippage. Moves no capital, so low stakes --
  but the number it produces is the number a board will decide on, so
  the governor recomputes it rather than trusting it."
  [db {:keys [subject assumptions claim-deficit]}]
  (let [a (store/association db subject)
        plan (store/plan-of db subject)
        sb (facts/spec-basis (:jurisdiction a))
        proj (when plan (reserve/project a plan assumptions))
        s (when proj (reserve/shortfall proj))
        required (when plan (reserve/required-contribution a plan assumptions))]
    {:summary    (if s
                   (str subject " 修繕積立金投影: 工事費上昇率 "
                        (* 100.0 (double (or (:cost-escalation assumptions) 0))) "% / 工期遅延 "
                        (or (:delay-years assumptions) 0) "年 -> "
                        (if (:funded? s) "充足"
                            (str "不足 " (Math/round (double (or claim-deficit (:deficit s)))) " 円")))
                   (str subject " の長期修繕計画が登録されていません"))
     :rationale  (if s
                   (str "最小残高 " (Math/round (double (or (:min-balance s) 0))) " 円 ("
                        (:min-balance-year s) "年目) / 計画期間外に押し出された工事費 "
                        (Math/round (:unmeasured-outflow s)) " 円")
                   "投影不能")
     :cites      (if sb [(:legal-basis sb) (:national-spec sb)] [])
     :effect     :projection/set
     :value      (when s
                   {:assumptions assumptions
                    :deficit (double (or claim-deficit (:deficit s)))
                    :funded? (:funded? s)
                    :min-balance (:min-balance s)
                    :min-balance-year (:min-balance-year s)
                    :first-negative-year (:first-negative-year s)
                    :unmeasured-outflow (:unmeasured-outflow s)
                    :required-contribution required})
     :stake      nil
     :confidence (if s 0.92 0.2)}))

(defn- propose-resolution
  "Draft the tally of a general-meeting resolution.

  `:count-total?` makes the advisor count an ordinary resolution
  against the TOTAL membership -- the denominator 第39条第1項 used
  before the 令和7年改正 came into force on 2026-04-01. Nothing about
  the resulting proposal looks wrong."
  [db {:keys [subject association-id resolution-kind tally budget-amount funding-amount count-total?]}]
  (let [a (store/association db association-id)
        rule (facts/resolution-rule (:jurisdiction a) resolution-kind)
        sb (facts/spec-basis (:jurisdiction a))
        effective-rule (when rule (if count-total? (assoc rule :base :total) rule))
        verdict (when effective-rule
                  (try (resolution/tally effective-rule tally)
                       (catch #?(:clj Exception :cljs :default) _ nil)))]
    {:summary    (if verdict
                   (str subject " (" (name resolution-kind) ") -> "
                        (if (:passed? verdict) "可決" "否決"))
                   (str subject " の決議要件をこの法域について判定できません"))
     :rationale  (if verdict (resolution/explain verdict)
                     (str (:jurisdiction a) " に検証済みの法定決議要件が無い、または集計値が不正"))
     :cites      (if (and sb rule) [(:article rule) (:provenance sb)] [])
     :effect     :resolution/filed
     :value      {:id subject
                  :association-id association-id
                  :kind resolution-kind
                  :budget-amount budget-amount
                  :funding-amount funding-amount
                  :tally tally
                  :verdict verdict
                  :tally-verdict (when verdict
                                   {:passed? (:passed? verdict) :base (:base verdict)})}
     :stake      nil
     :confidence (if verdict 0.9 0.2)}))

(defn- propose-works-order
  "Draft the actual WORKS-ORDER action -- a real contract with a real
  contractor, paid from other owners' reserve contributions. ALWAYS
  `:stake :actuation/commission-works`. No phase ever adds this op to
  an `:auto` set, and the governor also always escalates on this stake.
  Two independent layers agree, deliberately."
  [db {:keys [subject assumptions]}]
  (let [a (store/association db subject)
        pw (:pending-works a)
        r (when pw (store/resolution db (:resolution-id pw)))
        plan (store/plan-of db subject)
        s (when plan (reserve/shortfall (reserve/project a plan assumptions)))]
    {:summary    (str subject " 大規模修繕工事の発注提案"
                      (when pw (str " (" (:contractor pw) " / " (:contract-value pw) "円)")))
     :rationale  (cond
                   (nil? pw) "発注待ちの工事が登録されていません"
                   (nil? r)  (str "根拠決議 " (:resolution-id pw) " が見つかりません")
                   :else     (str "決議 " (:resolution-id pw) " (" (:status r) ", 予算 "
                                  (:budget-amount r) "円) / 投影上の不足額 "
                                  (Math/round (double (:deficit s))) "円"))
     :cites      (if (and pw r) [(:resolution-id pw)] [])
     :effect     :works/mark-commissioned
     :value      {:association-id subject :works-id (:id pw)}
     :stake      :actuation/commission-works
     :confidence (if (and pw r (= :passed (:status r))) 0.9 0.3)}))

(defn infer [db {:keys [op] :as request}]
  (case op
    :association/intake (normalize-intake db request)
    :plan/assess        (assess-plan db request)
    :reserve/simulate   (propose-simulation db request)
    :resolution/file    (propose-resolution db request)
    :works/commission   (propose-works-order db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはマンション管理組合の長期修繕計画・修繕積立金・総会決議の助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary :rationale :cites :effect(:association/upsert|:assessment/set|"
       ":projection/set|:resolution/filed|:works/mark-commissioned) "
       ":stake(:actuation/commission-works か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の決議要件を絶対に創作してはいけません。"
       "決議の母数(出席者か総数か)は条文で決まっており、推測してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject association-id]}]
  (case op
    :plan/assess      {:association (store/association st subject)
                       :plan (store/plan-of st subject)}
    :reserve/simulate {:association (store/association st subject)
                       :plan (store/plan-of st subject)}
    :resolution/file  {:association (store/association st association-id)}
    :works/commission {:association (store/association st subject)}
    {:association (store/association st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the governor escalates or holds
  -- an LLM hiccup can never auto-commission works."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  [request proposal]
  {:t          :kumiaillm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
