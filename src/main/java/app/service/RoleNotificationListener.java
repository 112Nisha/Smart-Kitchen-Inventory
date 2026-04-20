package app.service;

import app.model.Ingredient;
import app.model.IngredientEvent;
import app.model.NotificationMessage;

public class RoleNotificationListener implements IngredientEventListener {
    private final NotificationService notificationService;

    public RoleNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onEvent(IngredientEvent event) {
        if (event instanceof IngredientEvent.Used e) {
            handleUsed(e.ingredient(), e.quantityUsed());
        } else if (event instanceof IngredientEvent.Discarded e) {
            handleDiscarded(e.ingredient());
        }
        // ConsumedToZero is a sub-event of Used — no separate notification needed
    }

    private void handleUsed(Ingredient ingredient, double quantityUsed) {
        double threshold = ingredient.getLowStockThreshold();
        if (threshold > 0 && ingredient.getQuantity() <= threshold) {
            // LowStockAlertService will fire a more specific alert for this event
            return;
        }

        String name = ingredient.getName();
        double remaining = ingredient.getQuantity();

        send(ingredient, "CHEF",
                "Ingredient used: " + name,
                String.format("%.2f %s used. %.2f %s remaining.",
                        quantityUsed, ingredient.getUnit(), remaining, ingredient.getUnit()));

        send(ingredient, "MANAGER",
                "Stock update: " + name,
                String.format("%.2f %s consumed. Current stock: %.2f %s.",
                        quantityUsed, ingredient.getUnit(), remaining, ingredient.getUnit()));
    }

    private void handleDiscarded(Ingredient ingredient) {
        String name = ingredient.getName();

        // Chef: simple confirmation of the discard action
        send(ingredient, "CHEF",
                "Ingredient discarded: " + name,
                name + " has been marked as discarded.");

        // Manager: waste visibility for reporting
        send(ingredient, "MANAGER",
                "Waste alert: " + name + " discarded",
                name + " was discarded. Check waste report for details.");
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
            System.err.println("[RoleNotificationListener] dispatch failed for "
                    + role + "/" + ingredient.getName() + ": " + ex.getMessage());
        }
    }
}
