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

  == Verified jurisdictions, 2026-09-06 ==

  JPN, DEU, ESP and FRA carry `:resolutions` tables transcribed from
  the current statutory text fetched on that date (e-Gov 法令検索,
  gesetze-im-internet.de, BOE, Légifrance). Across those four the rule
  SHAPE varies more than the fractions do:

    denominator   JPN 出席者 / 総数  ·  DEU abgegebene Stimmen
                  FRA voix exprimées / voix de tous  ·  ESP total, and
                  the ATTENDING on a second call of the same meeting
    axes          JPN heads + voting rights (+ land-use-right value on
                  the two sale resolutions)  ·  DEU heads, and heads +
                  co-ownership shares for cost allocation  ·  ESP
                  owners + participation quotas  ·  FRA voices, and
                  members + voices at article 26
    per-axis      FRA article 26 (>1/2 of members AND >=2/3 of voices)
    fractions     and DEU § 21 Abs. 2 (>2/3 of votes CAST and >1/2 of
                  ALL shares) both need a different fraction, and DEU a
                  different denominator, on each axis of one rule
    fallback      FRA article 25-1 permits an immediate second ballot
                  at the lower majority
    quorum        JPN 第17条/第31条/第61条第5項 only

  A single-fraction, single-denominator model would answer confidently
  and wrongly in three of the four.

  == JPN ==

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
  (:require [kotoba.lang.text :as str]))

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

(def ^:private none {:numer 0 :denom 1})
(def ^:private one-quarter {:numer 1 :denom 4})
(def ^:private one-third {:numer 1 :denom 3})
(def ^:private half {:numer 1 :denom 2})
(def ^:private three-fifths {:numer 3 :denom 5})
(def ^:private two-thirds {:numer 2 :denom 3})
(def ^:private three-quarters {:numer 3 :denom 4})
(def ^:private four-fifths {:numer 4 :denom 5})
(def ^:private nine-tenths {:numer 9 :denom 10})
(def ^:private unanimity {:numer 1 :denom 1})

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

(def deu-resolutions
  "Transcribed from the current Wohnungseigentumsgesetz text
  (gesetze-im-internet.de, the official BMJ/juris edition), in the
  version left by the WEMoG reform of 2020.

  Germany is the clearest case of a denominator this actor could not
  have guessed: § 25 Absatz 1 decides by `die Mehrheit der abgegebenen
  Stimmen` -- the votes actually CAST. Abstentions are not in the
  denominator at all, and the pre-2020 Beschlussfähigkeit quorum is
  gone, so a sparsely attended meeting can still resolve.

  It is also the clearest case of a rule whose two axes are counted
  differently. § 21 Absatz 2 Nummer 1 needs `mehr als zwei Dritteln der
  abgegebenen Stimmen UND der Hälfte aller Miteigentumsanteile`: two
  thirds of the votes cast, and half of ALL co-ownership shares.
  Different fraction, different denominator, one rule.

  And the distinction that matters most for a repair programme:
  DECIDING to carry out a bauliche Veränderung is an ordinary majority
  (§ 20 Absatz 1). Whether EVERY owner then bears the cost, rather than
  only those who voted for it, is the separate qualified majority of
  § 21 Absatz 2 -- otherwise § 21 Absatz 3 puts the cost on the
  yes-voters alone. An actor that reported 'the works were approved'
  without that second question would be answering a question nobody
  asked."
  {:ordinary
   {:label "Beschlussfassung (ordnungsmäßige Verwaltung und Erhaltung)"
    :article "Wohnungseigentumsgesetz (WEG) § 25 Absatz 1"
    :base :cast
    :quorum nil
    :axes [:owners]
    :fraction half
    :comparison :greater-than
    :bylaw :agreement-only
    :note "§ 25 Absatz 2: Jeder Wohnungseigentümer hat eine Stimme (Kopfprinzip)。母数は abgegebene Stimmen で、棄権は分母に入らない。投票原理を変えられるのは Vereinbarung であって決議ではない。"}

   :structural-alteration
   {:label "Bauliche Veränderung -- Beschluss über die Maßnahme"
    :article "Wohnungseigentumsgesetz (WEG) § 20 Absatz 1 i.V.m. § 25 Absatz 1"
    :base :cast :quorum nil :axes [:owners]
    :fraction half :comparison :greater-than
    :bylaw :agreement-only
    :note "措置そのものは単純多数。全員に費用を負担させられるかは :structural-alteration-cost-allocation の別要件。"}

   :structural-alteration-cost-allocation
   {:label "Bauliche Veränderung -- Kostenverteilung auf alle Wohnungseigentümer"
    :article "Wohnungseigentumsgesetz (WEG) § 21 Absatz 2 Nummer 1"
    :base :cast :quorum nil :fraction nil :comparison :greater-than
    :axes [{:axis :owners :base :cast :fraction two-thirds :comparison :greater-than}
           {:axis :co-ownership-shares :base :total :fraction half :comparison :greater-than}]
    :bylaw :none
    :note "この多数に達しなければ、§ 21 Absatz 3 により費用は賛成した区分所有者だけが負担する。"}

   :virtual-meeting
   {:label "Virtuelle Wohnungseigentümerversammlung"
    :article "Wohnungseigentumsgesetz (WEG) § 23 Absatz 1a"
    :base :cast :quorum nil :axes [:owners]
    :fraction three-quarters :comparison :at-least
    :bylaw :none
    :note "最長3年間。`mindestens drei Vierteln der abgegebenen Stimmen`。"}})

