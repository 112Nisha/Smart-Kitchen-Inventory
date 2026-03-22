package app.notification;

import app.model.NotificationMessage;

import java.util.ArrayList;
import java.util.List;

public class NotificationService {
    private final List<NotificationStrategy> strategies = new ArrayList<>();
    private final int maxRetries;

    public NotificationService(int maxRetries) {
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries must be >= 1");
        }
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
