package app.service;

import app.model.IngredientEvent;

public interface IngredientEventListener {
    void onEvent(IngredientEvent event);
}
