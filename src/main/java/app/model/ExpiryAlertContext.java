package app.model;

import app.model.Ingredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExpiryAlertContext {
    private final Ingredient ingredient;
    private final long daysUntilExpiry;
    private final List<String> events = new ArrayList<>();

    public ExpiryAlertContext(Ingredient ingredient, long daysUntilExpiry) {
        this.ingredient = ingredient;
        this.daysUntilExpiry = daysUntilExpiry;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public long getDaysUntilExpiry() {
        return daysUntilExpiry;
    }

    public void addEvent(String event) {
        events.add(event);
    }

    public List<String> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
