package app;

import app.model.Ingredient;
import app.model.NotificationMessage;
import app.repository.InMemoryNotificationStore;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import app.service.ShoppingListService;
import app.web.AppServices;
import app.web.DashboardServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardLoadPerformanceTest {

    @AfterEach
    void tearDown() {
        InventoryManager.resetInstanceForTests();
    }

    @Test
    void dashboardLoadStaysUnderTwoSecondsWithCachedIngredientData() throws Exception {
        InventoryManager.resetInstanceForTests();
        String tenant = "tenant-dashboard-performance";
        DelayedIngredientRepository repository = new DelayedIngredientRepository(Duration.ofMillis(1400));
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        ShoppingListService shoppingListService = new ShoppingListService(manager);
        InMemoryNotificationStore notificationStore = new InMemoryNotificationStore();

        seedIngredients(manager, tenant, 600);
        seedNotifications(notificationStore, tenant, 300);

        // Warm tenant cache before measuring dashboard load.
        manager.listIngredients(tenant);
        int readsBeforeDashboardLoad = repository.findByTenantCallCount();

        RequestCapture capture = new RequestCapture();
        HttpServletRequest request = buildRequestProxy(capture);
        HttpServletResponse response = buildResponseProxy(capture);
        TestableDashboardServlet servlet = new TestableDashboardServlet(
                new AppServices(
                        manager,
                        null,
                        shoppingListService,
                        null,
                        null,
                        null,
                        notificationStore,
                        null
                ),
                tenant
        );

        long startNanos = System.nanoTime();
        servlet.execute(request, response);
        long elapsedMillis = nanosToMillis(System.nanoTime() - startNanos);
        int readsAfterDashboardLoad = repository.findByTenantCallCount();

        assertTrue(elapsedMillis < 2_000,
                "Dashboard load should stay under 2000 ms with cache warm; actual=" + elapsedMillis + " ms");
        assertEquals(readsBeforeDashboardLoad, readsAfterDashboardLoad,
                "Dashboard load should reuse cached ingredient data without extra repository reads");

        assertTrue(capture.forwarded.get(), "Dashboard request should forward to JSP view");
        assertEquals("/WEB-INF/views/dashboard.jsp", capture.forwardedPath);
        assertEquals(tenant, capture.attributes.get("tenant"));
        assertEquals(600, capture.attributes.get("ingredientCount"));
        assertEquals(300, capture.attributes.get("notificationCount"));
    }

    private void seedIngredients(InventoryManager manager, String tenantId, int count) {
        for (int i = 0; i < count; i++) {
            double quantity = i % 5 == 0 ? 0.3 : 3.0;
            double lowStockThreshold = 0.5;
            manager.addIngredient(new Ingredient(
                    tenantId,
                    "Ingredient-" + i,
                    quantity,
                    "kg",
                    LocalDate.now().plusDays(5 + (i % 3)),
                    lowStockThreshold
            ));
        }
    }

    private void seedNotifications(InMemoryNotificationStore store, String tenantId, int count) {
        for (int i = 0; i < count; i++) {
            store.save(new NotificationMessage(
                    tenantId,
                    "ingredient-" + i,
                    "STAKEHOLDER",
                    "Alert " + i,
                    "Body " + i
            ));
        }
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000;
    }

    private HttpServletRequest buildRequestProxy(RequestCapture capture) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("setAttribute".equals(methodName)) {
                        capture.attributes.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("getAttribute".equals(methodName)) {
                        return capture.attributes.get((String) args[0]);
                    }
                    if ("getRequestDispatcher".equals(methodName)) {
                        String path = (String) args[0];
                        capture.forwardedPath = path;
                        return buildDispatcherProxy(capture, path);
                    }
                    if ("getContextPath".equals(methodName)) {
                        return "";
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private RequestDispatcher buildDispatcherProxy(RequestCapture capture, String path) {
        return (RequestDispatcher) Proxy.newProxyInstance(
                RequestDispatcher.class.getClassLoader(),
                new Class<?>[]{RequestDispatcher.class},
                (proxy, method, args) -> {
                    if ("forward".equals(method.getName())) {
                        capture.forwarded.set(true);
                        capture.forwardedPath = path;
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private HttpServletResponse buildResponseProxy(RequestCapture capture) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("sendRedirect".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0.0d;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }

    private static final class TestableDashboardServlet extends DashboardServlet {
        private final AppServices services;
        private final String tenantId;

        private TestableDashboardServlet(AppServices services, String tenantId) {
            this.services = services;
            this.tenantId = tenantId;
        }

        @Override
        protected AppServices services() {
            return services;
        }

        @Override
        protected String loggedInTenant(HttpServletRequest req) {
            return tenantId;
        }

        private void execute(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            super.doGet(req, resp);
        }
    }

    private static final class DelayedIngredientRepository extends IngredientRepository {
        private final Duration delay;
        private final AtomicInteger findByTenantCalls = new AtomicInteger();

        private DelayedIngredientRepository(Duration delay) {
            this.delay = delay;
        }

        @Override
        public List<Ingredient> findByTenant(String tenantId) {
            findByTenantCalls.incrementAndGet();
            LockSupport.parkNanos(delay.toNanos());
            return super.findByTenant(tenantId);
        }

        private int findByTenantCallCount() {
            return findByTenantCalls.get();
        }
    }

    private static final class RequestCapture {
        private final Map<String, Object> attributes = new HashMap<>();
        private final AtomicBoolean forwarded = new AtomicBoolean(false);
        private String forwardedPath;
    }
}