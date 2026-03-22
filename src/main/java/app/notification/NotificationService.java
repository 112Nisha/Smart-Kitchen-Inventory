package app.notification;

import app.model.NotificationMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        strategies.add(Objects.requireNonNull(strategy, "strategy is required"));
    }

    public void sendWithRetry(NotificationMessage message) {
        if (strategies.isEmpty()) {
            throw new IllegalStateException("No notification strategies are configured");
        }

        RuntimeException lastFailure = null;
        boolean delivered = false;

        for (NotificationStrategy strategy : strategies) {
            int attempt = 0;
            while (attempt < maxRetries) {
                try {
                    strategy.send(message);
                    delivered = true;
                    break;
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    attempt++;
                }
            }
        }

        if (!delivered) {
            if (lastFailure != null) {
                throw lastFailure;
            }
            throw new IllegalStateException("Notification delivery failed");
        }
    }
}
