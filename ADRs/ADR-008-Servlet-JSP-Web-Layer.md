# ADR-008: Use a Servlet-and-JSP Server-Rendered Web Layer for the Prototype

## Status

Accepted

## Context

The system requires a web-facing interaction layer for login, dashboard views, inventory operations, notifications, dish recommendations, and shopping-list workflows. The team needed a web-layer approach that would work well with the chosen modular monolith architecture and keep the prototype implementation manageable.

The web layer needed to:

- support straightforward request handling
- render operational pages for end users
- integrate cleanly with application services
- avoid unnecessary framework overhead for the current scope

The main alternatives considered were:

- use a lightweight servlet-and-JSP web layer
- adopt a heavier framework-centered web stack
- build a separate frontend application with a dedicated API layer

For the prototype, a separate frontend stack or a heavier framework would increase setup and coordination overhead without being necessary to demonstrate the core architectural ideas and workflows.

## Decision

Use a servlet-and-JSP server-rendered web layer for the prototype.

This means:

- HTTP request handling is implemented in servlets
- server-rendered JSP pages provide the main user-facing views
- business services remain behind the web layer rather than inside view code
- the web layer stays aligned with the modular monolith structure of the rest of the system

## Consequences

Positive:

- the web layer is simple and direct for prototype development
- it integrates naturally with the rest of the Java application
- server-rendered pages are sufficient for the current operational workflows
- the team can focus effort on domain behavior rather than frontend/application split complexity

Negative:

- the presentation approach is less flexible than a richer client-side application architecture
- server-rendered UI can become harder to evolve if interaction complexity grows significantly
- the web layer remains tied to the surrounding Java web application stack
