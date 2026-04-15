package app.service;

import app.model.ExpiryAlertContext;
import app.model.NotificationMessage;

/**
 * Single stakeholder notifier for the expiry-alert pipeline. Replaces the
 * earlier chef/manager split — there is now one audience for near-expiry and
 * expired alerts, so there is no escalation window and no duplicate
 * notifications per item.
 */
public class StakeholderNotificationHandler {
    public static final String ROLE = "STAKEHOLDER";

    private final NotificationService notificationService;

    public StakeholderNotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyStakeholder(ExpiryAlertContext context) {
        long days = context.getDaysUntilExpiry();
        boolean expired = days <= 0;
        String name = context.getIngredient().getName();
        String subject = expired ? "Ingredient expired: " + name : "Ingredient nearing expiry: " + name;
        String body = expired ? "Discard immediately." : "Use within " + days + " day(s).";

        NotificationMessage message = new NotificationMessage(
                context.getIngredient().getTenantId(),
                context.getIngredient().getId(),
                ROLE,
                subject,
                body
        );
        notificationService.sendWithRetry(message);
        context.addEvent("StakeholderNotificationHandler: stakeholder notified");
    }
}
