package app.service;

import app.model.Ingredient;
import app.model.IngredientEvent;
import app.model.NotificationMessage;

public class LowStockAlertService implements IngredientEventListener {
    private final InventoryManager inventoryManager;
    private final NotificationService notificationService;

    public LowStockAlertService(InventoryManager inventoryManager, NotificationService notificationService) {
        this.inventoryManager = inventoryManager;
        this.notificationService = notificationService;
    }

    public void evaluateAllTenants() {
        for (String tenantId : inventoryManager.listKnownTenants()) {
            try {
                evaluateAndNotify(tenantId);
            } catch (RuntimeException ex) {
                System.err.println("[LowStockAlertService] sweep failed for tenant "
                        + tenantId + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public void onEvent(IngredientEvent event) {
        // Re-evaluate the ingredient immediately when it is used or updated so
        // low-stock notifications fire without waiting for the next daily sweep.
        Ingredient ingredient = null;
        if (event instanceof IngredientEvent.Used e) {
            ingredient = e.ingredient();
        } else if (event instanceof IngredientEvent.Updated e) {
            ingredient = e.ingredient();
        }
        if (ingredient != null) {
            checkAndNotify(ingredient);
        }
    }

    private void evaluateAndNotify(String tenantId) {
        for (Ingredient ingredient : inventoryManager.listIngredients(tenantId)) {
            checkAndNotify(ingredient);
        }
    }

    private void checkAndNotify(Ingredient ingredient) {
        if (ingredient.isDiscarded()) return;
        double qty = ingredient.getQuantity();
        double threshold = ingredient.getLowStockThreshold();
        if (threshold <= 0) return;
        if (qty <= threshold) {
            dispatch(ingredient);
        }
    }

    private void dispatch(Ingredient ingredient) {
        String name = ingredient.getName();
        String unit = ingredient.getUnit();
        double qty = ingredient.getQuantity();
        double threshold = ingredient.getLowStockThreshold();

        send(ingredient, "CHEF",
                "Low stock: " + name,
                String.format("Only %.2f %s remaining — reorder soon (threshold: %.2f).", qty, unit, threshold));
        send(ingredient, "MANAGER",
                "Stock advisory: " + name + " running low",
                String.format("%.2f %s remaining, below threshold of %.2f. Consider restocking.", qty, unit, threshold));
    }

    private void send(Ingredient ingredient, String role, String subject, String body) {
        try {
            notificationService.sendWithRetry(new NotificationMessage(
                    ingredient.getTenantId(),
                    ingredient.getId(),
                    role,
                    subject,
                    body));
        } catch (RuntimeException ex) {
            System.err.println("[LowStockAlertService] dispatch failed for "
                    + role + "/" + ingredient.getName() + ": " + ex.getMessage());
        }
    }
}
