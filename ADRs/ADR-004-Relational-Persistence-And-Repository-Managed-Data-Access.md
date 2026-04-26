# ADR-004: Use Relational Persistence with Repository-Managed Data Access, Implemented with SQLite for the Prototype

## Status

Accepted

## Context

The Smart Kitchen Inventory system persists several closely related kinds of structured operational data, including:

- restaurants and users
- inventory ingredients
- recipes and recipe ingredients
- notifications
- shopping-list state
- application configuration

This data has clear relationships, requires scoped queries by restaurant, and benefits from transactional consistency when a feature updates multiple records as part of one workflow.

The team needed to make two related design choices:

- what kind of persistence model best fits the system's data and operational behavior
- where persistence logic should live in the codebase

The main alternatives considered were:

- use only in-memory storage for the prototype
- use a relational database with SQL-based persistence
- use a heavier ORM-centered persistence stack
- place SQL directly in service or web-layer code instead of dedicated persistence classes

An in-memory-only design would simplify setup, but it would not support durable operational data. A heavier ORM stack could reduce some boilerplate, but it would add framework complexity that was not necessary for the current system scope. Embedding persistence logic directly in services and servlets would tightly couple business workflows to storage concerns and weaken maintainability.

## Decision

Use a relational persistence model, implemented with SQLite for the prototype, and keep persistence logic in repository-managed data access classes.

This means:

- the system treats its core data as structured relational data rather than transient in-memory state
- the prototype uses SQLite as the concrete database technology
- SQL access is concentrated in repository or repository-like persistence classes
- business services depend on persistence components instead of embedding database operations directly in request-handling logic
- the design remains conceptually open to replacing the prototype database technology later if the long-term system requires it

SQLite was chosen as the prototype implementation because it provides durable relational storage with very low setup overhead, which suits the current project stage while preserving the relational model needed by the application.

## Consequences

Positive:

- the persistence model fits the structured and related nature of the system's data
- durable storage supports realistic operational workflows instead of resetting state on every restart
- transaction-oriented database operations are available where needed
- concentrating persistence logic outside the web layer improves separation of concerns
- the use of repository-managed access gives the architecture a cleaner path for future refinement or database replacement

Negative:

- the prototype remains dependent on an embedded database technology at runtime
- moving to a larger-scale production database would still require adaptation and testing
- manual SQL-based persistence requires more implementation effort than a fully managed ORM approach
- the quality of abstraction depends on keeping persistence responsibilities disciplined across the codebase
