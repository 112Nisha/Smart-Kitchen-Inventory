# Developer Guide - Smart Kitchen Inventory

## 1. Stack
- Java 17
- Jakarta Servlet 6 / JSP / JSTL
- Maven WAR packaging
- Apache Tomcat 10+
- In-memory repositories for prototype (DB-ready architecture)

## 2. Build and Run
### Build
```bash
mvn clean package
```

WAR output:
- `target/smart-kitchen-inventory.war`

### Run on Tomcat
1. Deploy WAR to Tomcat `webapps/`.
2. Start Tomcat.
3. Open `http://localhost:8080/smart-kitchen-inventory`.

## 3. Project Structure
- `src/main/java/app/model`: Domain objects and enums
- `src/main/java/app/state`: Ingredient lifecycle states
- `src/main/java/app/alerts`: Alert chain and observer flow
- `src/main/java/app/notification`: Notification strategy + store
- `src/main/java/app/repository`: Data access layer
- `src/main/java/app/service`: Business services
- `src/main/java/app/web`: Servlets and app bootstrap
- `src/test/java/app`: Unit tests
- `src/main/webapp/WEB-INF/views`: JSP pages

## 4. Pattern Mapping
- Singleton: `InventoryManager`
- Observer: `AlertEventBus`, `ChefObserver`, `ManagerObserver`
- Chain of Responsibility:
  - `ExpiryCheckHandler`
  - `UrgencyFlagHandler`
  - `ChefNotificationHandler`
  - `ManagerNotificationHandler`
- State Pattern:
  - `FreshState`, `NearExpiryState`, `ExpiredState`, `DiscardedState`
- Strategy Pattern:
  - `NotificationStrategy`, `EmailNotificationStrategy`

## 5. Architectural Tactics Coverage
- Modularity: clear package separation by concern.
- Caching: tenant-based inventory cache in `InventoryManager`.
- Fault tolerance: notification retry loop in `NotificationService`.
- Scalability: tenant partitioning via `tenantId` on every ingredient.
- Data validation: guarded input checks before persistence and updates.

## 6. Extending to MySQL/PostgreSQL
1. Replace `IngredientRepository` implementation with JDBC/JPA-backed repository.
2. Keep service interfaces unchanged.
3. Add migration scripts for ingredient and alert tables.
4. Move seeded data into SQL seed scripts.

## 7. Test Commands
```bash
mvn test
```

Current unit tests validate:
- State transitions
- Shopping list logic
- Expiry alert escalation to manager

## 8. Notes
- Notifications are stored in-memory for prototype visibility.
- Sustainability impact uses simple configurable coefficients in `WasteImpactService`.
