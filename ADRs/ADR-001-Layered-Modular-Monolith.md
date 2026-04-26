# ADR-001: Use a Layered Modular Monolith as the Initial System Architecture

## Status

Accepted

## Context

The Smart Kitchen Inventory system needs to support several closely related capabilities: inventory tracking, expiry monitoring, notifications, dish recommendations, shopping-list generation, and tenant-scoped restaurant data. For the prototype, these capabilities are implemented inside a single Java web application, but the code is already organized into separate layers and subsystems rather than one undifferentiated codebase.

The architecture needed to satisfy two goals at once:

- provide a structure that is maintainable and extensible beyond the prototype
- remain practical to implement and integrate within the project constraints

The team needed an initial architecture that:

- keeps subsystem boundaries clear
- supports shared business logic and shared data consistency
- avoids unnecessary operational complexity during early development
- can evolve later if scale or deployment needs increase

## Decision

Use a layered modular monolith as the initial system architecture.

The system is deployed as a single application, but internally it is organized into distinct layers and modules, including:

- web layer for request handling and page flow
- service layer for business logic
- repository layer for persistence concerns
- state and model layers for domain behavior and data representation

This decision treats the current architecture as an intentional starting point for an evolvable system:

- one deployable unit for the prototype
- explicit internal subsystem boundaries
- shared in-process coordination for inventory-driven workflows
- future option to extract or externalize selected responsibilities if justified later

## Consequences

Positive:

- the architecture is simpler to implement and integrate for an initial prototype
- subsystem boundaries are still visible in the codebase, which improves maintainability
- inventory, notifications, shopping lists, and recommendations can coordinate through shared domain logic without distributed-system overhead
- the design provides a clearer path for future refinement than an ad hoc single-layer application
- the current structure can be strengthened over time without requiring an immediate architectural rewrite

Negative:

- the whole system is still deployed as one unit
- internal module boundaries are architectural conventions inside one codebase, not independent deployment boundaries
- selective scaling of individual subsystems is limited in the current implementation
- future growth would require stronger boundary enforcement before extracting modules into separate deployables
