# ADR-006: Use a Notification Service with Pluggable Delivery Strategies, Durable In-Application Storage, and Retry Handling

## Status

Accepted

## Context

The system produces several kinds of notifications, including expiry alerts, low-stock warnings, and role-specific operational updates. These notifications are part of the application's day-to-day behavior, so the team needed a notification design that would remain useful even when optional external delivery channels are unavailable.

The design needed to support:

- reliable in-application visibility of notifications
- optional external delivery without making the whole feature dependent on it
- a clear place to manage delivery behavior
- resilience to transient delivery failures

The main alternatives considered were:

- send notifications through one hard-coded mechanism only
- embed notification delivery logic directly in each feature that emits alerts
- centralize notification sending behind a notification service with multiple delivery strategies

A single hard-coded mechanism would reduce flexibility. Embedding delivery logic separately in expiry, low-stock, and other feature flows would duplicate behavior and make future changes harder. A centralized notification service with pluggable delivery strategies gives a cleaner architectural boundary while allowing the system to support more than one delivery path.

## Decision

Use a centralized notification service with pluggable delivery strategies, durable in-application notification storage, and retry handling.

This means:

- notification sending is coordinated through one notification service
- delivery mechanisms are treated as interchangeable strategies
- durable in-application storage is the primary built-in notification path
- external email delivery is optional and configuration-driven
- transient send failures are retried rather than failing immediately on the first attempt

This decision separates notification generation from notification delivery, while ensuring that the system retains a dependable in-application notification channel.

## Consequences

Positive:

- notification behavior is easier to extend without rewriting every alert-producing feature
- the system retains a reliable in-application notification path even when external delivery is unavailable
- durable storage allows notifications to survive restarts and remain visible to users
- retry handling improves resilience for transient delivery failures
- delivery concerns are centralized rather than duplicated across multiple features

Negative:

- notification delivery becomes more complex than a single direct send
- multiple delivery paths require careful coordination to avoid overlap or duplication
- role-targeted delivery depends on the surrounding role and user model remaining coherent
- notification configuration and permissions still require disciplined governance as the system grows
