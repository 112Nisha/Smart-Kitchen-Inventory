package app.service;

import app.alerts.AlertEventBus;
import app.alerts.AlertHandler;
import app.alerts.ExpiryAlertContext;
import app.model.Ingredient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class ExpiryAlertService {
    private static final double MIN_ALERTABLE_QUANTITY = 1e-9;

    private final InventoryManager inventoryManager;
    private final AlertHandler alertChain;
    private final AlertEventBus eventBus;

    public ExpiryAlertService(InventoryManager inventoryManager, AlertHandler alertChain, AlertEventBus eventBus) {
        this.inventoryManager = inventoryManager;
        this.alertChain = alertChain;
        this.eventBus = eventBus;
    }

    public List<ExpiryAlertContext> evaluateAndNotify(String tenantId) {
        List<ExpiryAlertContext> contexts = new ArrayList<>();

        for (Ingredient ingredient : inventoryManager.listIngredients(tenantId)) {
            if (ingredient.getQuantity() <= MIN_ALERTABLE_QUANTITY) {
                continue;
            }
            if (!ingredient.getState().shouldTriggerExpiryAlert()) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(LocalDate.now(), ingredient.getExpiryDate());
            ExpiryAlertContext context = new ExpiryAlertContext(ingredient, days);
            try {
                alertChain.handle(context);
                eventBus.publish(context);
            } catch (RuntimeException ex) {
                context.addEvent("Alert processing failed: " + ex.getMessage());
            }
            contexts.add(context);
        }
        return contexts;
    }
}
