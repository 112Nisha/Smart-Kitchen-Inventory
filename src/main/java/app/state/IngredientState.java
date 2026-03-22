package app.state;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public interface IngredientState {
    IngredientLifecycle getLifecycle();

    boolean canRecommendInDish();

    boolean shouldTriggerExpiryAlert();
}
