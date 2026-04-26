# ADR-002: Use Restaurant-Scoped Multi-Tenancy

## Status

Accepted

## Context

The Smart Kitchen Inventory system is intended to support multiple restaurants while keeping each restaurant's operational data logically separate. Inventory records, recipes, notifications, shopping-list state, and user context all belong to a specific restaurant and should be handled within that restaurant's scope.

This decision matters because the system must balance two goals:

- support more than one restaurant within the same application
- preserve clear data boundaries for day-to-day operations

The team considered the following approaches:

- use a single global data model with no explicit restaurant partitioning
- deploy a completely separate application instance per restaurant
- use a shared application with restaurant-scoped tenancy

A global shared model would simplify some queries, but it would blur ownership boundaries and complicate role-based operational workflows. A separate deployment per restaurant would provide strong isolation, but it would add operational overhead that is unnecessary for the current system scope.

## Decision

Use restaurant-scoped multi-tenancy as the architectural model.

Each restaurant is treated as a tenant, and operational data is scoped accordingly. The architecture assumes that:

- each user session operates within one restaurant context
- restaurant context is carried through the application's operational workflows
- tenant-scoped data is used for inventory, recipes, notifications, and shopping-list behavior
- caching and background processing are organized around restaurant boundaries rather than one global shared inventory

This choice provides a practical middle ground between a single undifferentiated system and fully separate per-restaurant deployments.

## Consequences

Positive:

- the architecture supports multiple restaurants within one application
- restaurant boundaries are treated as a first-class design concept
- operational workflows such as alerts, recommendations, and shopping lists can remain restaurant-specific
- tenant-scoped caching and data access align naturally with the system's domain model
- the design provides a foundation for future strengthening of authorization and scaling concerns

Negative:

- tenant identity becomes a cross-cutting architectural concern that must be handled consistently
- data access, caching, and user context must all remain aligned with the same restaurant scope
- future expansion to more advanced multi-restaurant administration would require additional tenancy and authorization design