(def esp-resolutions
  "Transcribed from the consolidated text of the Ley 49/1960, de 21 de
  julio, sobre propiedad horizontal (BOE-A-1960-10906), artículo 17.

  Spain counts two axes on every rule -- `los propietarios` and `las
  cuotas de participación` -- and moves the DENOMINATOR between the
  first and the second call of the same meeting: 17.7 needs a majority
  of ALL owners and ALL quotas on the first call, but on the second
  call only `la mayoría de los asistentes` representing more than half
  of the quotas `de los presentes`. Those are two different rules, so
  they are two entries here rather than one rule with a hidden flag.

  Article 17.8 also lets an absent owner who was properly summoned and
  does not dissent within 30 days be counted AS A VOTE IN FAVOUR. This
  catalog records that (`:deemed-consent` on the jurisdiction) but
  never applies it: whether notice was proper and whether 30 days have
  run are facts about the world, not arithmetic."
  {:ordinary-first-call
   {:label "Acuerdos no regulados expresamente -- primera convocatoria"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.7 (primer inciso)"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction half :comparison :greater-than
    :bylaw :none
    :note ":voting-rights はこの法域では cuotas de participación。"}

   :ordinary-second-call
   {:label "Acuerdos no regulados expresamente -- segunda convocatoria"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.7 (segundo inciso)"
    :base :attending :quorum nil
    :axes [:owners :voting-rights]
    :fraction half :comparison :greater-than
    :bylaw :none
    :note "母数が出席者に変わる (mayoría de los asistentes / más de la mitad del valor de las cuotas de los presentes)。"}

   :accessibility-works
   {:label "Obras de accesibilidad y establecimiento del servicio de ascensor"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.2"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction half :comparison :greater-than
    :bylaw :none}

   :common-services
   {:label "Establecimiento o supresión de servicios comunes de interés general"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.3"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction three-fifths :comparison :at-least
    :bylaw :none}

   :improvements
   {:label "Innovaciones y mejoras no requeridas para la conservación"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.4 (párrafo segundo)"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction three-fifths :comparison :at-least
    :bylaw :none}

   :structural-alteration
   {:label "Alteración de la estructura o fábrica del edificio, división o agregación"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.4 (párrafo tercero)"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction three-fifths :comparison :at-least
    :bylaw :none}

   :telecom-or-energy-infrastructure
   {:label "Infraestructuras comunes de telecomunicación o de energías renovables"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.1"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction one-third :comparison :at-least
    :bylaw :none
    :note "費用は賛成しなかった区分所有者に転嫁できない (17.1 párrafo segundo)。"}

   :bylaw-amendment
   {:label "Aprobación o modificación del título constitutivo o de los estatutos"
    :article "Ley 49/1960 sobre propiedad horizontal, artículo 17.6"
    :base :total :quorum nil
    :axes [:owners :voting-rights]
    :fraction unanimity :comparison :at-least
    :bylaw :none}})

