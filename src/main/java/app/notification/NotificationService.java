package app.notification;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.ArrayList;
import java.util.List;

public class NotificationService {
    private final List<NotificationStrategy> strategies = new ArrayList<>();
    private final int maxRetries;

    public NotificationService(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void registerStrategy(NotificationStrategy strategy) {
        strategies.add(strategy);
    }

    public void sendWithRetry(NotificationMessage message) {
        for (NotificationStrategy strategy : strategies) {
            int attempt = 0;
            while (attempt < maxRetries) {
                try {
                    strategy.send(message);
                    break;
                } catch (RuntimeException ex) {
                    attempt++;
                    if (attempt >= maxRetries) {
                        throw ex;
                    }
                }
            }
        }
    }
}
