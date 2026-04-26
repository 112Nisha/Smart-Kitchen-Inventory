# Developer Guide - Smart Kitchen Inventory

## 1. Stack

- Java 17
- Java Servlets, JSP, JSTL
- Maven WAR packaging
- SQLite via `sqlite-jdbc`
- Gson
- JavaMail
- JUnit 5

## 2. Repository Layout

- `src/main/java/app/config`: SQLite initialization and alert configuration
- `src/main/java/app/model`: domain models and records
- `src/main/java/app/repository`: SQLite-backed repositories and stores
- `src/main/java/app/service`: business services and coordination logic
- `src/main/java/app/state`: ingredient lifecycle state behavior
- `src/main/java/app/web`: servlets, bootstrapping, and app wiring
- `src/main/webapp/WEB-INF/views`: JSP views
- `src/main/webapp/assets`: CSS and static assets
- `src/test/java/app`: unit, integration, and performance-oriented tests
- `ADRs`: architectural decision records

## 3. Runtime Architecture

The application is packaged as a single WAR and wired in-process through [`AppContextListener`](./src/main/java/app/web/AppContextListener.java).

Key runtime components:

- `InventoryManager`: singleton coordination point for inventory operations
- `SqliteIngredientRepository`: persisted ingredient storage
- `SqliteNotificationStore`: persisted notifications with deduplication
- `SqliteShoppingListRepository`: persisted shopping-list state
- `DishRecommendationService`: recommendation and cooked-dish flow
- `ExpiryAlertService` and `LowStockAlertService`: alert generation
- `ExpiryAlertScheduler`: background periodic sweep
- `NavigationAssistantService`: optional assistant-backed navigation help

## 4. Introduction to Codebase

If you are new to the codebase, start with these classes in this order:

- [`AppContextListener`](./src/main/java/app/web/AppContextListener.java): application bootstrap. Creates repositories, services, listeners, notification strategies, and the background scheduler.
- [`AppServices`](./src/main/java/app/web/AppServices.java): shared container that exposes the initialized services to the servlet layer.
- [`BaseServlet`](./src/main/java/app/web/BaseServlet.java): common servlet helpers for session handling, tenant lookup, redirects, and role checks.
- [`InventoryManager`](./src/main/java/app/service/InventoryManager.java): the core coordination class for ingredient operations. Most inventory-related behavior flows through this singleton.
- Repository classes in [`src/main/java/app/repository`](./src/main/java/app/repository): persistence boundaries for ingredients, notifications, shopping-list state, and users.
- Service classes in [`src/main/java/app/service`](./src/main/java/app/service): business logic for recommendations, alerts, notifications, shopping-list generation, lifecycle tracking, and waste impact.
- Servlet classes in [`src/main/java/app/web`](./src/main/java/app/web): route handlers that translate HTTP requests into service calls and JSP rendering.
- JSP views in [`src/main/webapp/WEB-INF/views`](./src/main/webapp/WEB-INF/views): page templates for dashboard, inventory, auth, recommendations, notifications, recipes, and shopping list.

## 5. High-Level Responsibilities

These are the main classes a developer will encounter first:

- [`AppContextListener`](./src/main/java/app/web/AppContextListener.java): wires the whole application together at startup and starts the scheduled alert sweep.
- [`AppServices`](./src/main/java/app/web/AppServices.java): gives servlets access to the already-wired service layer.
- [`InventoryManager`](./src/main/java/app/service/InventoryManager.java): central inventory API for add/update/use/discard/list operations and tenant-scoped caching.
- [`ExpiryAlertService`](./src/main/java/app/service/ExpiryAlertService.java): evaluates ingredient expiry state and creates alert contexts.
- [`LowStockAlertService`](./src/main/java/app/service/LowStockAlertService.java): evaluates low-stock conditions and triggers notifications.
- [`ShoppingListService`](./src/main/java/app/service/ShoppingListService.java): builds and updates shopping-list entries from inventory thresholds.
- [`DishRecommendationService`](./src/main/java/app/service/DishRecommendationService.java): suggests dishes from near-expiry ingredients and handles the "log as cooked" workflow.
- [`NotificationService`](./src/main/java/app/service/NotificationService.java): dispatches notifications through registered strategies with retry/fault-tolerance behavior.
- [`SqliteIngredientRepository`](./src/main/java/app/repository/SqliteIngredientRepository.java): persists ingredient state in SQLite.
- [`SqliteNotificationStore`](./src/main/java/app/repository/SqliteNotificationStore.java): persists notifications with deduplication and retention support.
- [`UserRepository`](./src/main/java/app/repository/UserRepository.java): handles registration, login, and restaurant-user lookup.

