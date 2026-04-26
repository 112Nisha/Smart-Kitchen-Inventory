# ADR-005: Use an Explicit Ingredient Lifecycle Model with State-Based Behavior

## Status

Accepted

## Context

Ingredients do not remain equally usable throughout their lifetime. The system must distinguish between ingredients that are fresh, near expiry, expired, or discarded because those conditions affect multiple business behaviors, including:

- whether an ingredient can be recommended in a dish
- whether it should trigger expiry-related notifications
- how it should appear in operational views
- when it should effectively leave active inventory workflows

The team needed a lifecycle model that would keep these rules consistent across the system.

The main alternatives considered were:

- treat lifecycle as a simple data value and re-implement the related rules wherever needed
- use one central set of conditional checks in services
- make lifecycle an explicit domain concept with state-based behavior

Scattering lifecycle logic across services would increase duplication and make it harder to keep recommendation, alerting, and inventory behavior aligned. A more explicit lifecycle model provides a clearer domain vocabulary and a more maintainable place for behavior that depends on ingredient condition. In this ADR, "lifecycle" means the system-level condition of an ingredient as it moves from usable stock toward expiry, discard, or removal from active workflows.

## Decision

Use an explicit ingredient lifecycle model and implement lifecycle-dependent behavior through state-based logic.

The lifecycle currently includes:

- Fresh
- Near Expiry
- Expired
- Discarded

This decision treats lifecycle as a first-class domain concept rather than only a display label. The architecture uses lifecycle-aware behavior to govern:

- recommendation eligibility
- expiry-alert triggering
- inventory visibility and operational handling

In the current design, this lifecycle model is realized through explicit state objects together with lifecycle transition handling.

## Consequences

Positive:

- lifecycle-dependent behavior is governed by a clearer shared domain model
- recommendation, alerting, and inventory workflows can rely on the same lifecycle vocabulary
- the design reduces duplication of lifecycle rules across services
- future refinement of lifecycle-specific behavior is more structured

Negative:

- the model introduces more moving parts than a simpler enum-only approach
- lifecycle state and transition handling must remain consistent across the system
- some operational policies still require service-level coordination in addition to the lifecycle model itself
- the design improves clarity, but it does not eliminate the need for careful transition logic
