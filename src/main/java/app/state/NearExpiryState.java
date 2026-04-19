package app.state;

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



public class NearExpiryState implements IngredientState {
    @Override
    public IngredientLifecycle getLifecycle() {
        return IngredientLifecycle.NEAR_EXPIRY;
    }

    @Override
    public boolean canRecommendInDish() {
        return true;
    }

    @Override
    public boolean shouldTriggerExpiryAlert() {
        return true;
    }
}
