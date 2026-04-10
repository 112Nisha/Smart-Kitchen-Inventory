package app.web;

import app.alerts.AlertEventBus;
import app.alerts.AlertHandler;
import app.alerts.ChefNotificationHandler;
import app.alerts.ChefObserver;
import app.alerts.ExpiryCheckHandler;
import app.alerts.ManagerNotificationHandler;
import app.alerts.ManagerObserver;
import app.alerts.UrgencyFlagHandler;
import app.model.Ingredient;
import app.notification.EmailNotificationStrategy;
import app.notification.InMemoryNotificationStore;
import app.notification.NotificationService;
import app.repository.DishRepository;
import app.repository.IngredientRepository;
import app.repository.SqliteIngredientRepository;
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

@WebListener
public class AppContextListener implements ServletContextListener {
    public static final String APP_SERVICES_KEY = "appServices";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        IngredientRepository ingredientRepository = new SqliteIngredientRepository();
        InventoryManager inventoryManager = InventoryManager.getInstance(ingredientRepository, 3);

        seedData(inventoryManager);

        InMemoryNotificationStore notificationStore = new InMemoryNotificationStore();
        NotificationService notificationService = new NotificationService(3);
        notificationService.registerStrategy(new EmailNotificationStrategy(notificationStore));

        AlertHandler expiryCheck = new ExpiryCheckHandler(3);
        AlertHandler urgencyFlag = new UrgencyFlagHandler(20);
        AlertHandler chefNotify = new ChefNotificationHandler(notificationService);
        AlertHandler managerNotify = new ManagerNotificationHandler(notificationService);
        expiryCheck.setNext(urgencyFlag).setNext(chefNotify).setNext(managerNotify);

        AlertEventBus eventBus = new AlertEventBus();
        eventBus.subscribe(new ChefObserver());
        eventBus.subscribe(new ManagerObserver());

        ExpiryAlertService expiryAlertService = new ExpiryAlertService(inventoryManager, expiryCheck, eventBus);
        ShoppingListService shoppingListService = new ShoppingListService(inventoryManager);
        DishRecommendationService recommendationService = new DishRecommendationService(inventoryManager, new DishRepository());
        WasteImpactService wasteImpactService = new WasteImpactService();
        NavigationAssistantService navigationAssistantService = new NavigationAssistantService();

        AppServices appServices = new AppServices(
                inventoryManager,
                expiryAlertService,
                shoppingListService,
                recommendationService,
                wasteImpactService,
            navigationAssistantService,
                notificationStore
        );

        ServletContext context = sce.getServletContext();
        context.setAttribute(APP_SERVICES_KEY, appServices);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        InventoryManager.resetInstanceForTests();
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
