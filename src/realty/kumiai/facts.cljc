(ns realty.kumiai.facts
  "Per-jurisdiction CONDOMINIUM-ASSOCIATION regulatory catalog -- the
  spec-basis table the Condominium-Association Governor checks every
  resolution / long-term-repair-plan proposal against ('did the advisor
  cite an OFFICIAL source for this jurisdiction's unit-ownership
  resolution requirements and reserve-fund guidance, or did it invent
  one?').

  This is the `realty.facts` discipline applied to a SECOND subject.
  `realty.facts` covers a management FIRM acting for a property OWNER
  on a fee basis; this covers the OWNERS' ASSOCIATION itself -- the
  body that resolves a major-repair works order and funds it from a
  reserve. They are different legal actors under different statutes,
  so they get different catalogs rather than one blurred table.

  Coverage is reported HONESTLY (see `coverage`). Crucially, so is
  something finer-grained: a jurisdiction can be present here and STILL
  have no verified resolution-threshold table, because in most
  jurisdictions the thresholds live in the association's own
  declaration/bylaws rather than in statute. `resolution-rule` returns
  nil in that case and `realty.kumiai.governor` HOLDS -- it does not
  fall back to a plausible-looking default. An unverified threshold and
  a verified one must never produce the same answer.

  == JPN: verified against primary sources, 2026-09-06 ==

  The Japanese entries are transcribed from the CURRENT statutory text
  (e-Gov 法令検索 API, 建物の区分所有等に関する法律 昭和37年法律第69号)
  and from 国土交通省「マンションの修繕積立金に関するガイドライン」
  (平成23年4月策定 / 令和6年6月改定). They reflect the 令和7年改正
  区分所有法, IN FORCE since 2026-04-01 -- which is not a cosmetic
  update for this actor:

    - 第39条第1項 (ordinary resolutions, which is what a major-repair
      works order is) now counts `出席した区分所有者及びその議決権の
      各過半数` -- the ATTENDING base, not the total base it used to
      count. A proposal that computes a major-repair vote against the
      total membership is now measuring the wrong denominator, and can
      report `fail` for a resolution that actually passed.
    - 第17条第1項 / 第31条第1項 / 第61条第5項 are TWO-STAGE: a quorum
      (`区分所有者の過半数の者であつて議決権の過半数を有するものが出席`)
      and then a supermajority OF THE ATTENDING.
    - 第62条第1項 (rebuilding) stays on the TOTAL base at 4/5, dropping
      to 3/4 for the five statutory conditions of 第62条第2項.

  So within ONE jurisdiction the denominator differs per resolution
  kind, and it changed within the last six months. That is precisely
  the thing an LLM has no way to know it is wrong about, and precisely
  what `realty.kumiai.resolution` makes checkable."
  (:require [clojure.string :as str]))

;; --------------------------------------------------------------------
;; resolution rules
;; --------------------------------------------------------------------
;;
;; A rule is data, not prose:
;;
;;   :article    -- the provision, cited so a human can check it
;;   :base       -- :attending | :total  (WHICH DENOMINATOR the statute
;;                  counts; getting this wrong is a silent wrong answer,
;;                  not an error)
;;   :quorum     -- nil, or the attendance that must be reached FIRST
;;   :axes       -- every axis that must independently clear the bar.
;;                  Most resolutions count two (heads and voting
;;                  rights); the two land-sale resolutions count THREE
;;                  (heads, voting rights, and the value of the share in
;;                  the land-use right).
;;   :fraction   -- {:numer n :denom d} compared by cross-multiplication
;;                  so 2/3 is exact on every runtime (no Ratio literal:
;;                  this is .cljc and ClojureScript has no ratios).
;;   :comparison -- :greater-than for 過半数, :at-least for N分のM以上.
;;   :bylaw      -- what the association's own 規約 may lawfully change.
;;                  A claimed non-statutory threshold with no recorded
;;                  bylaw provision is a HARD violation, not a default.

(def ^:private half {:numer 1 :denom 2})
(def ^:private two-thirds {:numer 2 :denom 3})
(def ^:private three-quarters {:numer 3 :denom 4})
(def ^:private four-fifths {:numer 4 :denom 5})

(def ^:private majority-quorum
  "区分所有者の過半数の者であつて議決権の過半数を有するものが出席。
  規約で「上回る割合」を定めたときはその割合以上 (:bylaw :raise-only)."
  {:axes [:owners :voting-rights]
   :fraction half
   :comparison :greater-than
   :bylaw :raise-only})

(def jpn-resolutions
  "決議種別 -> rule. Transcribed from the current statutory text; every
  entry carries its own `:article` so a human can check it against
  e-Gov rather than against this file."
  {:ordinary
   {:label "普通決議 (修繕等、区分所有権の処分を伴わない事項)"
    :article "建物の区分所有等に関する法律 第39条第1項"
    :base :attending
    :quorum nil
    :axes [:owners :voting-rights]
    :fraction half
    :comparison :greater-than
    :bylaw :any
    :note "令和7年改正で母数が総数から出席者に変わった (2026-04-01施行)。大規模修繕工事の発注はこの決議種別。"}

   :common-area-major-change
   {:label "共用部分の変更 (その形状又は効用の著しい変更を伴うもの)"
    :article "建物の区分所有等に関する法律 第17条第1項"
    :base :attending
    :quorum majority-quorum
    :axes [:owners :voting-rights]
    :fraction three-quarters
    :comparison :at-least
    :bylaw :lower-to-above-half
    :note "規約で4分の3を下回る割合(2分の1を超える割合に限る)に引き下げ可。引き下げには規約の定めの実在が要る。"}

   :bylaw-amendment
   {:label "規約の設定・変更・廃止"
    :article "建物の区分所有等に関する法律 第31条第1項"
    :base :attending
    :quorum majority-quorum
    :axes [:owners :voting-rights]
    :fraction three-quarters
    :comparison :at-least
    :bylaw :none
    :note "第17条と違い、可決要件の引き下げ規定は無い。"}

   :restoration
   {:label "大規模滅失の復旧"
    :article "建物の区分所有等に関する法律 第61条第5項"
    :base :attending
    :quorum majority-quorum
    :axes [:owners :voting-rights]
    :fraction two-thirds
    :comparison :at-least
    :bylaw :none}

   :reconstruction
   {:label "建替え決議"
    :article "建物の区分所有等に関する法律 第62条第1項"
    :base :total
    :quorum nil
    :axes [:owners :voting-rights]
    :fraction four-fifths
    :comparison :at-least
    :relaxed {:fraction three-quarters
              :article "同法 第62条第2項第1号乃至第5号"
              :conditions [:seismic-deficient :fire-safety-deficient :exterior-fall-risk
                           :pipe-corrosion-sanitary :accessibility-noncompliant]}
    :bylaw :none
    :note "母数は総数のまま (出席者ベースではない)。第62条第2項各号のいずれかに該当するときのみ4分の3。"}

   :building-renewal
   {:label "建物更新決議 (一棟リノベーション)"
    :article "建物の区分所有等に関する法律 第64条の5第1項"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction four-fifths :comparison :at-least
    :relaxed {:fraction three-quarters :article "同法 第62条第2項各号の準用"}
    :bylaw :none}

   :building-and-site-sale
   {:label "建物敷地売却決議"
    :article "建物の区分所有等に関する法律 第64条の6第1項"
    :base :total :quorum nil
    :axes [:owners :voting-rights :land-use-right-value]
    :fraction four-fifths :comparison :at-least
    :relaxed {:fraction three-quarters :article "同法 第62条第2項各号の準用"}
    :bylaw :none
    :note "第3の軸 (敷地利用権の持分の価格) を数えないと要件判定にならない。"}

   :demolition-and-site-sale
   {:label "建物取壊し敷地売却決議"
    :article "建物の区分所有等に関する法律 第64条の7第1項"
    :base :total :quorum nil
    :axes [:owners :voting-rights :land-use-right-value]
    :fraction four-fifths :comparison :at-least
    :relaxed {:fraction three-quarters :article "同法 第62条第2項各号の準用"}
    :bylaw :none}

   :demolition
   {:label "取壊し決議"
    :article "建物の区分所有等に関する法律 第64条の8第1項"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction four-fifths :comparison :at-least
    :relaxed {:fraction three-quarters :article "同法 第62条第2項各号の準用"}
    :bylaw :none}})

;; --------------------------------------------------------------------
;; reserve-fund guidance
;; --------------------------------------------------------------------

(def jpn-reserve-guideline
  "国土交通省「マンションの修繕積立金に関するガイドライン」
  (平成23年4月策定 / 令和6年6月改定) の目安値。

  値は 円/㎡・月、機械式駐車場分を除く。目安は 366事例 -- いずれも
  長期修繕計画作成ガイドラインに概ね沿い、計画期間が新築30年以上・
  既存25年以上で、大規模修繕工事が2回以上含まれるもの -- から導いた
  もので、規模以外の変動要因は織り込まれていない。ガイドライン自身が
  『幅に収まっていないからといって直ちに不適切と判断されるわけでは
  ない』と述べているので、`realty.kumiai.governor` はこの幅の外を
  HOLD ではなく ESCALATE として扱う。"
  {:edition "令和6年6月改定"
   :provenance "https://www.mlit.go.jp/jutakukentiku/house/content/001747009.pdf"
   :sample-size 366
   :unit "円/㎡・月"
   :excludes "機械式駐車場分 (:mechanical-parking で別途加算)"
   ;; 建築延床面積による区分 (地上20階未満)。:upto は 未満 の上限。
   :by-gross-floor-area [{:upto 5000  :low 235 :high 430 :average 335}
                         {:upto 10000 :low 170 :high 320 :average 252}
                         {:upto 20000 :low 200 :high 330 :average 271}
                         {:upto nil   :low 190 :high 325 :average 255}]
   :high-rise {:min-floors 20 :low 240 :high 410 :average 338}
   ;; 機械式駐車場 1台あたりの修繕工事費 (円/台・月)。
   :mechanical-parking {"2段(ピット1段)昇降式"     6450
                        "3段(ピット2段)昇降式"     5840
                        "3段(ピット1段)昇降横行式" 7210
                        "4段(ピット2段)昇降横行式" 6235}
   ;; 段階増額積立方式における適切な引上げの考え方 (令和6年6月改定で追加):
   ;;   0.6 x D <= E  かつ  1.1 x D >= F
   ;;   D=計画期間全体の月平均額 E=最低額 F=最高額 (いずれも 円/㎡・月)
   :staged-increase {:initial-min-ratio 0.6 :final-max-ratio 1.1
                     :preferred :level                     ; 均等積立方式が望ましい
                     :note "実現性をもった引上げで早期に完了させ、均等積立方式へ誘導することが目的。"}
   :plan-horizon-years {:new 30 :existing 25}
   :major-repair-cycles-min 2
   :plan-review-interval-years 5})

;; --------------------------------------------------------------------
;; catalog
;; --------------------------------------------------------------------

(def catalog
  "iso3 -> condominium-association requirement map.

  `:resolutions` is present ONLY where the thresholds are set by
  STATUTE and this catalog has been checked against that statute's
  current text. Where an association's declaration/bylaws set them
  instead, the key is absent -- and `resolution-rule` returns nil, so
  the governor holds. That absence is the honest answer, not a gap to
  paper over with a plausible default."
  {"JPN"
   {:name "Japan"
    :owner-authority "法務省 (Ministry of Justice) / 国土交通省 (MLIT)"
    :legal-basis "建物の区分所有等に関する法律 (昭和37年法律第69号、令和7年改正 2026-04-01施行)"
    :management-act "マンションの管理の適正化の推進に関する法律 (平成12年法律第149号) -- 管理計画認定制度"
    :national-spec "国土交通省 長期修繕計画作成ガイドライン / マンションの修繕積立金に関するガイドライン (令和6年6月改定)"
    :provenance "https://laws.e-gov.go.jp/law/337AC0000000069"
    :verified-on "2026-09-06"
    :resolutions jpn-resolutions
    :reserve-guideline jpn-reserve-guideline
    :required-evidence ["管理規約 (bylaws / declaration)"
                        "長期修繕計画書 (long-term repair plan)"
                        "修繕積立金会計の収支報告書 (reserve-fund account statement)"
                        "総会議事録 (minutes of the general meeting)"
                        "設計監理者又は建築士による工事見積の妥当性確認書面"]}

   "USA-NY"
   {:name "United States -- New York (exemplar; no national condominium statute)"
    :owner-authority "New York Department of State / county recording officer"
    :legal-basis "New York Real Property Law Article 9-B (Condominium Act)"
    :provenance "https://dos.ny.gov/"
    :verified-on nil
    :notes ["Condominium law is state law -- there is no federal condominium statute; New York is an exemplar, not a national authority."
            "Voting thresholds for a common-element alteration or a special assessment are fixed by the declaration and bylaws of the individual condominium, not by a single statutory fraction -- so this catalog carries NO :resolutions table and the governor holds any resolution proposal for this jurisdiction."]
    :required-evidence ["Declaration and bylaws"
                        "Reserve study"
                        "Reserve-fund account statement"
                        "Minutes of the board / unit-owner meeting"
                        "Engineer's scope and cost opinion"]}

   "GBR"
   {:name "United Kingdom (England and Wales)"
    :owner-authority "Department for Levelling Up, Housing and Communities (DLUHC) / First-tier Tribunal (Property Chamber)"
    :legal-basis "Landlord and Tenant Act 1985 s.20 (consultation on qualifying works) and s.20ZA; Commonhold and Leasehold Reform Act 2002"
    :provenance "https://www.gov.uk/government/organisations/department-for-levelling-up-housing-and-communities"
    :verified-on nil
    :notes ["Most blocks of flats are leasehold, not commonhold: major works are funded through a service charge and consulted on under s.20, NOT resolved by a unit-owner supermajority. The Japanese/US resolution model does not transfer, so this catalog carries NO :resolutions table."
            "s.20 consultation is a procedural precondition to recovering more than the statutory contribution limit -- it belongs in :required-evidence, and the governor treats it as evidence, not as a vote."]
    :required-evidence ["Lease / service charge schedule"
                        "s.20 notice of intention and observations"
                        "s.20 notice of estimates"
                        "Service charge account and reserve (sinking) fund statement"
                        "Surveyor's specification and cost estimate"]}

   "DEU"
   {:name "Germany"
    :owner-authority "Bundesministerium der Justiz (BMJ)"
    :legal-basis "Wohnungseigentumsgesetz (WEG), in der Fassung des WEMoG 2020 -- insbesondere §19 (Erhaltungsrücklage), §20 (bauliche Veränderungen)"
    :provenance "https://www.gesetze-im-internet.de/woeigg/"
    :verified-on nil
    :notes ["The 2020 WEMoG reform moved most Beschlüsse to a simple majority of votes cast, with separate rules in §21 for who bears the cost of a bauliche Veränderung. This catalog has NOT been checked against the current §§19-21 text, so it carries NO :resolutions table rather than a remembered one."]
    :required-evidence ["Gemeinschaftsordnung / Teilungserklärung"
                        "Erhaltungsrücklage -- Kontostand und Vermögensbericht (§19 Abs. 2 Nr. 6, §28 Abs. 4)"
                        "Wirtschaftsplan und Jahresabrechnung"
                        "Protokoll der Eigentümerversammlung"
                        "Sachverständigen-Kostenschätzung"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO
  spec-basis, and the governor must hold any proposal built on it."
  [iso3]
  (get catalog iso3))

(defn resolution-rule
  "The statutory rule for one resolution kind, or nil.

  nil has exactly one meaning: THIS ACTOR HAS NO VERIFIED STATUTORY
  RULE for that (jurisdiction, kind). It never means 'use the usual
  majority'. Callers must hold, not guess -- see
  `realty.kumiai.governor/threshold-unverified-violations`."
  [iso3 kind]
  (get-in catalog [iso3 :resolutions kind]))

(defn reserve-guideline [iso3]
  (get-in catalog [iso3 :reserve-guideline]))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn required-evidence-satisfied?
  "Does `submitted` satisfy every evidence item listed for `iso3`?
  Missing spec-basis -> never satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [have (set submitted)]
      (every? have required-evidence))))

(defn coverage
  "Honest coverage report, at TWO resolutions: how many jurisdictions
  have a spec-basis at all, and -- separately -- how many of those have
  a VERIFIED statutory resolution-threshold table. The second number is
  the one that decides whether a resolution can be judged at all, and
  it is much smaller than the first. Reporting only the first would
  make this actor look four times as capable as it is."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)
         with-rules (filter #(seq (get-in catalog [% :resolutions])) have)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :with-statutory-resolution-thresholds (vec (sort with-rules))
      :without-statutory-resolution-thresholds (vec (sort (remove (set with-rules) have)))
      :note (str "cloud-itonami-isic-6820 kumiai R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis, of which "
                 (count with-rules)
                 " carry a statutory resolution-threshold table checked against the "
                 "current text. In the rest, thresholds live in the association's own "
                 "declaration/bylaws (or the tenure is leasehold and there is no "
                 "unit-owner vote at all) -- the governor HOLDS a resolution proposal "
                 "there rather than applying a remembered default. Extend "
                 "`realty.kumiai.facts/catalog` by citing a real source; never invent "
                 "a jurisdiction's thresholds to make coverage look bigger.")})))

(defn jurisdiction-summary
  "One-line human summary, used by the demo/console."
  [iso3]
  (if-let [b (spec-basis iso3)]
    (str iso3 " -- " (:name b) " / " (:legal-basis b)
         (if (seq (:resolutions b))
           (str " [決議要件 " (count (:resolutions b)) " 種、検証日 " (:verified-on b) "]")
           " [決議要件テーブル無し -- 規約/リース法制に依るため判定不能]"))
    (str iso3 " -- NO SPEC-BASIS")))

(defn relaxation-conditions
  "The statutory conditions that let a rebuilding-class resolution drop
  from 4/5 to 3/4, or nil."
  [iso3 kind]
  (get-in (resolution-rule iso3 kind) [:relaxed :conditions]))

(defn describe-fraction [{:keys [numer denom]}]
  (str numer "/" denom))

(defn rule-summary [rule]
  (when rule
    (str (:label rule) " -- " (:article rule)
         " / 母数=" (name (:base rule))
         " / " (str/join "+" (map name (:axes rule)))
         " の各" (describe-fraction (:fraction rule))
         (if (= :greater-than (:comparison rule)) "超" "以上")
         (when (:quorum rule) " (定足数あり)"))))
