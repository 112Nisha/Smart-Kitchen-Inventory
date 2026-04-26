# ADR Index

This folder contains Architecture Decision Records for major design decisions reflected in the current Smart Kitchen Inventory prototype.

The ADRs follow a compact Nygard-style structure/

## Current ADRs

- [ADR-001: Use a Layered Modular Monolith as the Initial System Architecture](./ADR-001-Layered-Modular-Monolith.md)
- [ADR-002: Model Each Restaurant as a Tenant](./ADR-002-Restaurant-As-Tenant.md)
- [ADR-003: Centralize Inventory Coordination in a Singleton InventoryManager](./ADR-003-Centralized-Inventory-Manager.md)
- [ADR-004: Use Relational Persistence with Repository-Managed Data Access, Implemented with SQLite for the Prototype](./ADR-004-Relational-Persistence-And-Repository-Managed-Data-Access.md)
- [ADR-005: Use an Explicit Ingredient Lifecycle Model with State-Based Behavior](./ADR-005-Explicit-Ingredient-Lifecycle-Model.md)
- [ADR-006: Deliver Notifications Through Strategies with Durable Storage and Retry](./ADR-006-Notification-Delivery-Strategies.md)
- [ADR-007: Use In-Process Event-Driven Coordination for Inventory-Triggered Side Effects](./ADR-007-In-Process-Event-Driven-Coordination.md)
- [ADR-008: Use a Servlet-and-JSP Server-Rendered Web Layer for the Prototype](./ADR-008-Servlet-JSP-Web-Layer.md)
- [ADR-009: Reject Chain of Responsibility for Expiry Alert Routing in the Current System](./ADR-009-Reject-Chain-of-Responsibility-for-Expiry-Alert-Routing.md)

## Notes

- These ADRs are based on the implemented prototype, not on earlier proposal assumptions.
- They document decisions visible in the current codebase, even where the implementation still has known limitations.
