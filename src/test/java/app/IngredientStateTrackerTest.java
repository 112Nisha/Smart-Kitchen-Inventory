package app;

import app.service.IngredientStateTracker;
import app.model.IngredientLifecycle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Fix 5: IngredientStateTracker only returns true on transitions *into*
 * an alertable lifecycle. These tests pin the exact behaviour so the
 * "refresh = spam" regression can't come back through a subtle change to the
 * transition rules (e.g. someone making first-sighting of FRESH return true,
 * or dropping the equality check).
 */
class IngredientStateTrackerTest {
    @Test
    void firstSightingOfAlertableIngredientReturnsTrue() {
        IngredientStateTracker tracker = new IngredientStateTracker();
        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
    }

    @Test
    void firstSightingOfNonAlertableIngredientReturnsFalse() {
        // FRESH is not alertable — first sighting must not fire the chain.
        IngredientStateTracker tracker = new IngredientStateTracker();
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.FRESH));
    }

    @Test
    void repeatedSameAlertableStateReturnsFalse() {
        // This is the core of the "refresh = spam" fix: seeing the same
        // NEAR_EXPIRY state twice should fire the chain exactly once.
        IngredientStateTracker tracker = new IngredientStateTracker();
        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
    }

    @Test
    void escalationFromNearExpiryToExpiredReturnsTrue() {
        // NEAR_EXPIRY -> EXPIRED is a genuine escalation and must fire the
        // chain again even though we already alerted on NEAR_EXPIRY.
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY);
        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.EXPIRED));
    }

    @Test
    void freshToNearExpiryReturnsTrue() {
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.FRESH);
        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
    }

    @Test
    void transitionIntoNonAlertableReturnsFalse() {
        // NEAR_EXPIRY -> DISCARDED: the lifecycle changed but the new state is
        // not alertable, so the chain should NOT fire.
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY);
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.DISCARDED));
    }

    @Test
    void expiredToDiscardedReturnsFalse() {
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.EXPIRED);
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.DISCARDED));
    }

    @Test
    void forgetResetsTrackingForIngredient() {
        // After forget(), the next observation is a "first sighting" again.
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY);
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));

        tracker.forget("ing-1");

        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY),
                "after forget(), the next observation should count as a first sighting");
    }

    @Test
    void forgetUnknownIngredientIsNoOp() {
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.forget("never-seen"); // should not throw
        assertTrue(tracker.recordAndCheckTransition("never-seen", IngredientLifecycle.NEAR_EXPIRY));
    }

    @Test
    void differentIngredientsTrackedIndependently() {
        IngredientStateTracker tracker = new IngredientStateTracker();
        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
        // Seeing ing-2 for the first time must still fire — ing-1's state
        // should not suppress ing-2's first-sighting alert.
        assertTrue(tracker.recordAndCheckTransition("ing-2", IngredientLifecycle.NEAR_EXPIRY));
        // And ing-1 should still be suppressed on repeat.
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY));
    }

    @Test
    void freshIsNeverAlertableEvenAfterTransition() {
        // EXPIRED -> FRESH shouldn't happen in production, but if it did, the
        // new state is not alertable so the chain must stay quiet.
        IngredientStateTracker tracker = new IngredientStateTracker();
        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.EXPIRED);
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.FRESH));
    }

    @Test
    void dayRolloverReNagsSameLifecycle() {
        // An item that stays NEAR_EXPIRY across a midnight boundary should
        // fire again on the new day — "daily re-nag" so a multi-day
        // near-expiry item doesn't silently drop out of the notifications log.
        IngredientStateTracker tracker = new IngredientStateTracker();
        LocalDate day1 = LocalDate.of(2026, 4, 14);
        LocalDate day2 = day1.plusDays(1);

        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY, day1));
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY, day1),
                "same day, same state — must not re-nag");
        assertTrue(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY, day2),
                "new day, still alertable — must re-nag");
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.NEAR_EXPIRY, day2),
                "same new day — suppressed again after the daily re-nag fired");
    }

    @Test
    void dayRolloverDoesNotReNagNonAlertableState() {
        // A FRESH item on day 2 after a FRESH on day 1 must not fire: daily
        // re-nag is for alertable states, not for revisiting steady non-alerts.
        IngredientStateTracker tracker = new IngredientStateTracker();
        LocalDate day1 = LocalDate.of(2026, 4, 14);
        LocalDate day2 = day1.plusDays(1);

        tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.FRESH, day1);
        assertFalse(tracker.recordAndCheckTransition("ing-1", IngredientLifecycle.FRESH, day2));
    }
}
