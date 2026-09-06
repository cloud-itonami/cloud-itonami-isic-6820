(ns realty.kumiai.sim
  "Demo driver -- `clojure -M:dev:run-kumiai`.

  Walks one association through intake -> plan assessment -> reserve
  projection under three assumption sets -> general-meeting resolution
  -> works order -> human approval -> commit, then shows the HARD holds
  that never reach a human at all.

  The three projections are the point. Same association, same plan,
  same contributions; only the construction-cost escalation and the
  schedule slippage move -- and the plan goes from funded, to short, to
  short AND carrying a cost that fell off the end of the horizon
  entirely."
  (:require [langgraph.graph :as g]
            [realty.kumiai.facts :as facts]
            [realty.kumiai.reserve :as reserve]
            [realty.kumiai.store :as store]
            [realty.kumiai.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :association-manager :phase 3})

(def flat {:cost-escalation 0.0 :delay-years 0})
(def rising {:cost-escalation 0.05 :delay-years 0})
(def rising-and-late {:cost-escalation 0.05 :delay-years 2})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- passing-tally
  "A tally that clears 第39条第1項 on the ATTENDING base: 44 of 70
  owners attend (incl. proxies and written votes, which 第39条第2項
  counts as attendance), 30 of them in favour. Against the total
  membership 30/70 is a clear failure; against the attending 44 it is a
  clear pass. Same vote, opposite answers -- which denominator applies
  is the law, not a modelling choice."
  []
  {:total     {:owners 70 :voting-rights 4900}
   :attending {:owners 44 :voting-rights 3080}
   :in-favour {:owners 30 :voting-rights 2100}})

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)
        a (store/association db "association-1")
        plan (store/plan-of db "association-1")]

    (println "== 法域カバレッジ (正直な二段報告) ==")
    (println (facts/coverage))
    (println)
    (doseq [j ["JPN" "USA-NY" "GBR" "DEU"]] (println " " (facts/jurisdiction-summary j)))

    (println)
    (println "== 決議要件テーブル (JPN、e-Gov 現行条文より、令和7年改正 2026-04-01施行) ==")
    (doseq [[k rule] (sort-by key facts/jpn-resolutions)]
      (println " " k "->" (facts/rule-summary rule)))

    (println)
    (println "== association/intake association-1 ==")
    (println (exec! actor "k1" {:op :association/intake :subject "association-1"
                                :patch {:id "association-1" :name "さくら台マンション管理組合"}} operator))

    (println)
    (println "== plan/assess association-1 (escalates -- human approves) ==")
    (println (exec! actor "k2" {:op :plan/assess :subject "association-1"} operator))
    (println (approve! actor "k2"))

    (println)
    (println "== 修繕積立金投影: 同じ計画・同じ積立額で、上昇率と遅延だけを動かす ==")
    (doseq [[label assumptions] [["工事費上昇率 0% / 遅延なし" flat]
                                 ["工事費上昇率 5% / 遅延なし" rising]
                                 ["工事費上昇率 5% / 工期遅延 2年" rising-and-late]]]
      (let [s (reserve/shortfall (reserve/project a plan assumptions))]
        (println (str "  " label
                      " -> " (if (:funded? s) "充足" "不足")
                      " / 最小残高 " (Math/round (double (:min-balance s))) "円 (" (:min-balance-year s) "年目)"
                      " / 不足額 " (Math/round (:deficit s)) "円"
                      " / 計画期間外に押し出された工事費 " (Math/round (:unmeasured-outflow s)) "円 "
                      (pr-str (:deferred-works s))))))

    (println)
    (println "== 感度表 (上昇率 x 遅延年数) -- 理事会が実際に決めるのはこの表に対してである ==")
    (doseq [row (reserve/sensitivity a plan {} [0.0 0.02 0.05 0.08] [0 1 2 3])]
      (println (str "  上昇率 " (* 100.0 (:cost-escalation row)) "% / 遅延 " (:delay-years row) "年 -> "
                    (if (:funded? row) "充足" (str "不足 " (Math/round (:deficit row)) "円"))
                    (when (pos? (:unmeasured-outflow row))
                      (str " [未計上 " (Math/round (:unmeasured-outflow row)) "円]")))))

    (println)
    (println "== 必要積立額 (上昇率5%/遅延なしを賄う最小の均等積立単価) ==")
    (println " " (reserve/required-contribution a plan rising))

    (println)
    (println "== ガイドライン照合 (国交省 令和6年6月改定) ==")
    (println "  Z =" (reserve/guideline-average a plan))
    (println "  目安 =" (reserve/benchmark-band "JPN" a))
    (println "  判定 =" (reserve/assess-against-benchmark "JPN" a plan))
    (println "  段階増額の考え方 =" (reserve/staged-increase-verdict "JPN" a plan))

    (println)
    (println "== reserve/simulate association-1 (5%/遅延なし、auto-commit -- これが台帳上の確定投影になる) ==")
    (println (exec! actor "k3" {:op :reserve/simulate :subject "association-1"
                                :assumptions rising} operator))

    (println)
    (println "== resolution/file res-1 (普通決議、出席者ベース -- escalates、人間が承認) ==")
    (let [r (exec! actor "k4" {:op :resolution/file :subject "res-1"
                               :association-id "association-1"
                               :resolution-kind :ordinary
                               :budget-amount 150000000
                               :funding-amount 0
                               :tally (passing-tally)} operator)]
      (println r)
      (println (approve! actor "k4")))

    (println)
    (println "== 発注待ちの工事を登録 (works-1 / 120,000,000円 / 根拠 res-1) ==")
    (println (exec! actor "k5" {:op :association/intake :subject "association-1"
                                :patch {:id "association-1"
                                        :pending-works {:id "works-1"
                                                        :contractor "南工務店"
                                                        :contract-value 120000000
                                                        :resolution-id "res-1"}}} operator))

    (println)
    (println "== HARD hold: 確定投影(5%)が示す不足額に対し、決議による借入・一時金の裏付けが無い ==")
    (println (exec! actor "k6" {:op :works/commission :subject "association-1"} operator))

    (println)
    (println "== 理事会が上昇率0%の投影を採用し直す (台帳には両方が残る -- これが唯一の歯止め) ==")
    (println (exec! actor "k7" {:op :reserve/simulate :subject "association-1"
                                :assumptions flat} operator))

    (println)
    (println "== works/commission association-1 (常に escalate -- actuation/commission-works) ==")
    (let [r (exec! actor "k8" {:op :works/commission :subject "association-1"} operator)]
      (println r)
      (println "-- 人間(理事長)が承認 --")
      (println (approve! actor "k8")))

    (println)
    (println "== HARD hold: 同じ工事の二重発注 (:pending-works は発注で消えるので同じ検査に落ちる) ==")
    (println (exec! actor "k9" {:op :works/commission :subject "association-1"} operator))

    (println)
    (println "== HARD hold: 決議予算 150,000,000円 を超える 200,000,000円 の発注 ==")
    (println (exec! actor "k10" {:op :association/intake :subject "association-1"
                                 :patch {:id "association-1"
                                         :pending-works {:id "works-2"
                                                         :contractor "南工務店"
                                                         :contract-value 200000000
                                                         :resolution-id "res-1"}}} operator))
    (println (exec! actor "k11" {:op :works/commission :subject "association-1"} operator))

    (println)
    (println "== HARD hold: 総数ベースで数えた普通決議 (可否は同じでも母数が条文と違う) ==")
    (println (exec! actor "k12" {:op :resolution/file :subject "res-2"
                                 :association-id "association-1"
                                 :resolution-kind :ordinary
                                 :budget-amount 150000000
                                 :tally (passing-tally)
                                 :count-total? true} operator))

    (println)
    (println "== HARD hold: 検証済み決議要件の無い法域 (USA-NY -- 規約で定まるため既定値を当てない) ==")
    (println (exec! actor "k13" {:op :resolution/file :subject "res-3"
                                 :association-id "association-4"
                                 :resolution-kind :common-area-major-change
                                 :budget-amount 100000000
                                 :tally {:total {:owners 55 :voting-rights 5200}
                                         :attending {:owners 40 :voting-rights 3800}
                                         :in-favour {:owners 35 :voting-rights 3300}}} operator))

    (println)
    (println "== HARD hold: spec-basis の無い法域 (ATL) ==")
    (println (exec! actor "k14" {:op :plan/assess :subject "association-2"} operator))

    (println)
    (println "== HARD hold: 管理受託されていない組合 ==")
    (println (exec! actor "k15" {:op :plan/assess :subject "association-3"} operator))

    (println)
    (println "== HARD hold: 計画期間15年・大規模修繕1回 -- ガイドラインの目安と比較できない計画 ==")
    (println (exec! actor "k16" {:op :reserve/simulate :subject "association-5"
                                 :assumptions flat} operator))

    (println)
    (println "== HARD hold: 主張された不足額が独立再計算と一致しない ==")
    (println (exec! actor "k17" {:op :reserve/simulate :subject "association-1"
                                 :assumptions rising :claim-deficit 1} operator))

    (println)
    (println "== HARD hold: 遅延で工事が計画期間外に押し出され、投影に計上されていない ==")
    (println (exec! actor "k18" {:op :reserve/simulate :subject "association-1"
                                 :assumptions rising-and-late} operator))

    (println)
    (println "== 監査台帳 ==")
    (doseq [f (store/ledger db)] (println " " f))

    (println)
    (println "== 決議録ドラフト ==")
    (doseq [r (store/resolution-history db)] (println " " r))

    (println)
    (println "== 工事発注ドラフト ==")
    (doseq [r (store/works-history db)] (println " " r))))
