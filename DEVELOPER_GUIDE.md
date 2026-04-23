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

Set the assistant API key before running from terminal:
```bash
export GEMINI_API_KEY="your_gemini_api_key"
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

Caching NFR validation:
```bash
mvn -Dtest=InventoryManagerCachingTest,InventoryManagerCachePerformanceTest test
```

Main dashboard performance threshold test (< 2s with warmed cache):
```bash
mvn -Dtest=DashboardLoadPerformanceTest test
```

Benchmark formulas for report:
- `latency_improvement_percent = ((miss_mean_ms - hit_mean_ms) / miss_mean_ms) * 100`
- `db_read_reduction_percent = ((miss_reads_per_request - hit_reads_per_request) / miss_reads_per_request) * 100`


# Cache and Performance Test Summary

## 1. InventoryManagerCachingTest

This test verifies that the ingredient cache inside `InventoryManager` behaves correctly during normal operations.

It checks that repeated calls to `listIngredients()` for the same tenant use cached data instead of repeatedly querying the repository. It also verifies that cache entries are invalidated whenever ingredient data changes through operations such as adding, updating, using, or discarding ingredients. In addition, it confirms that cache entries remain isolated between tenants so one tenant’s updates do not affect another tenant’s cached data.

### Output

This test does not print benchmark values. It returns assertion success or failure.

Typical output:

- Pass if repository call counts match expected values
- Fail if cache is not reused or invalidated correctly

Example assertions checked:

- repeated reads → repository called once  
- after update → repository called again  
- tenant isolation → counts remain separate

### Execution report (2026-04-19)

- Result: `PASS` (`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`)
- Command used: `mvn -Dtest=InventoryManagerCachingTest test`
- Evidence file: `target/surefire-reports/app.InventoryManagerCachingTest.txt`


## 2. InventoryManagerCachePerformanceTest

This test measures whether caching improves retrieval speed in `InventoryManager`.

A delayed repository is used to simulate slow database access. Two scenarios are measured:

- **Cache miss path:** cache reset before every request, forcing repository access
- **Cache hit path:** cache warmed once, then reused repeatedly

The test collects latency statistics across many iterations and compares both paths.

### Output

This test prints benchmark statistics to console.

Typical output:

```text
Caching benchmark (InventoryManager, tenant=tenant-cache-perf)
Cache miss: mean=4.2 ms, median=4.1 ms, p95=4.5 ms, reads/request=1.000
Cache hit:  mean=0.1 ms, median=0.1 ms, p95=0.2 ms, reads/request=0.000
Latency improvement: 97.6%
Repository read reduction: 100.0%
```

### Execution report (2026-04-19)

- Result: `PASS` (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`)
- Command used: `mvn -Dtest=InventoryManagerCachePerformanceTest test`
- Captured metrics:
  - Cache miss: mean=`4.532 ms`, median=`4.474 ms`, p95=`4.793 ms`, reads/request=`1.000`
  - Cache hit: mean=`0.019 ms`, median=`0.019 ms`, p95=`0.026 ms`, reads/request=`0.000`
  - Latency improvement: `99.59%`
  - Repository read reduction: `100.00%`
- Evidence file: `target/surefire-reports/app.InventoryManagerCachePerformanceTest.txt`

## 3.  DashboardLoadPerformanceTest

This test verifies that dashboard loading remains fast when ingredient data is already available in cache.

A delayed repository is used to simulate slow database access by adding artificial delay to every `findByTenant()` call. A large dataset of ingredients and notifications is inserted first. Before measuring dashboard load time, the ingredient cache is warmed using `manager.listIngredients(tenant)` so that dashboard rendering should reuse cached data instead of querying the repository again.

A proxy-based mock request and response are created to execute `DashboardServlet` without a real servlet container. The servlet is then timed while processing a dashboard request.

## What it checks

- Dashboard response completes in under 2 seconds  
- No additional repository read occurs during dashboard load  
- Request is forwarded to the dashboard JSP  
- Correct dashboard attributes are set

## Output

This test does not print benchmark values by default. It returns assertion success or failure.

Typical assertions checked:

- elapsed time < 2000 ms  
- repository read count unchanged after dashboard execution  
- forwarded path = `/WEB-INF/views/dashboard.jsp`  
- `ingredientCount = 600`  
- `notificationCount = 300`

### Execution report (2026-04-19)

- Result: `PASS` (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`)
- Command used: `mvn -Dtest=DashboardLoadPerformanceTest test`
- Confirmed checks:
  - Dashboard request remained under `2s` with warmed cache.
- Evidence file: `target/surefire-reports/app.DashboardLoadPerformanceTest.txt`

Current unit tests validate:
- State transitions
- Shopping list logic
- Expiry alert escalation to manager

## 8. Notes
- Notifications are stored in-memory for prototype visibility.
