# Operator Quickstart: Real estate activities on a fee or contract basis

## Prerequisites

1. **Clojure CLI** (`clojure` ≥ 1.11.0). [Install here](https://clojure.org/guides/install_clojure).
2. **If running inside the monorepo**: sibling paths in `deps.edn` resolve `langgraph-clj` and `langchain-clj` via `:local/root`. For a standalone fork, override them with Git coordinates.
3. **A text editor** for reading `src/realty/*.cljc` as you explore.

## Run the demo

Walk through two clean lifecycles (property intake → fee payment and contract execution) and seven HARD-hold cases:

```bash
clojure -M:dev:run
```

The demo driver (`src/realty/sim.cljc`) shows the OperationActor, the Real-Estate Fee-Services Governor, and how high-stakes actions (`:fee/pay`, `:contract/execute`) are never autonomous.

## Run tests

Verify governor contract, phase invariants, store parity, registry conformance, and facts coverage:

```bash
clojure -M:dev:test
```

Key test modules:
- `test/realty/governor_test.clj` — 7 HARD checks: spec-basis, evidence completeness, property-under-management, fee presence, fee-calculation mismatch, contract presence, contract authorization
- `test/realty/phase_test.clj` — Phase 0→3 invariants; `:fee/pay` and `:contract/execute` never auto-eligible at any phase
- `test/realty/registry_test.clj` — Fee-payment/contract-execution draft record shape and conformance
- `test/realty/store_test.clj` — MemStore and DatomicStore parity

## Lint

Check for static-analysis errors (fails CI if any):

```bash
clojure -M:lint
```

## Governor location

The **Real-Estate Fee-Services Governor** sits at:

```
src/realty/governor.cljc
```

Key gates (all HARD, non-overrideable):
- Spec-basis citation (jurisdiction disclosure/trust-account catalog)
- Evidence completeness (no fabricated citations)
- Property-under-management check (fee filed only for active properties)
- Fee-calculation mismatch (independent EXACT recompute)
- Contract-execution gates (contract exists, value ≤ owner authorization)
- Double-payment guard (checked against payment history)

See the Governor's docstring and `test/realty/governor_test.clj:fee-pay-never-auto-at-any-phase` / `contract-execute-never-auto-at-any-phase` for proof that payment and contract execution never bypass human approval.

## Architecture overview

| File | Role |
|---|---|
| `src/realty/store.cljc` | Store protocol (MemStore, DatomicStore); append-only audit ledger; no separate party concept |
| `src/realty/registry.cljc` | Fee and contract draft records; simplified fixed-percentage-of-rent formula |
| `src/realty/facts.cljc` | Jurisdiction-specific property-management disclosure/trust-account catalog with official spec-basis citations |
| `src/realty/realtyfeellm.cljc` | Realty-Fee-LLM Advisor (mock or real); intake/assessment/filing/payment/contract proposals |
| `src/realty/governor.cljc` | Independent verification layer; 7 HARD checks + double-payment guard |
| `src/realty/phase.cljc` | Phase table (0→3): read-only → assisted intake → assisted assess → supervised |
| `src/realty/operation.cljc` | OperationActor (langgraph-clj StateGraph) |
| `src/realty/sim.cljc` | Demo driver |

## Jurisdiction coverage

Current catalog (`src/realty/facts.cljc`): JPN, USA-NY, GBR, DEU (4/~194 worldwide). All entries cite official sources; never fabricated.

Adding a jurisdiction:
1. Add one map entry to `realty.facts/catalog` with official spec-basis reference
2. Add test case to `test/realty/facts_test.clj`
3. Run tests and lint

## Business model & operations

- **Customer**: licensed property-management firms, HOA managers, independent appraisers
- **Offer**: property intake, fee billing, contract execution, audit ledger
- **Trust controls**: Human sign-off for all disbursements, spec-basis citation required, HARD holds for violations

See `docs/business-model.md` for revenue model, and `docs/operator-guide.md` for first-deployment and certification requirements.

## Certification

Operators must prove:
- Case/policy-record integrity
- Governor independence
- Evidence-backed reporting
- Human review for every high-stakes action

See `docs/operator-guide.md` for minimum production controls.

## Next steps

1. **Read the README** (`../README.md`) for full architecture and context.
2. **Read the ADR** (`docs/adr/0001-architecture.md`) for design decisions.
3. **Run the demo**: `clojure -M:dev:run`
4. **Explore the Governor**: `src/realty/governor.cljc` and its tests
5. **Fork and adapt**: Override the fee-rate formula, extend the jurisdiction catalog, add robotics integrations

---

Built on [langgraph-clj](https://github.com/com-junkawasaki/langgraph-clj) StateGraph runtime. Designed alongside [cloud-itonami-isic-6511](https://github.com/cloud-itonami/cloud-itonami-isic-6511) (life insurance), [cloud-itonami-isic-6512](https://github.com/cloud-itonami/cloud-itonami-isic-6512) (non-life insurance), and siblings in the insurance-adjacent fleet.

License: AGPL-3.0-or-later
