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
import app.state.*;
import app.web.*;


import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatePatternTest {
    @Test
    void ingredientMovesAcrossLifecycleStates() {
        Ingredient ingredient = new Ingredient("tenant-x", "Tomato", 2, "kg", LocalDate.now().plusDays(2), 1);
        ingredient.refreshState(LocalDate.now(), 3);
        assertEquals(IngredientLifecycle.NEAR_EXPIRY, ingredient.getLifecycle());

        ingredient.setExpiryDate(LocalDate.now().minusDays(1));
        ingredient.refreshState(LocalDate.now(), 3);
        assertEquals(IngredientLifecycle.EXPIRED, ingredient.getLifecycle());

        ingredient.setDiscarded(true);
        ingredient.refreshState(LocalDate.now(), 3);
        assertEquals(IngredientLifecycle.DISCARDED, ingredient.getLifecycle());
    }
}
