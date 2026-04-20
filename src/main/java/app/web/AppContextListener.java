package app.web;

import app.service.ExpiryAlertScheduler;
import app.service.StakeholderNotificationHandler;
import app.config.AlertConfigService;
import app.model.Ingredient;
import app.service.DashboardNotificationStrategy;
import app.service.EmailNotificationStrategy;
import app.service.LowStockAlertService;
import app.service.RoleNotificationListener;
import app.service.StaleNotificationPruner;
import app.repository.NotificationStore;
import app.service.NotificationService;
import app.repository.SqliteNotificationStore;
import app.repository.DishRepository;
import app.repository.IngredientRepository;
import app.repository.ShoppingListRepository;
import app.repository.SqliteIngredientRepository;
import app.repository.SqliteShoppingListRepository;
import app.repository.UserRepository;
import app.service.DishRecommendationService;
import app.service.ExpiryAlertService;
import app.service.InventoryManager;
import app.service.NavigationAssistantService;
import app.service.ShoppingListService;
import app.service.WasteImpactService;


import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@WebListener
public class AppContextListener implements ServletContextListener {
    public static final String APP_SERVICES_KEY = "appServices";

    // Held as a field so contextDestroyed() can stop the background sweep
    // cleanly. Null until contextInitialized has run.
    private ExpiryAlertScheduler expiryAlertScheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Bundle 3: AlertConfigService owns the live-reloadable thresholds
        // (near-expiry, retention). Initialised first so every downstream
        // consumer can wire a supplier against its cache.
        AlertConfigService alertConfigService = new AlertConfigService();

        IngredientRepository ingredientRepository = new SqliteIngredientRepository();
        InventoryManager inventoryManager = InventoryManager.getInstance(
                ingredientRepository,
                () -> alertConfigService.get().nearExpiryDays());

        seedData(inventoryManager);

        // Fix 7: durable SQLite-backed store replaces the in-memory list so
        // notifications (and their dedup state) survive container restarts.
        // The interface type lets tests keep using InMemoryNotificationStore
        // without bringing the DB file into scope.
        NotificationStore notificationStore = new SqliteNotificationStore();
        NotificationService notificationService = new NotificationService(3);
        notificationService.registerStrategy(new DashboardNotificationStrategy(notificationStore));
        notificationService.registerStrategy(new EmailNotificationStrategy("localhost", 25, new UserRepository()));

        StakeholderNotificationHandler stakeholderNotify = new StakeholderNotificationHandler(notificationService);

        ExpiryAlertService expiryAlertService = new ExpiryAlertService(inventoryManager, stakeholderNotify);
        expiryAlertService.attachLifecycleListeners();
        inventoryManager.addListener(new RoleNotificationListener(notificationService));
        inventoryManager.addListener(new StaleNotificationPruner(notificationStore));

        // Create shopping list service with repository
        ShoppingListRepository shoppingListRepository = new SqliteShoppingListRepository();
        ShoppingListService shoppingListService = new ShoppingListService(inventoryManager, shoppingListRepository);

        // Register shopping list service as an observer of inventory changes
        inventoryManager.addInventoryObserver(shoppingListService);

        DishRecommendationService recommendationService = new DishRecommendationService(inventoryManager, new DishRepository(), notificationService);
        WasteImpactService wasteImpactService = new WasteImpactService();
        NavigationAssistantService navigationAssistantService = new NavigationAssistantService();

        LowStockAlertService lowStockAlertService = new LowStockAlertService(inventoryManager, notificationService);
        inventoryManager.addListener(lowStockAlertService);

        AppServices appServices = new AppServices(
                inventoryManager,
                expiryAlertService,
                shoppingListService,
                recommendationService,
                wasteImpactService,
                navigationAssistantService,
                notificationStore,
                alertConfigService,
                lowStockAlertService
        );

        ServletContext context = sce.getServletContext();
        context.setAttribute(APP_SERVICES_KEY, appServices);

