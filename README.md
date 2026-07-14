# cloud-itonami-isco-3334

Open Occupation Blueprint for **ISCO-08 3334**: Real Estate Agents and Property Managers.

This repository designs a forkable OSS business for an independent real estate and property management practice: a property-condition documentation robot manages listing and tenancy records under a governor-gated actor, so the practice keeps its own property records instead of renting a closed real-estate SaaS.

**Maturity: `:implemented`.** `src/realestate/` implements the
`RealEstateActor` as a `langgraph.graph/state-graph` (`realestate.actor`)
wired to a `Real Estate Advisor` (`realestate.advisor`) and an
independent `RealEstateGovernor` (`realestate.governor`), following the
itonami actor pattern (ADR-2607011000): `:intake -> :advise -> :govern ->
:decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. 14 tests / 29 assertions
green (`clojure -M:test`). HARD invariants (always hold, never
overridable): client provenance, no-actuation (`:effect` must be
`:propose`), a registered listing basis for any lease-execution
proposal, the proposed execution amount not exceeding the listing's
registered authorization ceiling (executing beyond it is unauthorized
disposition, not diligent management), and completed tenant screening
before any lease can be executed (offering a lease without one is an
unscreened placement, not efficient service). Always-escalate ops
(human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:approve-over-authorization-execution` and
`:approve-security-deposit-disbursement`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a property-condition documentation robot performs walkthrough photographing, condition-report assembly and physical filing under an actor that proposes
actions and an independent **Real Estate Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
lease/sale execution above the owner's registered authorization ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
property listing + tenant/buyer request + management agreement
        |
        v
Real Estate Advisor -> Real Estate Governor -> list/manage property, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3334`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
