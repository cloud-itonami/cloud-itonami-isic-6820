# ADR-0001: cloud-itonami-isic-6820 -- Realty-Fee-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530` ADR-0001s (the pattern this ADR ports; `6512`'s and `6530`'s
  ADRs establish the "write the lesson down, don't just fix it"
  discipline this build reapplies proactively before a bug could
  occur), ADR-2607032000 (`cloud-itonami` insurance (ISIC 65/66) +
  real-estate (ISIC 68) coverage push -- the blueprint scaffold this
  ADR deepens, and the LAST remaining candidate from its original
  8-repo batch), langgraph-clj ADR-0001 (Pregel superstep + interrupt +
  Datomic checkpoint)
- Context: `cloud-itonami-isic-6820` published a business/operator-model
  blueprint (ADR-2607032000's coverage push) but stopped at `:blueprint`
  maturity -- no governed actor implementation. This ADR deepens it to
  `:implemented`, the FIRST actor in this fleet outside the insurance
  division (65/66) -- ISIC division 68 (real estate) -- continuing the
  SAME "pick a new ISIC blueprint vertical" direction that produced
  `6512`/`6621`/`6622`/`6629`/`6520`/`6530`, and closing out the entire
  original 8-repo batch.

## Problem

Real-estate fee services bundle two genuinely distinct real-world acts
under one governed workflow:

1. **Jurisdiction property-management/trust-account disclosure
   correctness** -- is the required evidence for paying a management
   fee based on an official regulator, or invented?
2. **Fee arithmetic correctness** -- does a claimed management fee
   actually match the property's own fixed-percentage-of-rent formula?
   Structurally the same "never trust a claimed number, independently
   re-derive it" discipline `cloud-itonami-isic-6629`'s/`6520`'s checks
   established, expressed as an EXACT-MATCH (like those two) rather
   than an upper-bound cap (unlike `cloud-itonami-isic-6530`'s) -- a
   fixed-percentage fee has exactly one correct value.
3. **Contract-authorization correctness** -- does a pending vendor/
   maintenance contract's value exceed the property owner's own
   pre-authorized spending limit? Structurally like `casualty.
   governor/claim-exceeds-coverage-violations` (a cap against a STORED
   CONSTANT, not a computed one) -- genuinely different in kind from
   check 2's exact-match against a COMPUTED formula, in the SAME
   governor.
4. **Real actuation, twice, with genuinely different lifecycle
   shapes** -- paying a real management fee (a TWO-step lifecycle,
   `:fee/file` then `:fee/pay`, mirroring `casualty`'s/`pension`'s
   claim/disbursement shape) and executing a real contract on the
   owner's behalf (a ONE-step lifecycle, `:contract/execute` acting
   directly on a property's inline pending contract, mirroring
   `reinsurance`'s `:treaty/bind` shape) -- both irreversible acts an
   owner will rely on.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run property-management fee services with an
LLM" but "seal the LLM inside a trust boundary and layer evidence-
sufficiency, fee-arithmetic correctness, contract-authorization
correctness, audit and human-approval on top of it, while structurally
fixing both real actuation events as human-only."

## Decision

### 1. Realty-Fee-LLM is sealed into the bottom node; it never pays or executes directly

`realty.realtyfeellm` returns exactly five kinds of proposal: intake
normalization, jurisdiction disclosure/trust-account checklist, fee-
filing normalization, fee-payment draft, and contract-execution draft.
No proposal writes the SSoT or commits a real fee payment / contract
execution directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 real-estate-fee-services operation

`realty.operation/build` is the SAME StateGraph shape as every sibling
actor's operation namespace, copied verbatim.

### 3. A pending contract lives INLINE on the property, not in a separate collection -- and clearing it IS the double-execution guard

Like `pension.store`'s member folding the party role, `realty.store`
has no separate `contract` collection: a property has at most ONE
contract pending execution at a time, carried as `:pending-contract` on
the property record itself. `realty.store/execute-contract!` CLEARS
`:pending-contract` to nil on execution -- so a repeat execution
attempt naturally falls into the SAME `contract-missing-violations`
check a never-had-a-pending-contract property would also trigger. This
is a genuinely different guard MECHANISM from every sibling's separate
double-payment/double-booking check: one accurate check covers BOTH
"never had one" and "already consumed," rather than two checks for two
distinct facts, because both really are the SAME underlying fact (no
contract currently pending).

### 4. `fee-calculation-mismatch-violations` reuses the EXACT-MATCH independent-recompute pattern; `contract-exceeds-authorization-violations` reuses the STATIC-CAP pattern -- BOTH in one governor

`realty.registry/compute-management-fee` independently recomputes the
management fee (a fixed percentage of collected rent) and the governor
compares this recompute against the fee's OWN claimed amount --
reusing `cloud-itonami-isic-6629`'s/`6520`'s exact-match discipline.
Separately, `contract-exceeds-authorization-violations` compares a
pending contract's value against the property's own STATIC `:contract-
authorization-limit` -- reusing `casualty.governor/claim-exceeds-
coverage-violations`'s cap-against-a-constant shape. No sibling actor
combines BOTH check shapes (computed exact-match AND static cap) in
the SAME governor; `cloud-itonami-isic-6530`'s `disbursement-exceeds-
entitlement-violations` blended them into ONE check (a computed cap),
while this actor keeps them as two SEPARATE checks on two separate ops,
because the two questions are genuinely independent here (fee math vs.
contract authorization), not two facets of the same entitlement.

### 5. `property-not-under-management-violations` checks `:status :under-management` directly, safely -- reusing `6520`'s/`6530`'s reasoning, applied to a FOURTH lifecycle

Like `reinsurance.governor/treaty-not-bound-violations` and `pension.
governor/member-not-in-payout-violations`, a property's status never
regresses out of `:under-management` once entered (there is no further
status transition analogous to `6622`'s placement advancing past
`:bound`), so checking `:status` directly here is safe -- the same
reasoning already written down twice, reapplied here without needing
to rediscover it by a failing demo.

### 6. `spec-basis-violations` proactively guards on entity-existence from the FIRST draft -- applying `6530`'s lesson before it could recur

`cloud-itonami-isic-6530`'s ADR records a real bug: `spec-basis-
violations` scoped to an op that ALSO carries a "missing entity" check,
without guarding on existence first, spuriously co-fires the wrong rule
when the entity doesn't exist (empty `:cites` for the wrong reason).
This actor's `:fee/pay` op has the SAME shape (spec-basis check +
fee-missing check, both on `:fee/pay`) -- so `spec-basis-violations`
guards on `(store/fee st subject)` existing FIRST, from the very first
draft, not discovered by a failing demo. `:contract/execute`
deliberately does NOT carry a spec-basis check at all (see Decision 7),
sidestepping the overlap risk entirely on that op.

### 7. `:contract/execute` has no spec-basis check -- an honest domain distinction, not an inconsistency

Unlike `:fee/pay` (jurisdiction-specific disbursement/withholding rules
genuinely apply), `:contract/execute`'s compliance concern is purely
CONTRACTUAL (does a contract exist, is it within the owner's pre-
authorized limit) -- not a jurisdiction-citation matter. Forcing a
spec-basis check onto `:contract/execute` for symmetry with `:fee/pay`
would misrepresent what actually governs contract execution.

### 8. A real bug was caught during BUILD, not demo verification -- a data-modeling bug, a new kind for this fleet

The FIRST version of `realty.store`'s Datomic `property->tx` wrote
`:property/pending-contract` UNCONDITIONALLY on every property patch
(using `true` as the `cond->` trigger, needed because a legitimate
value IS `nil`, meaning 'no contract pending' -- truthiness alone can't
distinguish 'clear to nil' from 'field absent'). This would have
silently CLOBBERED an existing pending contract to nil on ANY partial
upsert that never even mentions `:pending-contract` (e.g. a bare
`{:status :ready}` patch) -- caught by re-reading the diff before
running anything, not by a failing test or a misleading demo output
(unlike `6512`'s/`6622`'s/`6530`'s bugs, all caught by demo/ledger
inspection). Fixed by checking `(contains? m :pending-contract)`
instead of truthiness, matching `MemStore`'s `merge` semantics (which
already correctly leaves absent keys untouched).

### 9. A second bug WAS caught during demo verification -- a sim-authoring bug, not a governance bug

The sim's first `:property/intake` step copied `casualty.sim`'s
`:patch {:id "pol-1" :status :ready}` pattern verbatim -- but unlike
`casualty` (where policies start at `:intake` and a LATER `:policy/
bind` step sets `:status :bound`), THIS actor's properties are already
seeded at `:under-management` with no separate property-binding
actuation at all. The patch clobbered `property-1`'s status to
`:ready`, which then correctly HARD-held every subsequent `:fee/file`
attempt against it (`property-not-under-management`) -- the GOVERNOR
was right to reject an invalid state; the SIM told the wrong story.
Fixed by patching a harmless already-true field (`:owner`) instead of
`:status`, which this domain's intake step has no legitimate reason to
touch. This is documented as a distinct lesson from `6530`'s (an
audit-trail-accuracy bug in governor logic): a demo-authoring mistake
that borrowed a lifecycle assumption (intake advances status toward a
later binding step) that does not hold in every domain.

### 10. Real actuation is structurally always human-only (enforced by two independent layers)

`realty.governor`'s `high-stakes` set has two members (`:actuation/
pay-fee` and `:actuation/execute-contract`, matching `6512`'s/`6622`'s/
`6520`'s/`6530`'s dual-actuation shape, not `6511`'s/`6621`'s/`6629`'s
single-actuation one), and `realty.phase`'s phase table never puts
`:fee/pay`/`:contract/execute` in any phase's `:auto` set.

### 11. No fabricated international fee/contract-number standard

Same discipline as every sibling's registry: there is no single
international check-digit standard for a fee-payment or contract-
execution reference number. `realty.registry` therefore does not invent
one; it validates required fields and assigns a jurisdiction-scoped
sequence number only.

### 12. Relationship to `kotoba-lang/property`

Unlike the insurance-adjacent actors' relationship to `kotoba-lang/
insurance`, this is this fleet's FIRST actor whose capability lib is
`kotoba-lang/property` (parcel/listing/lease contracts) -- but the same
self-contained-sibling posture holds: no code dependency.

## Consequences

- (+) Real-estate fee services gets the same governed, auditable-actor
  treatment as the six insurance-adjacent actors, extending the
  pattern successfully to a genuinely different ISIC division for the
  first time -- any licensed property-management firm can fork and run
  their own instance.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/realty/phase_test.clj`'s `fee-pay-never-
  auto-at-any-phase` / `contract-execute-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by
  `test/realty/store_contract_test.clj`, the same `:db-api`-driven
  swap pattern every sibling actor uses, including a dedicated
  assertion that a partial upsert preserves an untouched pending
  contract (regression-proofing Decision 8's fix).
- (+) Combining an exact-match arithmetic check AND a static-cap check
  in ONE governor (Decision 4) is a genuine structural contribution --
  proven by dedicated demo scenarios for both, plus a double-execution
  scenario proving the single-check guard mechanism (Decision 3) works
  as designed.
- (+) `spec-basis-violations`' proactive existence-guard (Decision 6)
  demonstrates a lesson applied BEFORE it could recur, not after --
  the demo and full test suite passed clean on the FIRST attempt for
  this specific concern.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA-NY, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `realty.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `compute-management-fee` models only a fixed-percentage-of-rent
  formula, not a full management agreement's real-world terms (tiered
  fee schedules, leasing/renewal commissions, maintenance markup are
  out of scope -- see that fn's own docstring); appraisal/valuation
  methodology, tenant-servicing coordination workflows, and real
  trust-account/banking integration are all out of scope for this OSS
  actor -- each operator's responsibility (see README's coverage
  table).
- 39 tests / 187 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Keep `cloud-itonami-isic-6820` at `:blueprint` only | ❌ | Leaves the LAST candidate from the original 8-repo batch without an `:implemented` reference actor |
| Model contracts as a separate collection, for consistency with fee's separate `fees` collection | ❌ | A property has at most one contract pending execution at a time; a separate collection would be an unused indirection, the same "no premature abstraction" judgment `6629`'s/`pension`'s ADRs already made for their own core entity shapes |
| Add an explicit double-execution guard, defensively matching every other actuation op's separate guard | ❌ | Clearing `:pending-contract` on execution already makes a repeat attempt fall into the accurate `contract-missing` check -- adding a SEPARATE guard for the same underlying fact would be redundant, not more correct |
| Blend fee-calculation and contract-authorization into ONE check, matching `6530`'s blended cap-and-recompute shape | ❌ | The two questions are genuinely independent here (fee math applies to `:fee/pay`, authorization applies to `:contract/execute`) -- forcing them into one check would misrepresent two distinct real-world concerns as one |
| Give `:contract/execute` its own spec-basis check, for symmetry with `:fee/pay` | ❌ | Contract execution's compliance concern is contractual (contract exists, within authorization), not jurisdiction-citation -- an honest domain distinction, not an oversight |
| Require `kotoba.property` (the capability lib) directly from `realty.*` | ❌ | No sibling actor requires its capability lib directly; keeping the actor self-contained matches the established pattern |
