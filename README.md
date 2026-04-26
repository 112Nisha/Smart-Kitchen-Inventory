# Smart Kitchen Inventory

Smart Kitchen Inventory is a Java web application prototype for restaurant inventory management. It is the prototype implementation for our software engineering course project and focuses on reducing food waste through inventory tracking, expiry awareness, dish recommendations, and shopping-list automation.

## Project Overview

The system helps restaurant teams:

- Track ingredient quantities, units, expiry dates, and low-stock thresholds
- Monitor ingredient lifecycle state: `FRESH`, `NEAR_EXPIRY`, `EXPIRED`, `DISCARDED`
- Generate expiry alerts and retain notification history
- Recommend dishes that use near-expiry ingredients
- Log a recommended dish as cooked and show waste-impact feedback
- Generate shopping lists when stock falls below threshold
- Support multiple restaurant tenants in one prototype
- Provide login and registration for restaurant staff

This prototype implements the core end-to-end workflows proposed in the project materials, with additional supporting features around authentication, notification retention, and role-based navigation.

## Tech Stack

- Java 17
- Java Servlets, JSP, and JSTL
- Maven WAR packaging
- SQLite for persisted application data
- Apache Tomcat / servlet container deployment

## Architecture Highlights

- Singleton: centralized `InventoryManager`
- State Pattern: ingredient lifecycle behavior
- Observer/Event-driven coordination: inventory listeners trigger downstream reactions
- Strategy Pattern: pluggable notification delivery
- Repository Pattern: persistence behind service-layer APIs
- Tenant-scoped services and data separation

## What Is Persisted

The application uses SQLite and creates its local database under [`data/`](./data):

- users and restaurants
- inventory ingredients
- recipes and recipe ingredients
- notifications
- shopping-list state
- alert configuration

The SQLite schema is initialized automatically at startup by [`DatabaseInitializer`](./src/main/java/app/config/DatabaseInitializer.java).

## Prerequisites

- Java 17
- Maven 3.9+ recommended
- A servlet container or local Maven run path

## Environment Variables

Required for the optional navigation assistant widget:

```bash
export GEMINI_API_KEY="your_gemini_api_key"
```

Optional for email notification delivery:

```bash
export GMAIL_FROM="your-address@gmail.com"
export GMAIL_APP_PASSWORD="your-app-password"
```

If the Gmail variables are not set, the application still runs and dashboard-stored notifications remain available.

## Build

```bash
mvn clean package
```

WAR output:

```text
target/smart-kitchen-inventory.war
```

## Run

### Option 1: Run with Maven

```bash
mvn tomcat7:run
```

Then open:

```text
http://localhost:8080/smart-kitchen-inventory
```

### Option 2: Deploy the WAR

1. Build the project with `mvn clean package`.
2. Deploy `target/smart-kitchen-inventory.war` to your servlet container.
3. Start the server.
4. Open `http://localhost:8080/smart-kitchen-inventory`.

## First-Time Use

1. Open the application.
2. If you are not logged in, you will be redirected to `/login`.
3. For a new restaurant, register the first account as a `manager`.
4. After login:
   - managers are redirected to the dashboard
   - chefs are redirected to dish recommendations

The application also seeds sample restaurant and inventory data for prototype exploration, but user accounts are created through registration.

## Main Screens

- `/dashboard`: summary metrics for the current tenant
- `/inventory`: add, update, use, discard, and review ingredients
- `/notifications`: expiry alerts, retained notifications, and alert settings
- `/recommendations`: near-expiry dish suggestions and cooked-dish impact flow
- `/shopping-list`: low-stock items, CSV export, and purchase/ignore actions
- `/register` and `/login`: user onboarding and authentication

## Documentation

- User guide: [`USER_GUIDE.md`](./USER_GUIDE.md)
- Developer guide: [`DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md)
- ADR index: [`ADRs/ADR-INDEX.md`](./ADRs/ADR-INDEX.md)
- Course project artifacts:
  - [`SE_Project3_Task1_Requirements-and-Subsystems.md`](./SE_Project3_Task1_Requirements-and-Subsystems.md)
  - [`SE_Project3_Task2_Refined_Stakeholder_Model.md`](./SE_Project3_Task2_Refined_Stakeholder_Model.md)
  - [`SE_Project3_Task3_Architecture_Diagram_Spec.md`](./SE_Project3_Task3_Architecture_Diagram_Spec.md)
  - [`SE_Project3_Task4_Prototype_and_Analysis.md`](./SE_Project3_Task4_Prototype_and_Analysis.md)

## Testing

Run the main automated test suite with:

```bash
mvn test
```

Some performance-oriented tests are documented in the developer guide and can be run separately when needed.
