# ADR-003: Centralize Inventory Coordination in a Singleton InventoryManager

## Status

Accepted

## Context

Inventory is the core operational concern of the system. Multiple features depend on the same ingredient data and on consistent reactions to inventory changes, including:

- inventory display and mutation
- expiry evaluation
- low-stock detection
- shopping-list generation
- dish recommendation
- notification and cleanup workflows

Because these features all depend on the same operational state, the team needed to decide how inventory coordination should be structured.

The main alternatives considered were:

- allow each servlet or feature-specific service to manage inventory access independently
- create separate inventory-handling components for each subsystem
- centralize inventory coordination in a shared application-level service

Distributing inventory operations across multiple components would reduce central coupling, but it would also duplicate business rules, complicate cache invalidation, and make it harder to guarantee consistent reactions when ingredient data changes. A shared coordination point offers stronger control over how inventory state is read, updated, cached, and propagated to dependent subsystems.

## Decision

Centralize inventory coordination in a single `InventoryManager`, used as the shared application-level inventory access point.

The `InventoryManager` is responsible for:

- adding, updating, using, and discarding ingredients
- applying common inventory validation and mutation rules
- refreshing lifecycle state during inventory operations
- maintaining tenant-scoped cached inventory views
- invalidating cache entries when inventory changes
- notifying dependent listeners and observers after relevant inventory events

The application uses one shared `InventoryManager` instance for the running system. In this design, the singleton-style access is intentional: inventory is treated as a centrally coordinated capability rather than a feature-local concern.

## Consequences

Positive:

- inventory behavior is governed from one place instead of being scattered across web and service layers
- the system has a clearer single operational source of truth for inventory-related behavior
- controlled access to inventory mutation reduces duplication of business rules
- tenant-scoped caching can be managed consistently
- dependent features such as alerts, recommendations, and shopping lists can react to inventory changes through a common coordination point

Negative:

- the `InventoryManager` becomes a highly important architectural dependency
- changes to inventory coordination can affect multiple subsystems at once
- the singleton-style approach increases global coupling compared with a more decentralized design
- the design requires disciplined handling of cache consistency, concurrency, and event side effects
