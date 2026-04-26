# ADR-009: Reject Chain of Responsibility for Expiry Alert Routing in the Current System

## Status

Rejected

## Context

During early architectural thinking, the team considered using a Chain of Responsibility structure for expiry-alert handling. The proposal described a staged alert pipeline in which different handlers would evaluate expiry conditions, determine urgency, and route or escalate notifications step by step.

This approach was appealing because it could:

- separate alert decisions into smaller units
- make routing logic extensible
- support a more elaborate escalation flow if different alert paths were required

However, the team also needed to evaluate whether that additional structure was justified by the actual notification requirements of the implemented system.

The main alternatives considered were:

- use a Chain of Responsibility pipeline for expiry-alert routing
- use a simpler centralized notifier once an ingredient is determined to be alertable

## Decision

Do not use Chain of Responsibility for expiry-alert routing in the current system.

Instead, once an ingredient has been classified as alertable, the system uses a simpler notification path in which role-appropriate CHEF and MANAGER notifications are generated directly through a centralized notifier.

The team rejected Chain of Responsibility for the current system because the implemented requirements do not depend on a true multi-step escalation flow. Both stakeholder roles need awareness of the same operational event, and the current routing logic does not require a long sequence of independently variable handlers.

## Consequences

Positive:

- the expiry-alert path remains simpler and easier to understand
- notification routing is aligned with the current requirement that both CHEF and MANAGER receive awareness of alertable items
- the system avoids adding indirection that does not currently provide proportional value
- the implemented notification flow is easier to test and reason about for the current scope

Negative:

- the current design is less structured for future multi-stage escalation behavior
- introducing richer prioritization or conditional escalation later may require revisiting this decision
- some extensibility that a handler chain could provide is intentionally deferred in favor of simpler routing
