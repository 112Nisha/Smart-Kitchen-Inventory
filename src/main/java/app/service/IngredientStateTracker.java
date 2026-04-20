package app.service;

import app.model.IngredientLifecycle;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last-seen lifecycle AND the day it was seen per ingredient,
 * so the alert pipeline fires on:
 *   - first sighting of an alertable lifecycle,
 *   - genuine lifecycle transitions into an alertable state,
 *   - or a new calendar day rolling over while the item is still alertable
 *     (daily re-nag so a multi-day near-expiry item keeps showing up in the
 *     notifications log instead of going quiet after day 1).
 *
 * Same-day repeats with no state change are suppressed — that's the
 * "refresh = spam" fix. The SQLite store's UNIQUE(dedup_key) remains a
 * backstop against duplicates within a day.
 */
public class IngredientStateTracker {
    private final Map<String, Entry> lastSeenByIngredient = new ConcurrentHashMap<>();

    public boolean recordAndCheckTransition(String ingredientId, IngredientLifecycle current) {
        return recordAndCheckTransition(ingredientId, current, LocalDate.now());
    }

    // Public overload so callers (and tests) can pin a specific "today"
    // without a time-injection abstraction.
    public boolean recordAndCheckTransition(String ingredientId, IngredientLifecycle current, LocalDate today) {
        // compute() is atomic on ConcurrentHashMap — the read of the previous
        // value and the write of the new value happen in one operation, so a
        // concurrent forget() can't race between the put and the check.
        boolean[] shouldFire = {false};
        lastSeenByIngredient.compute(ingredientId, (id, previous) -> {
            if (isAlertable(current)) {
                if (previous == null || !previous.day.equals(today) || previous.lifecycle != current) {
                    shouldFire[0] = true;
                }
            }
            return new Entry(current, today);
        });
        return shouldFire[0];
    }

    public void forget(String ingredientId) {
        lastSeenByIngredient.remove(ingredientId);
    }

    private static boolean isAlertable(IngredientLifecycle lifecycle) {
        return lifecycle == IngredientLifecycle.NEAR_EXPIRY
                || lifecycle == IngredientLifecycle.EXPIRED;
    }

    private static final class Entry {
        final IngredientLifecycle lifecycle;
        final LocalDate day;

        Entry(IngredientLifecycle lifecycle, LocalDate day) {
            this.lifecycle = Objects.requireNonNull(lifecycle);
            this.day = Objects.requireNonNull(day);
        }
    }
}
