package app;

import app.model.ExpiryAlertContext;
import app.service.StakeholderNotificationHandler;
import app.model.Ingredient;
import app.service.NotificationService;
import app.repository.IngredientRepository;
import app.service.ExpiryAlertService;
import app.service.InventoryManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiryAlertServiceResilienceTest {
    @Test
    void notificationFailureDoesNotAbortAlertEvaluation() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        manager.addIngredient(new Ingredient("tenant-resilience", "Basil", 1, "kg", LocalDate.now().plusDays(1), 1));

        NotificationService notificationService = new NotificationService(2);
        notificationService.registerStrategy(message -> {
            throw new RuntimeException("Simulated provider outage");
        });

        StakeholderNotificationHandler stakeholder = new StakeholderNotificationHandler(notificationService);
        ExpiryAlertService service = new ExpiryAlertService(manager, stakeholder);

        List<ExpiryAlertContext> contexts = service.evaluateAndNotify("tenant-resilience");

        assertEquals(1, contexts.size());
        assertTrue(contexts.get(0).getEvents().stream().anyMatch(event -> event.contains("Alert processing failed")));
    }
}
