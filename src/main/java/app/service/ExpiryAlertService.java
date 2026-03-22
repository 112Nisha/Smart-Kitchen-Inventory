package app.service;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class ExpiryAlertService {
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
            if (!ingredient.getState().shouldTriggerExpiryAlert()) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(LocalDate.now(), ingredient.getExpiryDate());
            ExpiryAlertContext context = new ExpiryAlertContext(ingredient, days);
            alertChain.handle(context);
            eventBus.publish(context);
            contexts.add(context);
        }
        return contexts;
    }
}
