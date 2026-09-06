# cloud-itonami-isic-6820

Open Business Blueprint for **ISIC Rev.5 6820**: Real estate activities
on a fee or contract basis. This repository publishes a property-
management fee-services actor -- managed-property intake, management-
fee payment and vendor/maintenance-contract execution on a property
owner's behalf -- as an OSS business that any qualified, licensed
property-management firm can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
[`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511)
(life insurance), [`cloud-itonami-isic-6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512)
(non-life insurance), [`cloud-itonami-isic-6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621)
(independent loss adjustment), [`cloud-itonami-isic-6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622)
(insurance intermediation), [`cloud-itonami-isic-6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629)
(insurance auxiliary services), [`cloud-itonami-isic-6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520)
(reinsurance) and [`cloud-itonami-isic-6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530)
(pension funding) -- the first of this fleet OUTSIDE the insurance
division (65/66). Here it is **Realty-Fee-LLM ⊣ Real-Estate Fee-
Services Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a fee-
> disclosure summary, normalizing property intake, and running the
> management-fee arithmetic -- but it has **no notion of which
> jurisdiction's property-management/trust-account requirements are
> official, no license to pay a real management fee or execute a real
> contract on an owner's behalf, and no way to know on its own whether
> a claimed fee actually matches the property's own fee-rate formula,
> or whether a contract's value exceeds the owner's pre-authorized
> limit**. Letting it pay a fee or execute a contract directly invites
> fabricated jurisdiction citations, silently-wrong fee math an owner
> would actually rely on, and unauthorized contractual commitments made
> in the owner's name -- and liability for whoever runs it. This
> project seals the Realty-Fee-LLM into a single node and wraps it with
> an independent **Real-Estate Fee-Services Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers managed-property intake through fee payment, and
contract-execution on an owner's behalf, gated by a pre-authorized
spending limit. It does **not**, by itself, hold a license to manage
property in any jurisdiction, and it does not claim to. It also does
**not** model a full management agreement's real-world terms -- no
tiered fee schedules, no leasing/renewal commissions, no maintenance
markup (see `realty.registry/compute-management-fee`'s own docstring
for the honest simplification this makes: a fixed percentage of
collected rent, not a full fee schedule). Whoever deploys and operates
a live instance (a licensed property-management firm) supplies the
jurisdiction-specific license, the real appraisal/consultancy expertise
and the real trust-account/banking integrations, and bears that
jurisdiction's liability -- the software supplies the governed, spec-
cited, audited execution scaffold so that operator does not have to
build the compliance layer from scratch for every new market.

### Actuation

**Paying a real management fee and executing a real contract on the
property owner's behalf are never autonomous, at any phase, by
construction.** Two independent layers enforce this (`realty.
governor`'s `:actuation/pay-fee`/`:actuation/execute-contract` high-
stakes gate and `realty.phase`'s phase table, which never puts `:fee/
pay`/`:contract/execute` in any phase's `:auto` set) -- see `realty.
phase`'s docstring and `test/realty/phase_test.clj`'s `fee-pay-never-
auto-at-any-phase`/`contract-execute-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human property manager is
always the one who actually pays a fee or executes a contract.

## The core contract

```
property intake + jurisdiction facts (realty.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Realty-Fee-  │ ─────────────▶ │ Real-Estate Fee-Services  │  (independent system)
   │ LLM (sealed) │  + citations    │ Governor: spec-basis ·    │
   └──────────────┘                 │ evidence-incomplete ·     │
                             commit ◀────┼──────────▶ hold │ not-under-management ·
                                 │             │           │ fee-missing · fee-mismatch
                           record + ledger  escalate ─▶ human   (independent recompute) ·
                                             (ALWAYS for         contract-missing ·
                                              :fee/pay /         contract-exceeds-
                                              :contract/execute)  authorization ·
                                                                  double-payment
```

**The Realty-Fee-LLM never pays a fee or executes a contract the Real-
Estate Fee-Services Governor would reject, and never does so without a
human sign-off.** Hard violations (fabricated jurisdiction
requirements; unsupported disclosure/trust-account evidence; a fee
filed against a property not under management; a fee amount that
doesn't match this vehicle's own independent recompute; a contract
execution attempt with no pending contract on file; a contract whose
value exceeds the owner's own pre-authorized limit; a double payment)
force **hold** and *cannot* be approved past; a clean payment/execution
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run          # fee-services actor: two clean lifecycles + seven HARD-hold cases
clojure -M:dev:run-kumiai   # 管理組合 actor: reserve projection under escalation/slippage, a general-meeting resolution, a works order, and eleven HARD-hold cases
clojure -M:dev:test         # governor contract · phase invariants · store parity · registry conformance · facts coverage (both actors)
clojure -M:lint             # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a property-condition
inspection robot documents managed-property condition for the human
manager or appraiser, under the actor, gated by the independent
**Real-Estate Fee-Services Governor**. The governor never dispatches
hardware itself; `:high`/`:safety-critical` actions require human
sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Real-Estate Fee-Services Governor, fee-payment + contract-execution draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6820`). Related capability contracts (parcel/listing/lease shapes) are
published as [`kotoba-lang/property`](https://github.com/kotoba-lang/property);
this actor's `realty.*` namespaces are a self-contained governed
implementation -- it does not require the capability lib directly, the
same "self-contained sibling" relationship the insurance-adjacent
actors have toward `kotoba-lang/insurance`.

## Layout

| File | Role |
|---|---|
| `src/realty/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + fee-payment/contract-execution history. No separate party concept -- a pending contract lives INLINE on the property record, and executing it clears the field (doubling as the double-execution guard) |
| `src/realty/registry.cljc` | Fee-payment + contract-execution draft records, plus `compute-management-fee` (REAL, simplified fixed-percentage-of-rent formula -- see docstring for what it does not model) |
| `src/realty/facts.cljc` | Per-jurisdiction property-management disclosure/trust-account catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/realty/realtyfeellm.cljc` | **Realty-Fee-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/filing/payment/contract-execution proposals |
| `src/realty/governor.cljc` | **Real-Estate Fee-Services Governor** -- 7 HARD checks (spec-basis · evidence-incomplete · property-not-under-management · fee-missing · fee-calculation-mismatch, independent EXACT-match recompute · contract-missing · contract-exceeds-authorization, static cap) + double-payment guard + 1 soft (confidence/actuation gate) |
| `src/realty/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (payment/execution always human; property intake + fee filing auto-eligible, no capital risk) |
| `src/realty/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/realty/observation.cljc` | **Observation contract** (`fee-observation/1`) -- provenance-preserving observations of PUBLISHED property-management/fee-disclosure requirements over official sources; separate from the actor's own drafts |
| `src/realty/sim.cljc` | demo driver |
| `src/realty/kumiai/facts.cljc` | **管理組合** per-jurisdiction catalog: statutory resolution thresholds (JPN only, transcribed from the current 区分所有法 text) + the MLIT reserve-fund guideline values, with two-level honest coverage reporting |
| `src/realty/kumiai/resolution.cljc` | Exact vote arithmetic: statutory denominator (attending ‖ total), per-axis thresholds, quorum stage, bylaw overrides, 第38条の2 exclusions, boundary flagging |
| `src/realty/kumiai/reserve.cljc` | Long-term repair plan projection under cost escalation and schedule slippage, deficit + `:unmeasured-outflow`, required-contribution solver, MLIT `Z` / benchmark band / staged-increase verdict |
| `src/realty/kumiai/registry.cljc` | Resolution-minute + works-order draft records (unsigned) |
| `src/realty/kumiai/store.cljc` | **Store** protocol for associations/plans/resolutions -- `MemStore` ‖ `DatomicStore`, pending works INLINE (double-commissioning guard) |
| `src/realty/kumiai/kumiaillm.cljc` | **Kumiai-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor` |
| `src/realty/kumiai/governor.cljc` | **Condominium-Association Governor** -- 12 HARD checks + 1 soft gate |
| `src/realty/kumiai/phase.cljc` | **Phase 0→3** -- works commissioning and resolution filing never auto-commit at any phase |
| `src/realty/kumiai/operation.cljc` | **OperationActor** (kumiai) -- langgraph-clj StateGraph |
| `src/realty/kumiai/sim.cljc` | 管理組合 demo driver |
| `test/realty/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage · observation contract |

## The observation contract (`fee-observation/1`)

`realty.observation` is the actor's **observation layer**: how a reading of
an OFFICIAL source (a jurisdiction's property-management / real-estate
regulator, a government portal) becomes a provenance-preserving,
re-observable claim about the **published** property-management
requirements and fee-disclosure facts this actor's `realty.facts` catalog
seeds -- and what such a claim can never become. It is deliberately
SEPARATE from `realty.registry` (the actor's own fee-payment and
contract-execution drafts): drafts are what this actor prepares under a
human gate; observations are what external official sources publish. An
observation is never a fee payment, never a contract execution, and never
feeds back into a payment or execution decision.

One contract, twelve parts: **source receipts** (frozen, content-hash
addressed, id derived from hash + observed-at; an edited receipt is
refused, not re-branded) · **typed subject + events** (one subject: a
jurisdiction's PUBLISHED requirement set, keyed exactly as `realty.facts`
keys it — national `JPN`/`GBR`/`DEU` and sub-national exemplar `USA-NY`
alike — under a declared scope that may never be re-scoped later; typed
publication acts only (requirement revised, fee schedule republished,
trust-account rule amended, CMP scheme membership changed, disclosure form
reissued), so a rent change or a listing is not an observable event here;
party data and addresses are refused BY CONSTRUCTION) · **measurement
window** (every observation states `{:from :to}`, events must be asserted
inside it) · **currency basis** (a disclosed-fee figure carries ISO-4217
currency + its own nominal date + the verbatim raw transcription; nothing
is normalized, converted or combined) · **method / version**
(`fee-observation/1` on every artifact; no model anywhere) ·
**missingness / coverage** (closed flag vocabulary; a jurisdiction without
a `realty.facts` spec-basis must carry `:jurisdiction-spec-basis-absent`,
a republished fee schedule with no fee figure must carry
`:fee-schedule-unavailable` — silence would claim completeness) ·
**derived observations** (`window-observation` and `coverage-observation`:
COUNTS and verbatim publication references only — never a fee, a trend, a
score or a market measure) · **refresh history** (append-only lineage via
`:obs/refresh-of`, cross-subject links refused at append time;
`refresh-delta` carries added / removed / changed figures and events IN
FULL on both sides plus gap movement and both generations' receipt ids,
and computes no numeric difference anywhere) · **Hyakka proposal**
(`hyakka-proposal` builds the claim SHAPE for the `fudosan` corpus —
receipts, verbatim values, bases, gaps, the scope's epistemic and privacy
boundaries, `:no-model true`; prop names are contract-local and flagged
unregistered; this contract transmits nothing anywhere) · **query /
readback** (`readback` revalidates everything it returns and refuses
tampered receipts; a miss is a miss, never a default; `readback-chain`
walks the full lineage oldest-first, refusing truncated, cyclic or
cross-subject chains) · **history discipline** (duplicate ids refused; a
national key is not a sub-national exemplar — re-scoping a jurisdiction
key is refused) · **refusals** (49 loud `:refusal/code` failures instead
of quiet degradation).

WHAT THIS CONTRACT NEVER PRODUCES: a valuation, a market score, a ranking
of properties / neighbourhoods / jurisdictions / managers, an eligibility
conclusion about any person (published requirement conditions are
observations, never adjudications), a fee that anyone should pay, or
investment advice of any kind. Published fee schedules and requirement
texts are observations of what a source disclosed — not what any
management fee should be, not an offer, and not an endorsement of any
manager or scheme.

Deterministic contract tests: `test/realty/observation_test.clj` -- 35
tests over synthetic fixtures only (marked as such; the receipt URLs are
the catalog's own provenance citations; no network, no I/O, no model).

## 管理組合 extension -- condominium owners' associations

The actor above serves a management FIRM acting for a property OWNER on
a fee basis. `realty.kumiai.*` serves the other party in the same ISIC
class: the **owners' association** (管理組合) itself -- the body that
resolves a major-repair works order at a general meeting and pays for
it out of a reserve fund it has been accumulating for decades.

They are different legal actors under different statutes, so they get
separate catalogs, separate governors and separate stores rather than
one blurred table. `clojure -M:dev:run-kumiai` walks the whole thing.

### The question it answers

A long-term repair plan is drawn at some base year against costs quoted
at that base year. Two forces move it, in **opposite** directions:

- **cost escalation** compounds every future works item from the base
  year to the year it is actually built;
- **schedule slippage** pushes the work later, which buys more months
  of contributions -- and more compounding.

So deferring a major repair helps or hurts depending on whether the
contribution rate outruns escalation over the deferral, and the
crossover is not intuitive. `realty.kumiai.reserve/sensitivity` prints
the grid a board actually has to decide against. On the demo
association (dimensioned as the MLIT guideline's own worked example --
70 units, 4,900㎡, 30-year plan) the same plan and the same
contributions give:

```
上昇率 0% / 遅延 0年 -> 充足
上昇率 5% / 遅延 0年 -> 不足 409,733,675円
上昇率 5% / 遅延 2年 -> 不足 282,004,785円 [未計上 259,316,543円]
```

Read the third line carefully: **delaying the works made the projected
deficit smaller.** The year-28 item slipped past the thirty-year
horizon and left the projection entirely. There is a row in the demo
grid (`0% / 2年`) that reports `不足 0円` -- fully funded -- while
60,000,000円 of work has silently fallen off the end.

That is why `shortfall` reports `:unmeasured-outflow` separately and
refuses `:funded?` while it is non-zero, and why the governor holds on
it. A cost that was not measured must not read the same as a cost that
was measured and covered.

### What the governor knows that the advisor cannot

Twelve HARD checks (no human can approve past them) and a soft gate.
The two that carry the most weight:

**The denominator is part of the law, and it changed six months ago.**
The 令和7年改正区分所有法 came into force on 2026-04-01. 第39条第1項 --
the ordinary resolution a major-repair works order runs on -- now counts
`出席した区分所有者及びその議決権の各過半数`: the **attending** base,
not the total membership it used to count. 第17条第1項 / 第31条第1項 /
第61条第5項 are two-stage (a quorum, then a supermajority of the
attending). 第62条第1項 stays on the **total** base at 4/5, dropping to
3/4 only for the five conditions of 第62条第2項. Within one
jurisdiction the denominator differs per resolution kind.

The demo files the same ballot twice -- 30 of 70 owners in favour, 44
attending. On the statutory (attending) base it passes; on the total
base it fails. Both computations are arithmetically impeccable, and
nothing in the proposal reveals which one was used. Only the recompute
in `realty.kumiai.governor` finds it.

**An unverified threshold must not answer like a verified one.**
`realty.kumiai.facts/coverage` reports **two** numbers, not one: four
jurisdictions have an official spec-basis, and exactly **one** of them
carries a statutory resolution-threshold table checked against the
current text. In New York the thresholds live in the individual
condominium's declaration and bylaws; in England and Wales the tenure
is usually leasehold and there is no unit-owner vote at all (major
works are consulted on under s.20, not resolved). Those catalog entries
carry **no** `:resolutions` table, `resolution-rule` returns `nil`, and
the governor **holds** -- it never falls back to a plausible majority.
Reporting only the first number would make this actor look four times
as capable as it is.

The other HARD checks: association not under management · plan below
the guideline's own sample preconditions (a 15-year plan with one
repair cycle cannot be compared to a band derived from 30-year plans
with two) · claimed deficit that does not survive an independent
re-projection · works slipped past the horizon · no committed
projection on file · works with no pending package or no passed
resolution (doubling as the double-commissioning guard) · an order
above the budget the meeting actually voted · a reserve that
demonstrably cannot fund the works with no borrowing or levy resolved
to cover it · incomplete jurisdiction evidence.

Soft (escalate, never hold): low confidence · a vote that landed
**exactly** on its threshold, where one proxy form flips the result ·
a reserve level outside the guideline's published band. That last one
is deliberately not HARD: the guideline states in terms that being
outside the band does not by itself make the level improper, and
holding on it would put this actor's opinion above the guideline's own
words.

### What it does not do

It does not decide which escalation rate is right. A board that re-runs
the projection at a rosier rate and adopts that can clear the funding
check -- the demo shows exactly this happening. The guard is not that
the assumptions are correct; it is that they are **on the record**,
under the board's name, in an append-only ledger, next to the works
order they authorised. Choosing 0% escalation in 2026 remains the
board's decision. It just stops being an invisible one.

It also does not hold a license, manage anyone's money, or replace a
マンション管理士 / 建築士. Commissioning works is `:actuation/
commission-works` and never auto-commits at any phase -- two
independent layers (`realty.kumiai.phase` and the governor's
high-stakes gate) agree on that.

### Sources

Every legal and numeric value is transcribed from a primary source and
carries its citation in the catalog, not from recollection:

- 建物の区分所有等に関する法律 (昭和37年法律第69号) -- current text via
  the e-Gov 法令検索 API, `https://laws.e-gov.go.jp/law/337AC0000000069`.
  Verified 2026-09-06.
- 国土交通省「マンションの修繕積立金に関するガイドライン」
  平成23年4月策定 / **令和6年6月改定** --
  `https://www.mlit.go.jp/jutakukentiku/house/content/001747009.pdf`.
  The benchmark bands, the mechanical-parking unit costs, the `Z`
  formula, the plan-horizon preconditions and the staged-increase rule
  (`0.6 × D ≤ E` かつ `1.1 × D ≥ F`, added in the 令和6年6月 revision)
  all come from that document. `reserve_test.clj` reproduces the
  guideline's own worked example -- Z ≒ 241 円/㎡・月, parking add ≒ 36,
  band 206〜356 -- so the transcription is regression-tested, not just
  the arithmetic.

Jurisdictions other than Japan carry `:verified-on nil` and say in
their own `:notes` what has not been checked. Extending coverage is
additive: one map entry citing a real source. Never invent a
jurisdiction's thresholds to make coverage look bigger.

## Business-process coverage (honest)

This actor covers managed-property intake through fee payment, and
contract execution on the owner's behalf -- the core governed lifecycle
this blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Property intake + per-jurisdiction disclosure/trust-account checklisting, HARD-gated on an official spec-basis citation (`:property/intake`/`:jurisdiction/assess`) | Tiered fee schedules, leasing/renewal commissions, maintenance markup (see `compute-management-fee`'s docstring) |
| Fee filing against a property under management (`:fee/file`, HARD-gated on the property actually being under management) | Real transfer-agent/banking/trust-account integration, tax/regulatory reporting |
| Fee payment, independently re-verified against this vehicle's OWN fee-rate recompute, with a double-payment guard (`:fee/pay`) | Appraisal/valuation methodology itself (this R0 does not model a valuation formula) |
| Contract execution against a property's pending vendor/maintenance contract, HARD-gated on the value not exceeding the owner's pre-authorized limit (`:contract/execute`) | Tenant-servicing coordination workflows, HOA/co-op-specific governance |
| Immutable audit ledger for every intake/assessment/filing/payment/execution decision | |

Extending coverage is additive: add the next gate (e.g. an appraisal/
valuation op) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`realty.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `realty.facts/catalog` --
currently 4 seeded (JPN, USA-NY, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `realty.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `Realty-Fee-LLM` + `Real-Estate Fee-Services
Governor` run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, modeled closely on the
sibling `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
`6530`'s architecture. See `docs/adr/0001-architecture.md` for the
history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
