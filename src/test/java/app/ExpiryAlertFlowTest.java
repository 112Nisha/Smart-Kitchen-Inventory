package app;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;


import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpiryAlertFlowTest {
    @Test
    void urgentNearExpiryItemEscalatesToManager() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        manager.addIngredient(new Ingredient("tenant-alert", "Tomato", 30, "kg", LocalDate.now().plusDays(1), 5));

        InMemoryNotificationStore store = new InMemoryNotificationStore();
        NotificationService notificationService = new NotificationService(2);
        notificationService.registerStrategy(new EmailNotificationStrategy(store));

        AlertHandler expiryCheck = new ExpiryCheckHandler(3);
        AlertHandler urgency = new UrgencyFlagHandler(20);
        AlertHandler chef = new ChefNotificationHandler(notificationService);
        AlertHandler managerNotify = new ManagerNotificationHandler(notificationService);
        expiryCheck.setNext(urgency).setNext(chef).setNext(managerNotify);

        AlertEventBus eventBus = new AlertEventBus();
        eventBus.subscribe(new ChefObserver());
        eventBus.subscribe(new ManagerObserver());

        ExpiryAlertService alertService = new ExpiryAlertService(manager, expiryCheck, eventBus);
        alertService.evaluateAndNotify("tenant-alert");

        boolean managerMessageSent = store.all().stream().anyMatch(msg -> msg.getRecipientRole().equals("MANAGER"));
        assertTrue(managerMessageSent);
    }

    @Test
    void consumedIngredientDoesNotAppearInExpiryAlerts() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient tomato = manager.addIngredient(
                new Ingredient("tenant-alert", "Tomato", 1.0, "kg", LocalDate.now().plusDays(1), 0.2)
        );

        manager.useIngredient("tenant-alert", tomato.getId(), 1.0);

        InMemoryNotificationStore store = new InMemoryNotificationStore();
        NotificationService notificationService = new NotificationService(2);
        notificationService.registerStrategy(new EmailNotificationStrategy(store));

        AlertHandler expiryCheck = new ExpiryCheckHandler(3);
        AlertHandler urgency = new UrgencyFlagHandler(20);
        AlertHandler chef = new ChefNotificationHandler(notificationService);
        AlertHandler managerNotify = new ManagerNotificationHandler(notificationService);
        expiryCheck.setNext(urgency).setNext(chef).setNext(managerNotify);

        AlertEventBus eventBus = new AlertEventBus();
        eventBus.subscribe(new ChefObserver());
        eventBus.subscribe(new ManagerObserver());

        ExpiryAlertService alertService = new ExpiryAlertService(manager, expiryCheck, eventBus);
        List<ExpiryAlertContext> alerts = alertService.evaluateAndNotify("tenant-alert");

        assertEquals(0, alerts.size());
    }
}
