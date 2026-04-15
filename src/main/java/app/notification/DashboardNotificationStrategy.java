package app.notification;

import app.model.NotificationMessage;

/**
 * Persists notifications to the configured {@link NotificationStore} so the
 * dashboard (/notifications, /expiry-alerts) can surface them. The DB is the
 * sole delivery channel today — email/SMS/push can be added later as
 * additional {@link NotificationStrategy} implementations without touching
 * {@link NotificationService}.
 */
public class DashboardNotificationStrategy implements NotificationStrategy {
    private final NotificationStore notificationStore;

    public DashboardNotificationStrategy(NotificationStore notificationStore) {
        this.notificationStore = notificationStore;
    }

    @Override
    public void send(NotificationMessage message) {
        notificationStore.save(message);
    }
}
