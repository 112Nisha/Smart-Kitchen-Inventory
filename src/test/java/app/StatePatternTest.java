package app;

import app.alerts.*;
import app.model.*;
import app.notification.*;
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
