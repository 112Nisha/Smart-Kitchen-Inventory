package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public class ChefNotificationHandler extends AlertHandler {
    private final NotificationService notificationService;

    public ChefNotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    protected void process(ExpiryAlertContext context) {
        NotificationMessage message = new NotificationMessage(
                context.getIngredient().getTenantId(),
                "CHEF",
                "Ingredient nearing expiry: " + context.getIngredient().getName(),
                "Use within " + context.getDaysUntilExpiry() + " day(s)."
        );
        notificationService.sendWithRetry(message);
        context.addEvent("ChefNotificationHandler: Chef notified");
    }
}
