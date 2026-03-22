package app.notification;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryNotificationStore {
    private final List<NotificationMessage> notifications = Collections.synchronizedList(new ArrayList<>());

    public void save(NotificationMessage message) {
        notifications.add(message);
    }

    public List<NotificationMessage> all() {
        synchronized (notifications) {
            return List.copyOf(notifications);
        }
    }
}
