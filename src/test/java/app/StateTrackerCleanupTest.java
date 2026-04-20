package app;

import app.model.Ingredient;
import app.model.IngredientEvent;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Fix 8: InventoryManager fires an "ingredient removed" event when an
 * item is discarded or consumed to zero quantity, and IngredientStateTracker
 * is wired up to forget the corresponding id.
 *
 * Without this, the tracker's map leaks entries forever — harmless per-item
 * but unbounded over time for an installation that cycles many ingredients.
 */
class StateTrackerCleanupTest {
    private InventoryManager manager;

    @BeforeEach
    void setUp() {
        InventoryManager.resetInstanceForTests();
        manager = InventoryManager.getInstance(new IngredientRepository(), 3);
    }

    @AfterEach
    void tearDown() {
        InventoryManager.resetInstanceForTests();
    }

    private List<String> captureRemovals() {
        List<String> removed = new ArrayList<>();
        manager.addListener(event -> {
            if (event instanceof IngredientEvent.Discarded e) {
                removed.add(e.ingredient().getId());
            } else if (event instanceof IngredientEvent.ConsumedToZero e) {
                removed.add(e.ingredient().getId());
            }
        });
        return removed;
    }

    @Test
    void discardIngredientFiresRemovalEvent() {
        List<String> removed = captureRemovals();
        Ingredient tomato = manager.addIngredient(
                new Ingredient("t", "Tomato", 5.0, "kg", LocalDate.now().plusDays(2), 1.0)
        );

        manager.discardIngredient("t", tomato.getId());

        assertEquals(List.of(tomato.getId()), removed);
    }

    @Test
    void consumeToZeroFiresRemovalEvent() {
        List<String> removed = captureRemovals();
        Ingredient tomato = manager.addIngredient(
                new Ingredient("t", "Tomato", 2.0, "kg", LocalDate.now().plusDays(2), 1.0)
        );

        manager.useIngredient("t", tomato.getId(), 2.0);

        assertEquals(List.of(tomato.getId()), removed,
                "consuming the full quantity should fire the removal event once");
    }

    @Test
    void partialConsumeDoesNotFireRemovalEvent() {
        // Item still has quantity left — should stay in the alertable pool,
        // so no removal event should fire. Otherwise we'd forget tracker state
        // while the item is still actively being alerted on.
        List<String> removed = captureRemovals();
        Ingredient tomato = manager.addIngredient(
                new Ingredient("t", "Tomato", 5.0, "kg", LocalDate.now().plusDays(2), 1.0)
        );

        manager.useIngredient("t", tomato.getId(), 2.0);

        assertTrue(removed.isEmpty(),
                "partial consume should not fire — still has quantity remaining");
    }

    @Test
    void overconsumeThrowsAndFiresNoEvent() {
        // useIngredient rejects a quantity that exceeds available inventory —
        // no removal event should fire since the ingredient was not affected.
        List<String> removed = captureRemovals();
        Ingredient tomato = manager.addIngredient(
                new Ingredient("t", "Tomato", 1.0, "kg", LocalDate.now().plusDays(2), 1.0)
        );

        try {
            manager.useIngredient("t", tomato.getId(), 999.0);
            assertFalse(true, "expected IllegalArgumentException for overconsume");
        } catch (IllegalArgumentException expected) {
            // correct
        }

        assertTrue(removed.isEmpty(), "no removal event should fire when use is rejected");
    }

    @Test
    void failingListenerDoesNotBreakOtherListenersOrTheCall() {
        // fireIngredientRemoved swallows runtime exceptions so a buggy listener
        // can't poison other subscribers or bubble out of a routine discard.
        List<String> captured = new ArrayList<>();
        manager.addListener(event -> { throw new RuntimeException("first listener fails"); });
        manager.addListener(event -> {
            if (event instanceof IngredientEvent.Discarded e) captured.add(e.ingredient().getId());
            else if (event instanceof IngredientEvent.ConsumedToZero e) captured.add(e.ingredient().getId());
        });

        Ingredient tomato = manager.addIngredient(
                new Ingredient("t", "Tomato", 1.0, "kg", LocalDate.now().plusDays(2), 1.0)
        );

        // This call must not throw despite the first listener's exception.
        manager.discardIngredient("t", tomato.getId());

        assertEquals(List.of(tomato.getId()), captured,
                "second listener should still receive the event");
    }

    @Test
    void addIngredientRemovedListenerRejectsNull() {
        try {
            manager.addListener(null);
            assertFalse(true, "expected NullPointerException for null listener");
        } catch (NullPointerException expected) {
            // good
        }
    }

    @Test
    void multipleListenersAreAllInvoked() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        manager.addListener(event -> {
            if (event instanceof IngredientEvent.Discarded e) a.add(e.ingredient().getId());
            else if (event instanceof IngredientEvent.ConsumedToZero e) a.add(e.ingredient().getId());
        });
        manager.addListener(event -> {
            if (event instanceof IngredientEvent.Discarded e) b.add(e.ingredient().getId());
            else if (event instanceof IngredientEvent.ConsumedToZero e) b.add(e.ingredient().getId());
        });

        Ingredient tomato = manager.addIngredient(
                new Ingredient("t", "Tomato", 1.0, "kg", LocalDate.now().plusDays(2), 1.0)
        );
        manager.discardIngredient("t", tomato.getId());

        assertEquals(List.of(tomato.getId()), a);
        assertEquals(List.of(tomato.getId()), b);
    }
}
