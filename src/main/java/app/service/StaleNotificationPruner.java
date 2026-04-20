package app.service;

import app.model.IngredientEvent;
import app.repository.NotificationStore;

public class StaleNotificationPruner implements IngredientEventListener {
    private final NotificationStore notificationStore;

    public StaleNotificationPruner(NotificationStore notificationStore) {
        this.notificationStore = notificationStore;
    }

    @Override
    public void onEvent(IngredientEvent event) {
        if (event instanceof IngredientEvent.Updated e) {
            notificationStore.pruneByIngredient(
                    e.ingredient().getTenantId(),
                    e.ingredient().getId());
        }
    }
}