(def fra-resolutions
  "Transcribed from the current text of the loi n° 65-557 du 10 juillet
  1965 fixant le statut de la copropriété des immeubles bâtis
  (Légifrance).

  France supplies two shapes this actor would otherwise not have:

  - article 26 is `la majorité des membres du syndicat représentant au
    moins les deux tiers des voix` -- MORE THAN HALF the members and AT
    LEAST two thirds of the voices. One rule, two axes, two different
    fractions and two different comparisons.
  - article 25-1 is a statutory FALLBACK: a resolution that failed the
    article 25 majority but reached at least a third of all owners'
    votes may be voted again, in the same assembly, at the article 24
    majority. This catalog records the precondition; the second ballot
    is an event, and `realty.kumiai.resolution` reports that it is
    available rather than pretending it happened.

  Note which majority a repair programme runs on: article 24 II a)
  puts `les travaux nécessaires à la conservation de l'immeuble` at the
  article 24 majority -- the votes CAST by those present, represented
  or voting by post."
  {:ordinary
   {:label "Décisions de l'assemblée générale -- majorité simple"
    :article "Loi n° 65-557 du 10 juillet 1965, article 24"
    :base :cast :quorum nil
    :axes [:voting-rights]
    :fraction half :comparison :greater-than
    :bylaw :none
    :note "`majorité des voix exprimées des copropriétaires présents, représentés ou ayant voté par correspondance`。travaux nécessaires à la conservation de l'immeuble はこの多数 (article 24 II a)。"}

   :absolute-majority
   {:label "Décisions à la majorité de tous les copropriétaires"
    :article "Loi n° 65-557 du 10 juillet 1965, article 25"
    :base :total :quorum nil
    :axes [:voting-rights]
    :fraction half :comparison :greater-than
    :bylaw :none
    :fallback {:article "Loi n° 65-557 du 10 juillet 1965, article 25-1"
               :threshold one-third
               :axes [:voting-rights]
               :of :total
               :to :ordinary
               :note "第25条で可決に至らなかったが全区分所有者の議決権の3分の1以上を得たときは、同一の総会で第24条の多数により再度決議できる。自動的に可決するのではなく、二度目の投票が要る。"}}

   :double-majority
   {:label "Décisions à la double majorité de l'article 26"
    :article "Loi n° 65-557 du 10 juillet 1965, article 26"
    :base :total :quorum nil :fraction nil :comparison :greater-than
    :axes [{:axis :owners :base :total :fraction half :comparison :greater-than}
           {:axis :voting-rights :base :total :fraction two-thirds :comparison :at-least}]
    :bylaw :none
    :note "`la majorité des membres du syndicat représentant au moins les deux tiers des voix`。軸ごとに分数も比較も違う。"}})

(def sgp-resolutions
  "Transcribed from the current text of the Building (Strata
  Management) Act 2004, section 2, on Singapore Statutes Online.

  Note the TITLE. The act this catalog previously pointed at as
  `Building Maintenance and Strata Management Act 2004` is now the
  `Building (Strata Management) Act 2004`, and its SSO identifier is
  `BSMA2004`, not `BMSMA2004` -- which is why every earlier fetch
  returned a 200 carrying a Page Not Found. Remembering an act's name
  is not knowing it.

  Singapore contributes two shapes no civil-law entry in this catalog
  needed:

  - The ordinary resolution has NO BASE. s 2(2)(b) decides it by
    comparing the valid votes FOR against the valid votes AGAINST --
    on a show of hands by count, and `on a poll` by aggregate share
    value. Abstentions appear on neither side and a TIE fails. Writing
    it as a fraction would require inventing a denominator, and the
    invented one would part company with the statute as soon as anyone
    abstained.
  - The NOTICE PERIOD is part of the definition, not evidence around
    it: `a motion is decided by ordinary resolution if (a) the motion
    is passed at a duly convened general meeting held on the 15th day
    (or later) after the notice ... and (b) the votes ...`. A motion
    carried on the 14th day is not a narrowly-failed ordinary
    resolution. It is not an ordinary resolution.

  s 2(8) also defines a valid vote negatively -- a vote given both for
  and against, unmarked, or void for uncertainty is not one. That is a
  fact about each ballot paper, so this actor takes the valid counts as
  given and does not attempt to derive them."
  {:ordinary-show-of-hands
   {:label "Ordinary resolution -- no poll taken"
    :article "Building (Strata Management) Act 2004, section 2(2)(b)(i)"
    :notice-days 15
    :base :cast :quorum nil :fraction nil :comparison :more-than-opposed
    :axes [{:axis :valid-votes :base :cast :comparison :more-than-opposed}]
    :bylaw :none
    :note "賛成票数 > 反対票数。分母は無く、同数は否決。"}

   :ordinary-poll
   {:label "Ordinary resolution -- on a poll"
    :article "Building (Strata Management) Act 2004, section 2(2)(b)(ii)"
    :notice-days 15
    :base :cast :quorum nil :fraction nil :comparison :more-than-opposed
    :axes [{:axis :share-value :base :cast :comparison :more-than-opposed}]
    :bylaw :none
    :note "poll を採ると、頭数ではなく lot の share value で数える。同じ総会・同じ議案でも数え方が変わる。"}

   :special
   {:label "Special resolution"
    :article "Building (Strata Management) Act 2004, section 2(3)"
    :notice-days 22
    :base :cast :quorum nil
    :axes [:share-value]
    :fraction three-quarters :comparison :at-least
    :bylaw :none
    :note "母数は出席者が投じた有効票の share value 合計 (棄権・無効票を含まない)。"}

   :ninety-percent
   {:label "90% resolution"
    :article "Building (Strata Management) Act 2004, section 2(5)"
    :notice-days 22
    :base :cast :quorum nil
    :axes [:share-value]
    :fraction nine-tenths :comparison :at-least
    :bylaw :none}

   :unanimous
   {:label "Unanimous resolution"
    :article "Building (Strata Management) Act 2004, section 2(4)"
    :notice-days 22
    :base :cast :quorum nil
    :axes [:valid-votes]
    :fraction unanimity :comparison :at-least
    :bylaw :none
    :note "出席者が投じた有効票の全部。全区分所有者の全部ではない。"}

   :comprehensive
   {:label "Comprehensive resolution"
    :article "Building (Strata Management) Act 2004, section 2(6)"
    :notice-days 22
    :base :total :quorum nil
    :axes [:share-value]
    :fraction nine-tenths :comparison :at-least
    :bylaw :none
    :note "総会から12週間後の時点の全区分所有者の share value に対して90%。母数が総数であり、しかも測定時点が総会より後ろにある。"}

   :by-consensus
   {:label "Resolution by consensus"
    :article "Building (Strata Management) Act 2004, section 2(7)"
    :notice-days 22
    :base :total :quorum nil
    :axes [:owners]
    :fraction unanimity :comparison :at-least
    :bylaw :none
    :note "投票ではなく、12週間後の時点の全区分所有者による書面での支持。"}})