## 6. Request and Data Flow

The normal synchronous request path is:

`browser -> servlet -> service -> repository/store -> SQLite -> servlet -> JSP response`

Typical examples:

- Inventory update:
  `InventoryServlet -> InventoryManager -> SqliteIngredientRepository`
- Dish recommendation page:
  `DishRecommendationServlet -> DishRecommendationService -> InventoryManager / DishRepository`
- Notifications page:
  `NotificationsServlet -> NotificationStore + AlertConfigService`

There is also background and event-driven behavior:

- `AppContextListener` starts `ExpiryAlertScheduler`
- the scheduler periodically triggers expiry and low-stock evaluation
- inventory changes notify listeners/observers
- listeners update notifications and shopping-list state

## 7. Persistence Model

The prototype is not in-memory anymore. It persists data in SQLite under `data/data.db`.

Initialized tables include:

- `restaurants`
- `users`
- `dish_recipes`
- `recipe_ingredients`
- `inventory_ingredients`
- `notifications`
- `shopping_list_items`
- `app_config`

Database setup is handled automatically by [`DatabaseInitializer`](./src/main/java/app/config/DatabaseInitializer.java).

## 8. Authentication and Roles

- Users register against a restaurant name
- The first user for a new restaurant must be a `manager`
- Supported roles are `manager` and `chef`
- Login stores `tenant`, `username`, and `role` in the session
- Chefs are redirected to `/recommendations`
- Managers are redirected to `/dashboard`

## 9. Build and Run

### Build

```bash
mvn clean package
```

WAR output:

```text
target/smart-kitchen-inventory.war
```

### Local Run with Maven

```bash
mvn tomcat7:run
```

Open:

```text
http://localhost:8080/smart-kitchen-inventory
```

### Environment Variables

Optional navigation assistant support:

```bash
export GEMINI_API_KEY="your_gemini_api_key"
```

Optional email notifications:

```bash
export GMAIL_FROM="your-address@gmail.com"
export GMAIL_APP_PASSWORD="your-app-password"
```

If Gmail variables are absent, the app still runs with dashboard-stored notifications only.

## 10. Important Routes

- `/login`
- `/register`
- `/dashboard`
- `/inventory`
- `/notifications`
- `/recommendations`
- `/shopping-list`
- `/assistant/navigation`

## 11. Pattern Mapping

- Singleton: `InventoryManager`
- State Pattern: `FreshState`, `NearExpiryState`, `ExpiredState`, `DiscardedState`
- Strategy Pattern: `NotificationStrategy`, `EmailNotificationStrategy`, `DashboardNotificationStrategy`
- Observer/Event-driven flow: inventory listeners, role notification listener, shopping-list observer
- Repository Pattern: ingredient, notification, shopping-list, and user persistence boundaries

## 12. Architectural Tactics Coverage

- Modularity: packages split by config, repository, service, web, and state concerns
- Caching: tenant-scoped caching inside `InventoryManager`
- Fault tolerance: notification retries and graceful fallback when optional integrations are missing
- Scalability: tenant-based isolation and repository abstractions
- Data validation: servlet and service-level input checks before persistence

## 13. Testing

Main suite:

```bash
mvn test
```

Selected performance/caching commands:

```bash
mvn -Dtest=InventoryManagerCachingTest,InventoryManagerCachePerformanceTest test
mvn -Dtest=DashboardLoadPerformanceTest test
```

The Maven Surefire configuration excludes some performance-heavy tests from the default run. See [`pom.xml`](./pom.xml) for current exclusions.

## 14. Notes for Submission / Demo

- The app auto-initializes its SQLite database on startup
- Sample restaurant and inventory data are seeded for prototype exploration
- User accounts are created through the registration flow
- Notification history survives restarts because it is stored in SQLite
- The shopping list supports CSV export
- The navigation assistant depends on `GEMINI_API_KEY`; without it, that feature is not usable
