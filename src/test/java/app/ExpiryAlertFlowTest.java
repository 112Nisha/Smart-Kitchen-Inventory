package app;

import app.model.ExpiryAlertContext;
import app.service.ExpiryAlertScheduler;
import app.service.IngredientStateTracker;
import app.service.StakeholderNotificationHandler;
import app.model.*;
import app.repository.InMemoryNotificationStore;
import app.repository.NotificationStore;
import app.repository.SqliteNotificationStore;
import app.service.DashboardNotificationStrategy;
import app.service.NotificationService;
import app.service.NotificationStrategy;
import app.repository.*;
import app.service.*;


import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpiryAlertFlowTest {
    @Test
    void nearExpiryItemProducesStakeholderNotification() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        manager.addIngredient(new Ingredient("tenant-alert", "Tomato", 30, "kg", LocalDate.now().plusDays(1), 5));

        InMemoryNotificationStore store = new InMemoryNotificationStore();
        NotificationService notificationService = new NotificationService(2);
        notificationService.registerStrategy(new DashboardNotificationStrategy(store));

        StakeholderNotificationHandler stakeholder = new StakeholderNotificationHandler(notificationService);
        ExpiryAlertService alertService = new ExpiryAlertService(manager, stakeholder);
        alertService.evaluateAndNotify("tenant-alert");

        boolean stakeholderMessageSent = store.all().stream()
                .anyMatch(msg -> msg.getRecipientRole().equals("CHEF") || msg.getRecipientRole().equals("MANAGER"));
        assertTrue(stakeholderMessageSent);
    }

    @Test
    void repeatedEvaluateDoesNotProduceDuplicateNotifications() {
        // End-to-end guard for the transition gate: a second evaluate on an
        // unchanged inventory must not create a second notification. Without
        // the gate, every UI refresh turned into a notification spam event.
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        manager.addIngredient(new Ingredient("tenant-alert", "Tomato", 30, "kg", LocalDate.now().plusDays(1), 5));

        InMemoryNotificationStore store = new InMemoryNotificationStore();
        NotificationService notificationService = new NotificationService(2);
        notificationService.registerStrategy(new DashboardNotificationStrategy(store));

        StakeholderNotificationHandler stakeholder = new StakeholderNotificationHandler(notificationService);
        ExpiryAlertService alertService = new ExpiryAlertService(manager, stakeholder);

        alertService.evaluateAndNotify("tenant-alert");
        int countAfterFirst = store.all().size();
        assertTrue(countAfterFirst > 0, "first evaluate should produce at least one notification");

        alertService.evaluateAndNotify("tenant-alert");
        alertService.evaluateAndNotify("tenant-alert");

        assertEquals(countAfterFirst, store.all().size(),
                "subsequent evaluates on unchanged inventory must not add notifications");
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
        notificationService.registerStrategy(new DashboardNotificationStrategy(store));

        StakeholderNotificationHandler stakeholder = new StakeholderNotificationHandler(notificationService);
        ExpiryAlertService alertService = new ExpiryAlertService(manager, stakeholder);
        List<ExpiryAlertContext> alerts = alertService.evaluateAndNotify("tenant-alert");

        assertEquals(0, alerts.size());
    }
}
