package app.web;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

import java.time.LocalDate;

@WebListener
public class AppContextListener implements ServletContextListener {
    public static final String APP_SERVICES_KEY = "appServices";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        IngredientRepository ingredientRepository = new IngredientRepository();
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

        AppServices appServices = new AppServices(
                inventoryManager,
                expiryAlertService,
                shoppingListService,
                recommendationService,
                wasteImpactService,
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
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Tomato", 12, "kg", LocalDate.now().plusDays(2), 5));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Onion", 4, "kg", LocalDate.now().plusDays(8), 6));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Basil", 1.5, "kg", LocalDate.now().plusDays(1), 1));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Olive Oil", 12, "liters", LocalDate.now().plusDays(2), 5));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Garlic", 4, "kg", LocalDate.now().plusDays(8), 6));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Pasta", 25, "kg", LocalDate.now().plusDays(5), 7));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Cream", 1.5, "liters", LocalDate.now().plusDays(1), 1));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Soy Sauce", 25, "liters", LocalDate.now().plusDays(5), 7));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Bell Pepper", 3, "kg", LocalDate.now().plusDays(4), 2));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Carrot", 3, "kg", LocalDate.now().plusDays(4), 2));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Spinach", 2, "kg", LocalDate.now().plusDays(2), 1));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Egg", 40, "units", LocalDate.now().plusDays(6), 12));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Potato", 8, "kg", LocalDate.now().plusDays(6), 3));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Lettuce", 2, "kg", LocalDate.now().plusDays(2), 1));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Orange", 15, "units", LocalDate.now().plusDays(5), 5));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Lemon", 12, "units", LocalDate.now().plusDays(5), 4));
        inventoryManager.addIngredient(new Ingredient("restaurant-a", "Parsley", 1, "kg", LocalDate.now().plusDays(3), 0.5));

        inventoryManager.addIngredient(new Ingredient("restaurant-b", "Spinach", 3, "kg", LocalDate.now().plusDays(2), 4));
        inventoryManager.addIngredient(new Ingredient("restaurant-b", "Egg", 50, "units", LocalDate.now().plusDays(6), 15));
    }
}
