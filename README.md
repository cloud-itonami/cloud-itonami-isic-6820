# cloud-itonami-6820

Open Business Blueprint for **ISIC Rev.5 6820**: Real estate activities on a fee or contract basis.

This repository designs a forkable OSS business for real estate activities performed for others on a fee or contract basis -- property management, appraisal and consultancy for property owners who retain title (contrast with cloud-itonami-L6810's own/leased-property agency) -- run by a qualified, licensed operator so a community or
independent professional never surrenders customer data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a property-condition inspection robot documents managed-property condition for the human manager or appraiser,
under an actor that proposes actions and an independent **Real-Estate Fee-Services Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + case/policy records
        |
        v
Realty-Fee-LLM -> Real-Estate Fee-Services Governor -> hold, proceed, or human approval
        |
        v
case/policy ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: disbursing a fee or executing a contract on the client's behalf.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6820`). Required capabilities are implemented by:

- [`kotoba-lang/property`](https://github.com/kotoba-lang/property)
  -- parcel, listing and lease contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`Realty-Fee-LLM` + `Real-Estate Fee-Services Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.
