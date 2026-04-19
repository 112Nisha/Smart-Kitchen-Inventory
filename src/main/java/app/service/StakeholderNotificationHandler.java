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
    private final NotificationService notificationService;

    public StakeholderNotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyStakeholder(ExpiryAlertContext context) {
        long days = context.getDaysUntilExpiry();
        boolean expired = days <= 0;
        String name = context.getIngredient().getName();

        String chefSubject = expired ? "Ingredient expired: " + name : "Ingredient nearing expiry: " + name;
        String chefBody = expired ? "Discard immediately." : "Use within " + days + " day(s).";

        String managerSubject = expired ? "Stock alert — expired: " + name : "Stock advisory: " + name + " expiring soon";
        String managerBody = expired ? name + " has expired — check waste report." : name + " expires in " + days + " day(s).";

        send(context, "CHEF", chefSubject, chefBody);
        send(context, "MANAGER", managerSubject, managerBody);
        context.addEvent("StakeholderNotificationHandler: chef and manager notified");
    }

    private void send(ExpiryAlertContext context, String role, String subject, String body) {
        notificationService.sendWithRetry(new NotificationMessage(
                context.getIngredient().getTenantId(),
                context.getIngredient().getId(),
                role,
                subject,
                body));
    }
}
