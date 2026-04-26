# ADR-007: Use In-Process Event-Driven Coordination for Inventory-Triggered Side Effects

## Status

Accepted

## Context

Several parts of the system need to react when inventory changes. These reactions include expiry evaluation, low-stock notification, shopping-list refresh behavior, notification cleanup, and related operational side effects.

The team needed a coordination mechanism that would:

- avoid tightly coupling every inventory mutation flow to every dependent subsystem
- allow multiple subsystems to respond to the same inventory event
- remain simple enough for a single-application prototype
- preserve fast in-process response for inventory-driven workflows

The main alternatives considered were:

- invoke every dependent subsystem directly from each mutation flow
- use in-process event/listener coordination
- introduce an external message broker or asynchronous event infrastructure

Direct calls would tightly couple inventory operations to every downstream concern. An external broker would add infrastructure and operational complexity that was unnecessary for the current architecture. In-process event-driven coordination provides a middle ground that preserves loose coupling without requiring distributed messaging.

## Decision

Use in-process event-driven coordination for inventory-triggered side effects.

In this design:

- inventory changes publish application-level events
- dependent services subscribe as listeners or observers
- event handling remains inside the same application process
- inventory-triggered reactions are coordinated through event dispatch rather than feature-specific direct wiring everywhere

## Consequences

Positive:

- downstream reactions to inventory changes are less tightly coupled to the initiating workflow
- multiple subsystems can respond to the same operational event
- new event-driven behavior can be added more cleanly than with repeated direct calls
- the design fits the modular monolith architecture without introducing distributed messaging overhead

Negative:

- event-driven control flow is less linear than direct method calls
- debugging requires understanding which listeners are attached to a given event source
- synchronous in-process listeners still share failure and runtime characteristics with the main application
- event contracts must remain stable enough for dependent subsystems to stay aligned
