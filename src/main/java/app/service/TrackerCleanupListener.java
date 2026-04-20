package app.service;

import app.model.IngredientEvent;

public class TrackerCleanupListener implements IngredientEventListener {
    private final IngredientStateTracker stateTracker;

    public TrackerCleanupListener(IngredientStateTracker stateTracker) {
        this.stateTracker = stateTracker;
    }

    @Override
    public void onEvent(IngredientEvent event) {
        if (event instanceof IngredientEvent.Discarded e) {
            stateTracker.forget(e.ingredient().getId());
        } else if (event instanceof IngredientEvent.ConsumedToZero e) {
            stateTracker.forget(e.ingredient().getId());
        } else if (event instanceof IngredientEvent.Updated e) {
            stateTracker.forget(e.ingredient().getId());
        }
    }
}
