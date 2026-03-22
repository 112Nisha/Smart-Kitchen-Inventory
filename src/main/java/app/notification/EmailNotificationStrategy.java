package app.notification;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public class EmailNotificationStrategy implements NotificationStrategy {
    private final InMemoryNotificationStore notificationStore;

    public EmailNotificationStrategy(InMemoryNotificationStore notificationStore) {
        this.notificationStore = notificationStore;
    }

    @Override
    public void send(NotificationMessage message) {
        notificationStore.save(message);
    }
}
