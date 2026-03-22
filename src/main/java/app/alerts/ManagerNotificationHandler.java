package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public class ManagerNotificationHandler extends AlertHandler {
    private final NotificationService notificationService;

    public ManagerNotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    protected void process(ExpiryAlertContext context) {
        if (!context.isUrgent()) {
            context.addEvent("ManagerNotificationHandler: Escalation skipped");
            return;
        }
        NotificationMessage message = new NotificationMessage(
                context.getIngredient().getTenantId(),
                "MANAGER",
                "URGENT expiry alert: " + context.getIngredient().getName(),
                "Ingredient requires immediate use/disposal. Days left: " + context.getDaysUntilExpiry()
        );
        notificationService.sendWithRetry(message);
        context.addEvent("ManagerNotificationHandler: Manager notified");
    }
}
