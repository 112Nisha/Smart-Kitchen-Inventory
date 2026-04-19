package app.service;

import app.model.Ingredient;
import app.model.NotificationMessage;

import java.util.HashMap;
import java.util.Map;

public class LowStockAlertService {
    private final InventoryManager inventoryManager;
    private final NotificationService notificationService;
    // Tracks which ingredients already have a low-stock notification fired today
    // to avoid re-firing on every sweep until quantity rises above threshold.
    private final Map<String, Double> lastAlertedQuantity = new HashMap<>();

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

    private void evaluateAndNotify(String tenantId) {
        for (Ingredient ingredient : inventoryManager.listIngredients(tenantId)) {
            if (ingredient.isDiscarded()) continue;
            double qty = ingredient.getQuantity();
            double threshold = ingredient.getLowStockThreshold();
            if (threshold <= 0) continue;

            boolean isLow = qty <= threshold;
            Double prev = lastAlertedQuantity.get(ingredient.getId());

            // Fire when crossing into low-stock. Re-fire if quantity dropped further.
            if (isLow && (prev == null || qty < prev)) {
                lastAlertedQuantity.put(ingredient.getId(), qty);
                dispatch(ingredient);
            } else if (!isLow) {
                lastAlertedQuantity.remove(ingredient.getId());
            }
        }
    }

    private void dispatch(Ingredient ingredient) {
        String subject = "Low stock: " + ingredient.getName();
        String body = String.format("Only %.2f %s remaining (threshold: %.2f).",
                ingredient.getQuantity(), ingredient.getUnit(), ingredient.getLowStockThreshold());
        NotificationMessage message = new NotificationMessage(
                ingredient.getTenantId(),
                ingredient.getId(),
                "STAKEHOLDER",
                subject,
                body
        );
        try {
            notificationService.sendWithRetry(message);
        } catch (RuntimeException ex) {
            System.err.println("[LowStockAlertService] dispatch failed for "
                    + ingredient.getName() + ": " + ex.getMessage());
        }
    }
}