(def ^:private nsw-quorum
  "Strata Schemes Management Act 2015 (NSW), Schedule 1 clause 17(2).

  DISJUNCTIVE: a quorum is present if not less than a quarter of the
  persons entitled to vote are present, OR not less than a quarter of
  the aggregate unit entitlement is represented. Only one need hold.

  Clause 17(2)(c) -- two persons present suffice where there is more
  than one owner and the quorum otherwise calculated would come to
  fewer than two -- is NOT applied here. It depends on comparing a
  computed threshold against a headcount floor, and this actor does not
  do that. The consequence is stated rather than hidden: in a very
  small scheme this may report a quorum unmet where the clause would
  have supplied one. It errs toward HOLD, which is the direction this
  catalog errs everywhere else it knows only part of a rule."
  {:mode :any
   :article "Strata Schemes Management Act 2015 (NSW), Schedule 1 clause 17(2)"
   :disjuncts [{:axis :owners :fraction one-quarter :comparison :at-least}
               {:axis :unit-entitlement :fraction one-quarter :comparison :at-least}]})

(def nsw-resolutions
  "Transcribed from the current text of the Strata Schemes Management
  Act 2015 (NSW), section 5, on legislation.nsw.gov.au.

  New South Wales sets its thresholds on the OPPOSITION. s 5(1): a
  resolution is special if, `of the value of votes cast`, `not more
  than 25% are against` -- and s 5(3): unanimous if `no vote is cast
  against`. Neither sentence mentions the votes in favour. Restating
  them as `at least 75% for` is algebra that holds only while every
  vote cast is either for or against, which is an assumption about the
  ballot and not something the statute says, so these are measured
  where the statute measures them.

  Two subject-matter variants relax the cap rather than the base: a
  sustainability infrastructure resolution and an accessibility
  infrastructure resolution each pass while `less than 50%` are
  against -- note LESS THAN, not `not more than`, so exactly half fails
  where exactly a quarter would have passed.

  == What is deliberately NOT here ==

  The ORDINARY resolution. s 5 carries a Note saying a motion not
  requiring a special or unanimous resolution `is passed by a simple
  majority of votes (see clause 14 of Schedule 1)`. A Note is not the
  operative provision, and clause 14 of Schedule 1 was not read. So
  there is no `:ordinary` entry and `resolution-rule` returns nil for
  it: the governor holds, exactly as it does for a jurisdiction with no
  table at all. A partially read statute yields a partial table, not a
  guessed one.

  == Vote values are taken as given ==

  s 5(2) sets the value of a vote for a lot to its unit entitlement,
  and s 5(2A) REDUCES an original owner`s vote value by two thirds when
  their entitlement is at least half the aggregate and the scheme has
  more than two lots. That reduction is a fact about who cast which
  vote, so this actor takes the already-valued tallies it is given --
  the same posture as Singapore s 2(8) valid-vote definition."
  {:special
   {:label "Special resolution"
    :article "Strata Schemes Management Act 2015 (NSW), section 5(1)(b)(i)"
    :base :cast :quorum nsw-quorum
    :axes [{:axis :unit-entitlement :base :cast
            :fraction one-quarter :comparison :opposition-at-most}]
    :fraction one-quarter :comparison :opposition-at-most
    :bylaw :none
    :note "反対が投票価値の25%を超えないこと。賛成票は条文に現れない。値は unit entitlement (s 5(2))。"}

   :special-sustainability-infrastructure
   {:label "Special resolution -- sustainability infrastructure"
    :article "Strata Schemes Management Act 2015 (NSW), section 5(1)(b)(ii)"
    :base :cast :quorum nsw-quorum
    :axes [{:axis :unit-entitlement :base :cast
            :fraction half :comparison :opposition-less-than}]
    :fraction half :comparison :opposition-less-than
    :bylaw :none
    :note "`less than 50%` —— ちょうど50%は否決。s 5(1)(b)(i) の `not more than 25%` とは境界の向きが違う。"}

   :special-accessibility-infrastructure
   {:label "Special resolution -- accessibility infrastructure"
    :article "Strata Schemes Management Act 2015 (NSW), section 5(1)(b)(iii)"
    :base :cast :quorum nsw-quorum
    :axes [{:axis :unit-entitlement :base :cast
            :fraction half :comparison :opposition-less-than}]
    :fraction half :comparison :opposition-less-than
    :bylaw :none}

   :ordinary
   {:label "Ordinary motion -- majority in number, no poll"
    :article "Strata Schemes Management Act 2015 (NSW), Schedule 1 clause 14(1)"
    :base :cast :quorum nsw-quorum
    :fraction nil :comparison :more-than-opposed
    :axes [{:axis :owners :base :cast :comparison :more-than-opposed}]
    :bylaw :none
    :note "`a majority in number of the votes cast for and against`、1 lot につき 1 票。分母は無く、同数は否決 —— SGP s 2(2)(b)(i) と同じ形。"}

   :ordinary-poll
   {:label "Ordinary motion -- on a poll, by value"
    :article "Strata Schemes Management Act 2015 (NSW), Schedule 1 clause 14(3)"
    :base :cast :quorum nsw-quorum
    :fraction nil :comparison :more-than-opposed
    :axes [{:axis :unit-entitlement :base :cast :comparison :more-than-opposed}]
    :bylaw :none
    :note "poll が請求されると価値 (unit entitlement) で数え直す。cl 14(4): poll は多数決の直前・直後どちらでも請求でき、請求は撤回できる —— つまり同じ議案の可否が投票後に変わりうる。"}

   :unanimous
   {:label "Unanimous resolution"
    :article "Strata Schemes Management Act 2015 (NSW), section 5(3)"
    :base :cast :quorum nsw-quorum
    :axes [{:axis :unit-entitlement :base :cast
            :fraction none :comparison :opposition-at-most}]
    :fraction none :comparison :opposition-at-most
    :bylaw :none
    :note "`no vote is cast against`。全員賛成ではなく、反対が1票も無いこと —— 棄権は妨げない。"}})

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

   "DEU"
   {:name "Germany"
    :owner-authority "Bundesministerium der Justiz (BMJ)"
    :legal-basis "Wohnungseigentumsgesetz (WEG), in der Fassung des WEMoG 2020"
    :national-spec "WEG § 19 Absatz 2 Nummer 4 -- Ansammlung einer angemessenen Erhaltungsrücklage"
    :provenance "https://www.gesetze-im-internet.de/woeigg/"
    :verified-on "2026-09-06"
    :resolutions deu-resolutions
    :required-evidence ["Gemeinschaftsordnung / Teilungserklärung"
                        "Erhaltungsrücklage -- Kontostand und Vermögensbericht (§ 28 Absatz 4)"
                        "Wirtschaftsplan und Jahresabrechnung"
                        "Protokoll der Eigentümerversammlung"
                        "Sachverständigen-Kostenschätzung"]
    :notes ["Es gibt keine gesetzliche Mindesthöhe der Erhaltungsrücklage und keinen amtlichen Richtwert je Quadratmeter -- deshalb trägt dieser Eintrag KEINE :reserve-guideline. Die Zahlen im japanischen Eintrag gelten dort und nirgendwo sonst."
            "§ 22: Ist das Gebäude zu mehr als der Hälfte seines Wertes zerstört und der Schaden nicht gedeckt, kann der Wiederaufbau nicht beschlossen werden -- ein Verbot, keine Mehrheit."]}

   "FRA"
   {:name "France"
    :owner-authority "Ministère chargé du logement / Agence nationale de l'habitat (Anah)"
    :legal-basis "Loi n° 65-557 du 10 juillet 1965 fixant le statut de la copropriété des immeubles bâtis"
    :national-spec "Fonds de travaux obligatoire, article 14-2 de la loi n° 65-557 (alimenté par une cotisation annuelle)"
    :provenance "https://www.legifrance.gouv.fr/loda/id/LEGITEXT000006068256/"
    :verified-on "2026-09-06"
    :resolutions fra-resolutions
    :required-evidence ["Règlement de copropriété et état descriptif de division"
                        "Projet de plan pluriannuel de travaux"
                        "Compte du fonds de travaux (article 14-2)"
                        "Procès-verbal de l'assemblée générale"
                        "Devis et notice descriptive du maître d'oeuvre"]
    :notes ["La loi impose un fonds de travaux, mais elle ne publie pas de barème de cotisation au mètre carré comparable au barème japonais -- cet enregistrement ne porte donc PAS de :reserve-guideline."]}

   "ESP"
   {:name "Spain"
    :owner-authority "Ministerio de Vivienda y Agenda Urbana"
    :legal-basis "Ley 49/1960, de 21 de julio, sobre propiedad horizontal"
    :national-spec "Artículo 9.1.f) -- fondo de reserva, dotado con al menos el 10 por ciento del último presupuesto ordinario"
    :provenance "https://www.boe.es/buscar/act.php?id=BOE-A-1960-10906"
    :verified-on "2026-09-06"
    :resolutions esp-resolutions
    :deemed-consent {:article "Ley 49/1960 sobre propiedad horizontal, artículo 17.8"
                     :effect :absent-owners-counted-in-favour
                     :requires ["citación debidamente practicada"
                                "comunicación del acuerdo conforme al artículo 9"
                                "transcurso de 30 días naturales sin discrepancia"]
                     :auto-applied? false
                     :note "この actor は自動適用しない。招集の適法性と30日の経過は世界についての事実であって、算術ではない。"}
    :required-evidence ["Título constitutivo y estatutos de la comunidad"
                        "Acta de la Junta de propietarios"
                        "Cuentas del fondo de reserva (artículo 9.1.f)"
                        "Presupuesto ordinario aprobado"
                        "Informe técnico y presupuesto de las obras"]
    :notes ["El fondo de reserva legal se define como un porcentaje del presupuesto ordinario, no como una cuota por metro cuadrado: este registro no lleva :reserve-guideline."]}

   "USA-NY"
   {:name "United States -- New York (exemplar; no national condominium statute)"
    :owner-authority "New York Department of State / county recording officer"
    :legal-basis "New York Real Property Law Article 9-B (Condominium Act)"
    :provenance "https://dos.ny.gov/"
    :verified-on nil
    :unverified-reason :no-statutory-threshold-table
    :notes ["Condominium law is state law -- there is no federal condominium statute; New York is an exemplar, not a national authority."
            "Voting thresholds for a common-element alteration or a special assessment are fixed by the declaration and bylaws of the individual condominium, not by a single statutory fraction. This is NOT a gap this actor can close by reading harder: there is no national table to read. The governor holds any resolution proposal for this jurisdiction."]
    :required-evidence ["Declaration and bylaws"
                        "Reserve study"
                        "Reserve-fund account statement"
                        "Minutes of the board / unit-owner meeting"
                        "Engineer's scope and cost opinion"]}

   "GBR"
   {:name "United Kingdom (England and Wales)"
    :owner-authority "Ministry of Housing, Communities and Local Government / First-tier Tribunal (Property Chamber)"
    :legal-basis "Landlord and Tenant Act 1985 s.20 and s.20ZA; Commonhold and Leasehold Reform Act 2002"
    :provenance "https://www.legislation.gov.uk/ukpga/1985/70"
    :verified-on nil
    :unverified-reason :no-unit-owner-vote
    :notes ["Most blocks of flats are leasehold, not commonhold: major works are funded through a service charge and consulted on under s.20, NOT resolved by a unit-owner supermajority. The Japanese/continental resolution model does not transfer, so this catalog carries NO :resolutions table."
            "s.20 consultation is a procedural precondition to recovering more than the statutory contribution limit -- it belongs in :required-evidence, and the governor treats it as evidence, not as a vote."]
    :required-evidence ["Lease / service charge schedule"
                        "s.20 notice of intention and observations"
                        "s.20 notice of estimates"
                        "Service charge account and reserve (sinking) fund statement"
                        "Surveyor's specification and cost estimate"]}

   ;; ---- Statutory thresholds EXIST, but this actor could not read them ----
   ;;
   ;; These four are a different kind of absence from USA-NY and GBR.
   ;; There, the thresholds genuinely are not in a statute. Here they
   ;; are -- and the primary source refused the request (HTTP 403) or
   ;; returned a navigation frame rather than the article text on
   ;; 2026-09-06. `:legal-basis-unverified` is deliberately a DIFFERENT
   ;; key from `:legal-basis`, so no reader can mistake a pointer to
   ;; check for something that was checked.

   "SGP"
   {:name "Singapore"
    :owner-authority "Building and Construction Authority / Strata Titles Boards"
    :legal-basis "Building (Strata Management) Act 2004"
    :provenance "https://sso.agc.gov.sg/Act/BSMA2004"
    :verified-on "2026-09-06"
    :resolutions sgp-resolutions
    :required-evidence ["Strata title plan and by-laws"
                        "Management fund and sinking fund accounts"
                        "Notice of the general meeting specifying the motion"
                        "Minutes of the general meeting"
                        "Quantity surveyor's estimate"]
    :notes ["The act's current short title is `Building (Strata Management) Act 2004` and its SSO identifier is BSMA2004. An earlier revision of this catalog carried the older name and the identifier BMSMA2004, and every fetch of it returned HTTP 200 with a Page Not Found body -- recorded here because the failure looked exactly like a success."
            "s 2(8) defines a valid vote negatively (a vote given both for and against, unmarked, or void for uncertainty is not valid). Deciding which ballot papers are valid is a fact about the papers, so the valid counts are taken as given."
            "There is no statutory per-square-metre sinking-fund benchmark comparable to the Japanese one, so this entry carries no :reserve-guideline."]}

   "AUS-NSW"
   {:name "Australia -- New South Wales (exemplar; strata law is per-state)"
    :owner-authority "NSW Fair Trading / NSW Civil and Administrative Tribunal"
    :legal-basis "Strata Schemes Management Act 2015 (NSW), section 5"
    :provenance "https://legislation.nsw.gov.au/view/whole/html/inforce/current/act-2015-050"
    :verified-on "2026-09-06"
    :resolutions nsw-resolutions
    :required-evidence ["Strata by-laws"
                        "Capital works fund plan (10-year)"
                        "Capital works fund account"
                        "Minutes of the general meeting"
                        "Quantity surveyor estimate"]
    :notes ["Read in two passes. s 5 (special / unanimous) first; then Schedule 1 clauses 14 and 17 (the ordinary motion and the quorum) during a later window when the site answered again. The table was partial in between, and said so -- a partially read statute yields a partial table, not a guessed one."
            "Access to this source is INTERMITTENT. legislation.nsw.gov.au answers curl with a Cloudflare interstitial, answered a plain urllib GET with the full text on 2026-09-06, and refused an identical request minutes later. Nothing was done to defeat the challenge: the readable response arrived without one. `scripts/hermes-kumiai-sources` re-probes it, and the rule stands that no entry may be moved on a body obtained by defeating a challenge."
            "s 5(2A) reduces an original owner vote value by two thirds in the stated circumstances, and Schedule 1 clause 14(2)/(3) applies the same calculation to elections and polls. Applying the reduction is the caller job; this actor takes the valued tallies as given."
            "Schedule 1 clause 17(2)(c) -- two persons present suffice in a very small scheme -- is not applied. See `nsw-quorum` for what that costs and which way it errs."
            "Schedule 1 clause 14(4): a poll may be demanded immediately before OR AFTER a vote decided by number, and the demand may be withdrawn. The same motion therefore has two lawful counts (:ordinary and :ordinary-poll) and which one governs is a fact about the meeting, not about the motion."
            "There is no statutory per-square-metre capital-works benchmark comparable to the Japanese one, so this entry carries no :reserve-guideline."]}

   "ITA"
   {:name "Italy"
    :owner-authority "Ministero della Giustizia"
    :legal-basis-unverified "Codice civile, articolo 1136 (costituzione dell'assemblea e validità delle deliberazioni)"
    :provenance "https://www.normattiva.it/"
    :verified-on nil
    :unverified-reason :source-unreachable
    :source-attempt {:on "2026-09-06" :status 200
                     :note "normattiva.it は 200 を返すが本文ではなく目次フレームだった。条文本体を読めていない。200 は「読めた」を意味しない。"}
    :required-evidence ["Regolamento di condominio e tabelle millesimali"
                        "Fondo speciale per le opere (art. 1135)"
                        "Rendiconto condominiale"
                        "Verbale dell'assemblea"
                        "Preventivo del tecnico"]}

   "CHN"
   {:name "China"
    :owner-authority "住房和城乡建设部"
    :legal-basis-unverified "中华人民共和国民法典 第二百七十八条"
    :provenance "http://www.npc.gov.cn/"
    :verified-on nil
    :unverified-reason :source-unreachable
    :source-attempt {:on "2026-09-06" :status 200
                     :note "npc.gov.cn は 200 を返すがニュース面が返り条文が含まれていなかった。"}
    :required-evidence ["管理规约"
                        "住宅专项维修资金账户"
                        "业主大会决议"
                        "工程预算书"]}})

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
  "Honest coverage report, at THREE levels -- because the difference
  between them is the whole point.

    :covered
      jurisdictions with an official spec-basis at all.

    :with-statutory-resolution-thresholds
      of those, the ones whose thresholds are in a STATUTE and were
      transcribed from that statute's current text. Only these can have
      a resolution judged.

    :without-statutory-resolution-thresholds
      the rest, split by WHY, because the two reasons are not the same
      and must not be reported as one:

        :no-statutory-threshold-table / :no-unit-owner-vote
          -- there is nothing to read. USA-NY leaves thresholds to each
             condominium's declaration; England and Wales fund major
             works through a leasehold service charge with no
             unit-owner vote at all. Reading harder will not close
             these.
        :source-unreachable
          -- there IS a statute and this actor could not read it. The
             attempt is recorded in `:source-attempt` with the date and
             the HTTP status, including the ones that answered 200 with
             something other than the text. UNVERIFIED is not the same
             as ABSENT, and neither is the same as CHECKED.
        :source-behind-bot-challenge
          -- there IS a statute, and the obstacle is a bot-detection
             interstitial. Kept separate from the line above because
             this one is not a matter of trying harder: defeating it is
             something this workspace does not do, so the entry stays
             unverified until an official API or another distribution
             of the text exists. Naming the reason is the difference
             between a gap and a decision.

  Reporting only the first number would make this actor look several
  times as capable as it is."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)
         with-rules (filter #(seq (get-in catalog [% :resolutions])) have)
         without (remove (set with-rules) have)
         by-reason (reduce (fn [m j] (update m (get-in catalog [j :unverified-reason] :unstated)
                                             (fnil conj []) j))
                           {} without)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :with-statutory-resolution-thresholds (vec (sort with-rules))
      :without-statutory-resolution-thresholds (vec (sort without))
      :without-thresholds-by-reason (into {} (map (fn [[k v]] [k (vec (sort v))]) by-reason))
      :unreadable-sources (vec (sort (concat (get by-reason :source-unreachable [])
                                             (get by-reason :source-behind-bot-challenge []))))
      :note (str "cloud-itonami-isic-6820 kumiai: " (count catalog)
                 " jurisdictions seeded with an official spec-basis, of which "
                 (count with-rules)
                 " carry a statutory resolution-threshold table transcribed from the "
                 "current text. Of the rest, "
                 (count (concat (get by-reason :source-unreachable [])
                                (get by-reason :source-behind-bot-challenge [])))
                 " HAVE a statutory table that this actor could not read (see "
                 ":source-attempt for the date and status) and "
                 (count (concat (get by-reason :no-statutory-threshold-table [])
                                (get by-reason :no-unit-owner-vote [])))
                 " have no national table to read at all. The governor HOLDS a "
                 "resolution proposal in every case where :resolutions is absent, "
                 "whichever reason applies -- an unverified threshold must never "
                 "answer like a verified one. Extend `realty.kumiai.facts/catalog` "
                 "by citing a real source; never invent a jurisdiction's thresholds "
                 "to make coverage look bigger.")})))

(defn jurisdiction-summary
  "One-line human summary, used by the demo/console. Says WHY a
  jurisdiction cannot be judged, not merely that it cannot."
  [iso3]
  (if-let [b (spec-basis iso3)]
    (str iso3 " -- " (:name b) " / " (or (:legal-basis b) (:legal-basis-unverified b))
         (cond
           (seq (:resolutions b))
           (str " [決議要件 " (count (:resolutions b)) " 種、検証日 " (:verified-on b) "]")

           (= :source-unreachable (:unverified-reason b))
           (str " [法定要件は存在するが一次資料を取得できていない -- "
                (:on (:source-attempt b)) " / HTTP " (:status (:source-attempt b)) "]")

           (= :source-behind-bot-challenge (:unverified-reason b))
           (str " [法定要件は存在するが、一次資料が bot 検出の背後にある -- 回避しない ("
                (:on (:source-attempt b)) " / HTTP " (:status (:source-attempt b)) ")]")

           (= :no-unit-owner-vote (:unverified-reason b))
           " [区分所有者の決議という制度自体が無い (leasehold + s.20 consultation)]"

           :else
           " [全国一律の法定要件テーブルが存在しない -- 規約/宣言に依る]"))
    (str iso3 " -- NO SPEC-BASIS")))

(defn unverified-reason [iso3]
  (get-in catalog [iso3 :unverified-reason]))

(defn deemed-consent
  "A jurisdiction's rule for counting silent absentees, or nil. Never
  applied automatically -- see the ESP entry's own note."
  [iso3]
  (get-in catalog [iso3 :deemed-consent]))

(defn relaxation-conditions
  "The statutory conditions that let a rebuilding-class resolution drop
  to a lower fraction, or nil."
  [iso3 kind]
  (get-in (resolution-rule iso3 kind) [:relaxed :conditions]))

(defn describe-fraction [{:keys [numer denom]}]
  (when (and numer denom) (str numer "/" denom)))

(defn- axis-summary [rule a]
  (let [m (if (keyword? a) {:axis a} a)
        f (or (:fraction m) (:fraction rule))
        cmp (or (:comparison m) (:comparison rule))
        base (or (:base m) (:base rule))]
    (str (name (:axis m)) "@" (name base) " " (describe-fraction f)
         (if (= :greater-than cmp) "超" "以上"))))

(defn rule-summary
  "A rule in one line. Prints fraction, comparison AND base PER AXIS,
  because under WEG § 21 Absatz 2 and loi 65-557 article 26 those
  differ between the axes of a single rule -- a summary that printed
  one fraction for the rule would be describing a rule that does not
  exist."
  [rule]
  (when rule
    (str (:label rule) " -- " (:article rule) " / "
         (str/join " + " (map #(axis-summary rule %) (:axes rule)))
         (when (:quorum rule) " (定足数あり)")
         (when (:fallback rule) (str " (" (:article (:fallback rule)) " の再決議あり)")))))
