package app.state;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public class DiscardedState implements IngredientState {
    @Override
    public IngredientLifecycle getLifecycle() {
        return IngredientLifecycle.DISCARDED;
    }

    @Override
    public boolean canRecommendInDish() {
        return false;
    }

    @Override
    public boolean shouldTriggerExpiryAlert() {
        return false;
    }
}
