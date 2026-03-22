package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExpiryAlertContext {
    private final Ingredient ingredient;
    private final long daysUntilExpiry;
    private final List<String> events = new ArrayList<>();
    private boolean urgent;

    public ExpiryAlertContext(Ingredient ingredient, long daysUntilExpiry) {
        this.ingredient = ingredient;
        this.daysUntilExpiry = daysUntilExpiry;
        this.urgent = false;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public long getDaysUntilExpiry() {
        return daysUntilExpiry;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public void addEvent(String event) {
        events.add(event);
    }

    public List<String> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
