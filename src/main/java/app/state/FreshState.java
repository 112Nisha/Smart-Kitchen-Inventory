package app.state;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public class FreshState implements IngredientState {
    @Override
    public IngredientLifecycle getLifecycle() {
        return IngredientLifecycle.FRESH;
    }

    @Override
    public boolean canRecommendInDish() {
        return true;
    }

    @Override
    public boolean shouldTriggerExpiryAlert() {
        return false;
    }
}
