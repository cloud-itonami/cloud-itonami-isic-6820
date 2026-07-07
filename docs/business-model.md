# Business Model: Real estate activities on a fee or contract basis

## Classification

- Repository: `cloud-itonami-isic-6820`
- ISIC Rev.5: `6820`
- Activity: real estate activities performed for others on a fee or contract basis -- property management, appraisal and consultancy for property owners who retain title (contrast with cloud-itonami-L6810's own/leased-property agency)
- Social impact: financial inclusion, data sovereignty, transparent audit

## Customer

- property-management firms
- independent appraisers
- real-estate consultancies
- HOA and co-op managers

## Offer

- managed-property intake
- appraisal/valuation proposal
- maintenance and tenant-servicing coordination
- fee/contract billing
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per managed unit
- support: monthly retainer with SLA
- migration: import from an incumbent property-management system
- per-appraisal fee

## Trust Controls

- no fee is disbursed and no contract is executed on the client's
  behalf without human sign-off
- a fabricated jurisdiction disclosure/trust-account citation,
  unsupported evidence, a fee filed for a property not under
  management, a claimed fee amount that does not match this vehicle's
  own independent recompute, a contract execution attempt with no
  pending contract on file, or a contract whose value exceeds the
  owner's own pre-authorized limit -- each forces a hold, not an
  override
- a fee cannot be paid twice: a double-payment attempt is held off this
  actor's own payment history alone, with no upstream comparison needed
- every intake, assessment, filing, payment and contract-execution path
  is auditable
- emergency manual override paths remain outside LLM control
