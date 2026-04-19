package app;

import app.model.NotificationMessage;
import app.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Fix 1: NotificationService applies exponential back-off between
 * retry attempts (NFR4). Without these tests, a future change could remove
 * the Thread.sleep call and the retry path would silently become busy-looping
 * again — the existing resilience test would still pass because it only
 * verifies that a successful strategy eventually delivers.
 */
class NotificationServiceBackoffTest {
    @Test
    void retriesAreSpacedByExponentialBackoff() {
        long baseMillis = 50L;
        int maxRetries = 3;
        NotificationService service = new NotificationService(maxRetries, baseMillis);

        List<Long> attemptTimestamps = new ArrayList<>();
        service.registerStrategy(message -> {
            attemptTimestamps.add(System.nanoTime());
            throw new RuntimeException("always fails");
        });

        assertThrows(RuntimeException.class,
                () -> service.sendWithRetry(new NotificationMessage("t", "ing-1", "CHEF", "S", "B")));

        assertEquals(maxRetries, attemptTimestamps.size());

        long gap1Ms = (attemptTimestamps.get(1) - attemptTimestamps.get(0)) / 1_000_000;
        long gap2Ms = (attemptTimestamps.get(2) - attemptTimestamps.get(1)) / 1_000_000;

        // gap1 should be roughly baseMillis (50ms). Lower bound is loose to
        // survive CI scheduler jitter; without backoff the gap would be <5ms.
        assertTrue(gap1Ms >= 30, "gap1 should reflect backoff, was " + gap1Ms + "ms");
        // gap2 should be roughly 2*baseMillis. Again lower bound is loose.
        assertTrue(gap2Ms >= 80, "gap2 should reflect doubled backoff, was " + gap2Ms + "ms");
        // Exponential growth: each retry waits longer than the previous one.
        assertTrue(gap2Ms > gap1Ms,
                "expected exponential growth, gap1=" + gap1Ms + "ms gap2=" + gap2Ms + "ms");
    }

    @Test
    void zeroBaseDelaySkipsSleep() {
        NotificationService service = new NotificationService(5, 0L);
        service.registerStrategy(message -> {
            throw new RuntimeException("fail");
        });

        long start = System.nanoTime();
        assertThrows(RuntimeException.class,
                () -> service.sendWithRetry(new NotificationMessage("t", "ing-1", "CHEF", "S", "B")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // With zero base delay and 5 failing attempts, the whole thing should
        // complete near-instantly. Generous upper bound for CI.
        assertTrue(elapsedMs < 100,
                "zero-base-delay retries should be fast, elapsed=" + elapsedMs + "ms");
    }

    @Test
    void singleAttemptHasNoBackoffSleep() {
        // maxRetries=1 means only one attempt is made — the sleepForBackoff
        // guard (attempt < maxRetries) should prevent any delay at all.
        NotificationService service = new NotificationService(1, 500L);
        service.registerStrategy(message -> {
            throw new RuntimeException("fail");
        });

        long start = System.nanoTime();
        assertThrows(RuntimeException.class,
                () -> service.sendWithRetry(new NotificationMessage("t", "ing-1", "CHEF", "S", "B")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Without the maxRetries guard this would sleep 500ms before throwing.
        assertTrue(elapsedMs < 100,
                "single-attempt should not trigger backoff sleep, elapsed=" + elapsedMs + "ms");
    }

    @Test
    void negativeBackoffRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationService(3, -1L));
    }
}
