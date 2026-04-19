package app;

import app.model.ExpiryAlertContext;
import app.service.ExpiryAlertScheduler;
import app.service.ExpiryAlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Fix 6: ExpiryAlertScheduler runs evaluateAllTenants() on a fixed
 * cadence and is safe under double-start / double-stop / mid-sweep exceptions.
 *
 * Tests use a fake ExpiryAlertService subclass that counts invocations,
 * avoiding any dependency on InventoryManager / AlertEventBus wiring. The
 * TimeUnit.MILLISECONDS constructor keeps the suite fast — without it each
 * scheduler test would take multiple seconds.
 */
class ExpiryAlertSchedulerTest {
    /**
     * Minimal ExpiryAlertService stand-in. Passes nulls into super() since the
     * override ensures those fields are never touched. If a future change to
     * ExpiryAlertService starts dereferencing them in the constructor, this
     * pattern will have to change — but keeping the fake lean is worth the
     * small coupling.
     */
    private static class CountingService extends ExpiryAlertService {
        final AtomicInteger sweepCount = new AtomicInteger();
        private final RuntimeException failure;

        CountingService() { this(null); }
        CountingService(RuntimeException failure) {
            super(null, null);
            this.failure = failure;
        }

        @Override
        public Map<String, List<ExpiryAlertContext>> evaluateAllTenants() {
            sweepCount.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return Map.of();
        }
    }

    private ExpiryAlertScheduler scheduler;

    @AfterEach
    void tearDown() {
        // Always stop so a failed assertion does not leave a daemon timer
        // running into the next test.
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    void scheduledSweepInvokesEvaluateAllTenantsRepeatedly() throws InterruptedException {
        CountingService service = new CountingService();
        // 20ms interval, 0ms initial delay so the test finishes in well under a
        // second even with scheduler jitter.
        scheduler = new ExpiryAlertScheduler(service, 20L, 0L, TimeUnit.MILLISECONDS);
        scheduler.start();

        // Wait long enough to see ~10 ticks. Lower-bound assertion of 3 keeps
        // the test robust against CI scheduler slowness — the point is to
        // verify it fires more than once, not to measure cadence exactly.
        Thread.sleep(200);
        scheduler.stop();

        assertTrue(service.sweepCount.get() >= 3,
                "expected multiple sweeps within 200ms, got " + service.sweepCount.get());
    }

    @Test
    void stopHaltsFurtherSweeps() throws InterruptedException {
        CountingService service = new CountingService();
        scheduler = new ExpiryAlertScheduler(service, 20L, 0L, TimeUnit.MILLISECONDS);
        scheduler.start();
        Thread.sleep(100);
        scheduler.stop();

        int countAfterStop = service.sweepCount.get();
        Thread.sleep(200);

        // No ticks should have fired after stop() returned.
        assertEquals(countAfterStop, service.sweepCount.get(),
                "sweepCount should not grow after stop()");
    }

    @Test
    void doubleStartIsIdempotent() throws InterruptedException {
        CountingService service = new CountingService();
        scheduler = new ExpiryAlertScheduler(service, 20L, 0L, TimeUnit.MILLISECONDS);
        scheduler.start();
        scheduler.start(); // second start should be a no-op, not spawn a second timer
        Thread.sleep(150);
        scheduler.stop();

        // If double-start actually spawned two executors, we'd see roughly
        // double the tick rate. 20ms interval over 150ms is ~7-8 ticks of
        // a single timer; we allow a generous upper bound for jitter but
        // exclude the "two executors = 15+ ticks" scenario.
        assertTrue(service.sweepCount.get() < 14,
                "double-start should not double the tick rate, got " + service.sweepCount.get());
    }

    @Test
    void stopWithoutStartIsSafe() {
        scheduler = new ExpiryAlertScheduler(new CountingService(), 20L, 0L, TimeUnit.MILLISECONDS);
        scheduler.stop(); // should not throw
    }

    @Test
    void doubleStopIsSafe() throws InterruptedException {
        scheduler = new ExpiryAlertScheduler(new CountingService(), 20L, 0L, TimeUnit.MILLISECONDS);
        scheduler.start();
        Thread.sleep(50);
        scheduler.stop();
        scheduler.stop(); // should not throw
    }

    @Test
    void exceptionFromSweepDoesNotCancelFutureSweeps() throws InterruptedException {
        // ScheduledExecutorService cancels a recurring task if its Runnable
        // throws. runOnce() wraps in try/catch specifically to prevent this —
        // the test verifies that guarantee.
        CountingService service = new CountingService(new RuntimeException("boom"));
        scheduler = new ExpiryAlertScheduler(service, 20L, 0L, TimeUnit.MILLISECONDS);
        scheduler.start();

        Thread.sleep(200);
        scheduler.stop();

        // Without the try/catch, sweepCount would be exactly 1 (the first tick
        // throws, the executor cancels the task). With it, we expect many.
        assertTrue(service.sweepCount.get() >= 3,
                "sweep should keep running after an exception, got " + service.sweepCount.get());
    }

    @Test
    void nonPositiveIntervalRejected() {
        CountingService service = new CountingService();
        assertThrows(IllegalArgumentException.class,
                () -> new ExpiryAlertScheduler(service, 0L, 0L, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpiryAlertScheduler(service, -5L, 0L, TimeUnit.MILLISECONDS));
    }

    @Test
    void negativeInitialDelayRejected() {
        CountingService service = new CountingService();
        assertThrows(IllegalArgumentException.class,
                () -> new ExpiryAlertScheduler(service, 20L, -1L, TimeUnit.MILLISECONDS));
    }

    @Test
    void nullServiceRejected() {
        assertThrows(NullPointerException.class,
                () -> new ExpiryAlertScheduler(null, 20L, 0L, TimeUnit.MILLISECONDS));
    }

    @Test
    void initialDelayDelaysFirstTick() throws InterruptedException {
        // With a 100ms initial delay, no sweep should have happened at t=30ms.
        CountingService service = new CountingService();
        scheduler = new ExpiryAlertScheduler(service, 20L, 100L, TimeUnit.MILLISECONDS);
        scheduler.start();
        Thread.sleep(30);

        assertEquals(0, service.sweepCount.get(),
                "no sweep should have fired before the initial delay elapsed");

        Thread.sleep(150); // now well past initial delay + a few interval ticks
        assertTrue(service.sweepCount.get() >= 1,
                "first tick should have fired after the initial delay");
    }
}
