package app.service;

import app.repository.NotificationStore;
import app.service.ExpiryAlertService;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

/**
 * Runs ExpiryAlertService.evaluateAllTenants() on a fixed cadence so the
 * alert pipeline fires even when no user is visiting /expiry-alerts.
 *
 * This is the concrete piece of FR4 ("automated monitoring on a configurable
 * schedule"): without this class, alerts only fire on HTTP request, which
 * defeats the point of scheduled monitoring.
 *
 * Designed to be owned by the application lifecycle (AppContextListener):
 * start() is called on context init, stop() on context destroyed.
 */
public class ExpiryAlertScheduler {
    private final ExpiryAlertService alertService;
    private LowStockAlertService lowStockAlertService;
    // How often to sweep, in whatever time unit was supplied. Stored as raw
    // long + TimeUnit so tests can use milliseconds while production uses
    // seconds — no unit conversion happens until scheduleAtFixedRate is called.
    private final long interval;
    // Delay before the FIRST tick. We don't want the sweep to run during
    // context init (the app isn't fully warm yet), so default to the same
    // value as the interval — first tick happens one interval after start.
    private final long initialDelay;
    private final TimeUnit unit;

    // Lazily created so stop() can be called safely even if start() never was.
    private ScheduledExecutorService executor;
    // Guards against double-start / double-stop. AtomicBoolean keeps the
    // guard lock-free and safe under concurrent lifecycle calls (unlikely in
    // practice but cheap insurance).
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Retention pruning — optional. When wired, the scheduler runs a prune
    // once per calendar day (regardless of sweep cadence) to keep the
    // notifications table from growing unbounded. Null store = pruning disabled,
    // which is the default for tests that only exercise the sweep path.
    // retentionDaysSupplier is resolved at prune time so the operator can
    // retune the window live from the admin config page.
    private final NotificationStore retentionStore;
    private final IntSupplier retentionDaysSupplier;
    // Tracks the last day a prune ran so we skip pruning on every 60s tick —
    // the prune only needs to happen when the calendar day actually advances.
    private final AtomicReference<LocalDate> lastPruneDay = new AtomicReference<>();

    public ExpiryAlertScheduler(ExpiryAlertService alertService, long intervalSeconds) {
        // Default initial delay = one interval, so the first sweep happens
        // after the app has had time to settle.
        this(alertService, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public ExpiryAlertScheduler(ExpiryAlertService alertService, long intervalSeconds, long initialDelaySeconds) {
        this(alertService, intervalSeconds, initialDelaySeconds, TimeUnit.SECONDS);
    }

    public ExpiryAlertScheduler(ExpiryAlertService alertService, long interval, long initialDelay, TimeUnit unit) {
        this(alertService, interval, initialDelay, unit, null, null);
    }

    public ExpiryAlertScheduler(ExpiryAlertService alertService, long interval, long initialDelay, TimeUnit unit,
                                NotificationStore retentionStore, int retentionDays) {
        this(alertService, interval, initialDelay, unit, retentionStore,
                retentionStore == null ? null : (IntSupplier) () -> retentionDays);
        if (retentionStore != null && retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be > 0 when a retentionStore is provided");
        }
    }

    /**
     * Primary constructor. Accepts a TimeUnit so tests can exercise the
     * scheduler at millisecond cadence without waiting real seconds, plus
     * optional retention wiring: when {@code retentionStore} is non-null the
     * scheduler runs a day-scoped prune once per calendar day against that
     * store, reading the window from {@code retentionDaysSupplier} so it
     * picks up admin-page retunes without a restart.
     */
    public ExpiryAlertScheduler(ExpiryAlertService alertService, long interval, long initialDelay, TimeUnit unit,
                                NotificationStore retentionStore, IntSupplier retentionDaysSupplier) {
        this.alertService = Objects.requireNonNull(alertService, "alertService is required");
        this.unit = Objects.requireNonNull(unit, "unit is required");
        if (interval <= 0) {
            // scheduleAtFixedRate rejects <=0 periods; fail fast with a clear message.
            throw new IllegalArgumentException("interval must be > 0");
        }
        if (initialDelay < 0) {
            throw new IllegalArgumentException("initialDelay must be >= 0");
        }
        if (retentionStore != null && retentionDaysSupplier == null) {
            throw new IllegalArgumentException("retentionDaysSupplier is required when a retentionStore is provided");
        }
        this.interval = interval;
        this.initialDelay = initialDelay;
        this.retentionStore = retentionStore;
        this.retentionDaysSupplier = retentionDaysSupplier;
    }

    /**
     * Start the background sweep. Safe to call once; subsequent calls are
     * no-ops until stop() is called.
     */
    public void start() {
        // compareAndSet ensures only the first caller actually starts the
        // executor; any concurrent second caller sees running==true and exits.
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Single-thread executor: the sweep is lightweight (transition-gated,
        // so most evaluations are no-ops) and serialising ticks avoids two
        // sweeps overlapping if one runs long. Daemon thread so it does not
        // block JVM shutdown if stop() is somehow skipped.
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "expiry-alert-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        // fixed-rate semantics: each tick starts interval units after the
        // previous tick STARTED. If a tick overruns, the next one runs back
        // to back. Acceptable given how cheap the sweep is post-Fix 5.
        executor.scheduleAtFixedRate(this::runOnce, initialDelay, interval, unit);
    }

    /**
     * Stop the background sweep. Safe to call more than once; safe to call
     * even if start() was never called. Waits briefly for the in-flight tick
     * to finish so we don't leave the notification store mid-write.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            // Give the in-flight tick up to 5 seconds to finish cleanly.
            // If it doesn't, forceful shutdown — we're in app teardown and
            // cannot block the container.
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            // Restore the interrupt flag and give up waiting; the container
            // is already tearing us down.
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void setLowStockAlertService(LowStockAlertService lowStockAlertService) {
        this.lowStockAlertService = lowStockAlertService;
    }

    private void runOnce() {
        try {
            alertService.evaluateAllTenants();
        } catch (RuntimeException ex) {
            System.err.println("[ExpiryAlertScheduler] expiry sweep failed: " + ex.getMessage());
        }
        if (lowStockAlertService != null) {
            try {
                lowStockAlertService.evaluateAllTenants();
            } catch (RuntimeException ex) {
                System.err.println("[ExpiryAlertScheduler] low-stock sweep failed: " + ex.getMessage());
            }
        }
        pruneIfNewDay();
    }

    // Runs at most once per calendar day. Guarded by a compareAndSet on
    // lastPruneDay so two ticks on the same day can't both prune. Failures
    // are logged, never thrown — same reasoning as the sweep above.
    private void pruneIfNewDay() {
        if (retentionStore == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate previous = lastPruneDay.get();
        if (today.equals(previous)) {
            return;
        }
        if (!lastPruneDay.compareAndSet(previous, today)) {
            return;
        }
        try {
            int retentionDays = retentionDaysSupplier.getAsInt();
            if (retentionDays <= 0) {
                // Live-config could legitimately be mid-update; skip this tick
                // rather than feeding a negative cutoff into the store.
                return;
            }
            int removed = retentionStore.pruneOlderThan(today.minusDays(retentionDays));
            if (removed > 0) {
                System.out.println("[ExpiryAlertScheduler] pruned " + removed
                        + " notifications older than " + retentionDays + " days");
            }
        } catch (RuntimeException ex) {
            System.err.println("[ExpiryAlertScheduler] prune failed: " + ex.getMessage());
        }
    }
}