        // Sweep runs once per day — expiry thresholds are day-level so more
        // frequent checks add no value. Override via system property
        // `expiry.alert.interval.seconds` for testing without waiting 24h.
        long intervalSeconds = readIntervalSecondsFromProperty(86400L);
        expiryAlertScheduler = new ExpiryAlertScheduler(
                expiryAlertService,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS,
                notificationStore,
                (java.util.function.IntSupplier) () -> alertConfigService.get().retentionDays());
        expiryAlertScheduler.setLowStockAlertService(lowStockAlertService);
        expiryAlertScheduler.start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Stop the background sweep BEFORE resetting the singleton so any
        // in-flight tick completes against a still-valid InventoryManager.
        if (expiryAlertScheduler != null) {
            expiryAlertScheduler.stop();
        }
        InventoryManager.resetInstanceForTests();
    }

    // Parses the interval from a system property, falling back to the default
    // if unset or malformed. Kept lenient on purpose — a bad value should
    // degrade to the default rather than refuse to boot the app.
    private long readIntervalSecondsFromProperty(long defaultSeconds) {
        String raw = System.getProperty("expiry.alert.interval.seconds");
        if (raw == null || raw.isBlank()) {
            return defaultSeconds;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : defaultSeconds;
        } catch (NumberFormatException ex) {
            return defaultSeconds;
        }
    }

    private void seedData(InventoryManager inventoryManager) {
        seedTenantIfEmpty(inventoryManager, "restaurant-a", List.of(
                new Ingredient("restaurant-a", "Tomato", 12, "kg", LocalDate.now().plusDays(2), 5),
                new Ingredient("restaurant-a", "Onion", 4, "kg", LocalDate.now().plusDays(8), 6),
                new Ingredient("restaurant-a", "Basil", 1.5, "kg", LocalDate.now().plusDays(1), 1),
                new Ingredient("restaurant-a", "Olive Oil", 12, "liters", LocalDate.now().plusDays(2), 5),
                new Ingredient("restaurant-a", "Garlic", 4, "kg", LocalDate.now().plusDays(8), 6),
                new Ingredient("restaurant-a", "Cream", 1.5, "liters", LocalDate.now().plusDays(1), 1),
                new Ingredient("restaurant-a", "Soy Sauce", 25, "liters", LocalDate.now().plusDays(5), 7),
                new Ingredient("restaurant-a", "Bell Pepper", 3, "kg", LocalDate.now().plusDays(4), 2),
                new Ingredient("restaurant-a", "Carrot", 3, "kg", LocalDate.now().plusDays(4), 2),
                new Ingredient("restaurant-a", "Spinach", 2, "kg", LocalDate.now().plusDays(2), 1),
                new Ingredient("restaurant-a", "Egg", 40, "kg", LocalDate.now().plusDays(6), 12),
                new Ingredient("restaurant-a", "Potato", 8, "kg", LocalDate.now().plusDays(6), 3)
        ));

        seedTenantIfEmpty(inventoryManager, "restaurant-b", List.of(
                new Ingredient("restaurant-b", "Spinach", 3, "kg", LocalDate.now().plusDays(2), 4),
                new Ingredient("restaurant-b", "Egg", 50, "kg", LocalDate.now().plusDays(6), 15)
        ));

        seedTenantIfEmpty(inventoryManager, "restaurant-c", List.of(
                new Ingredient("restaurant-c", "Tomato", 12, "kg", LocalDate.now().plusDays(2), 5),
                new Ingredient("restaurant-c", "Onion", 4, "kg", LocalDate.now().plusDays(8), 6),
                new Ingredient("restaurant-c", "Basil", 1.5, "kg", LocalDate.now().plusDays(1), 1),
                new Ingredient("restaurant-c", "Olive Oil", 12, "liters", LocalDate.now().plusDays(2), 5),
                new Ingredient("restaurant-c", "Garlic", 4, "kg", LocalDate.now().plusDays(8), 6),
                new Ingredient("restaurant-c", "Cream", 1.5, "liters", LocalDate.now().plusDays(1), 1),
                new Ingredient("restaurant-c", "Soy Sauce", 25, "liters", LocalDate.now().plusDays(5), 7),
                new Ingredient("restaurant-c", "Bell Pepper", 3, "kg", LocalDate.now().plusDays(4), 2),
                new Ingredient("restaurant-c", "Carrot", 3, "kg", LocalDate.now().plusDays(4), 2),
                new Ingredient("restaurant-c", "Spinach", 2, "kg", LocalDate.now().plusDays(2), 1),
                new Ingredient("restaurant-c", "Egg", 40, "kg", LocalDate.now().plusDays(6), 12),
                new Ingredient("restaurant-c", "Potato", 8, "kg", LocalDate.now().plusDays(6), 3),
                new Ingredient("restaurant-c", "Pasta", 6, "kg", LocalDate.now().plusDays(12), 4),
                new Ingredient("restaurant-c", "Butter", 2, "kg", LocalDate.now().plusDays(7), 3),
                new Ingredient("restaurant-c", "Parsley", 1, "kg", LocalDate.now().plusDays(3), 2),
                new Ingredient("restaurant-c", "Salt", 3, "kg", LocalDate.now().plusDays(30), 10),
                new Ingredient("restaurant-c", "Pepper", 1, "kg", LocalDate.now().plusDays(30), 8)
        ));
    }

    private void seedTenantIfEmpty(InventoryManager inventoryManager, String tenantId, List<Ingredient> ingredients) {
        if (!inventoryManager.listIngredients(tenantId).isEmpty()) {
            return;
        }
        ingredients.forEach(inventoryManager::addIngredient);
    }
}
