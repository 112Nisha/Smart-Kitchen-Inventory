package app.service;

import app.model.NotificationMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationService {
    private final List<NotificationStrategy> strategies = new ArrayList<>();
    private final int maxRetries;
    // Base delay (ms) used for exponential back-off between retry attempts (NFR4).
    private final long backoffBaseMillis;

    public NotificationService(int maxRetries) {
        this(maxRetries, 10L);
    }

    public NotificationService(int maxRetries, long backoffBaseMillis) {
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries must be >= 1");
        }
        if (backoffBaseMillis < 0) {
            throw new IllegalArgumentException("backoffBaseMillis must be >= 0");
        }
        this.maxRetries = maxRetries;
        this.backoffBaseMillis = backoffBaseMillis;
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
                    // Apply exponential back-off BETWEEN attempts (not after the
                    // final attempt). This satisfies NFR4: retries use an
                    // increasing delay rather than hammering the downstream.
                    if (attempt < maxRetries) {
                        sleepForBackoff(attempt);
                    }
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

    private void sleepForBackoff(int attempt) {
        // Skip sleeping when base is zero — useful for tests that want to
        // exercise the retry path without paying real wall-clock time.
        if (backoffBaseMillis == 0L) {
            return;
        }
        // Delay = base * 2^(attempt-1): first retry waits `base`, second
        // waits 2*base, third waits 4*base, etc.
        long delay = backoffBaseMillis * (1L << (attempt - 1));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            // Preserve interrupt status so callers/executors can react; we
            // simply abandon the remaining back-off and let the next attempt
            // proceed immediately.
            Thread.currentThread().interrupt();
        }
    }
}
