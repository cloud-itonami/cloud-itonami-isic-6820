(ns realty.facts
  "Per-jurisdiction property-management/fee-disclosure regulatory
  catalog -- the G2-style spec-basis table the Real-Estate Fee-Services
  Governor checks every jurisdiction/assess proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's
  property-management licensing/trust-account/fee-disclosure
  requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official property/
  real-estate regulator (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a real
  source, done -- never invent a jurisdiction's requirements to make
  coverage look bigger.

  Like the insurance-adjacent siblings' `USA-NY`/`USA` split (insurance
  is per-state; pension/ERISA is federal), property-management/real-
  estate-broker licensing in the US is ALSO per-state (no federal real-
  estate license) -- so this catalog's US entry is `USA-NY`, an
  exemplar, not a national authority, matching `casualty.facts`'s/
  `reinsurance.facts`'s posture rather than `pension.facts`'s.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  management-agreement/trust-account/fee-disclosure evidence set
  submitted in some form; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省 (Ministry of Land, Infrastructure, Transport and Tourism)"
          :legal-basis "宅地建物取引業法 + 賃貸住宅管理業法 (Real Estate Brokerage Act + Rental Housing Management Business Act, 2020)"
          :national-spec "国土交通省 賃貸住宅管理業者登録制度 実施要領"
          :provenance "https://www.mlit.go.jp/"
          :required-evidence ["管理委託契約書 (management agreement)"
                              "手数料開示書面 (fee disclosure statement)"
                              "管理受託収入に係る帳簿 (trust/client account reconciliation)"
                              "業務保証(保険)証明書 (professional indemnity insurance certificate)"]}
   "USA-NY" {:name "United States -- New York (exemplar; federalism note below)"
             :owner-authority "New York Department of State -- Division of Licensing Services"
             :legal-basis "New York Real Property Law Article 12-A (Real Estate Brokers and Salespersons)"
             :national-spec "NY DOS trust-account / escrow-handling rules for managing agents"
             :provenance "https://dos.ny.gov/professional-licensing"
             :notes "No federal real-estate broker/property-manager license -- licensing and trust-account rules are regulated per-state; New York is an exemplar, not a national authority."
             :required-evidence ["Management agreement"
                                 "Fee disclosure statement"
                                 "Trust/escrow account reconciliation"
                                 "Professional indemnity insurance certificate"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Department for Levelling Up, Housing and Communities (DLUHC)"
          :legal-basis "The Client Money Protection Schemes for Property Agents (Approval and Designation of Schemes) Regulations 2019"
          :national-spec "RICS Service Charge Residential Management Code / Client Money Protection scheme rules"
          :provenance "https://www.gov.uk/government/organisations/department-for-levelling-up-housing-and-communities"
          :required-evidence ["Management agreement"
                              "Fee disclosure statement"
                              "Client money protection / trust account reconciliation"
                              "Professional indemnity insurance certificate"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesministerium für Wirtschaft und Klimaschutz (BMWK)"
          :legal-basis "Gewerbeordnung (GewO) §34c i.V.m. Makler- und Bauträgerverordnung (MaBV)"
          :national-spec "MaBV Vermögensbetreuungspflichten (trust-account/client-money obligations)"
          :provenance "https://www.bmwk.de/"
          :required-evidence ["Verwaltervertrag (management agreement)"
                              "Gebührenoffenlegung (fee disclosure statement)"
                              "Treuhandkonto-Abstimmung (trust/client account reconciliation)"
                              "Vermögensschaden-Haftpflichtversicherung (professional indemnity insurance certificate)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to pay a fee on
  it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6820 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `realty.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
