package app;

import app.model.Ingredient;
import app.model.IngredientLifecycle;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryManagerRobustnessTest {
    @Test
    void useIngredientRejectsNonPositiveQuantity() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient ingredient = new Ingredient("tenant-robust", "Tomato", 5, "kg", LocalDate.now().plusDays(2), 1);
        manager.addIngredient(ingredient);

        assertThrows(IllegalArgumentException.class,
                () -> manager.useIngredient("tenant-robust", ingredient.getId(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> manager.useIngredient("tenant-robust", ingredient.getId(), -2));
    }

    @Test
    void useIngredientRejectsQuantityGreaterThanAvailable() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient ingredient = new Ingredient("tenant-robust", "Tomato", 5, "kg", LocalDate.now().plusDays(2), 1);
        manager.addIngredient(ingredient);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.useIngredient("tenant-robust", ingredient.getId(), 5.01));
        assertEquals("Used quantity cannot exceed available inventory", ex.getMessage());

        Ingredient persisted = manager.findById("tenant-robust", ingredient.getId()).orElseThrow();
        assertEquals(5.0, persisted.getQuantity(), 1e-9);
    }

    @Test
    void useIngredientToZeroRemovesIngredientFromInventory() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient ingredient = new Ingredient("tenant-robust", "Spinach", 1.0, "kg", LocalDate.now().plusDays(2), 0.2);
        manager.addIngredient(ingredient);

        manager.useIngredient("tenant-robust", ingredient.getId(), 1.0);

        assertTrue(manager.findById("tenant-robust", ingredient.getId()).isEmpty());
        assertTrue(manager.listIngredients("tenant-robust").isEmpty());
    }

    @Test
    void discardIngredientRemovesIngredientFromInventory() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient ingredient = new Ingredient("tenant-robust", "Cucumber", 2.0, "kg", LocalDate.now().plusDays(2), 0.5);
        manager.addIngredient(ingredient);

        manager.discardIngredient("tenant-robust", ingredient.getId());

        // Discarded items are kept in the repository for audit but excluded from
        // listIngredients (active inventory view). findById still returns the row.
        assertTrue(manager.findById("tenant-robust", ingredient.getId()).isPresent());
        assertTrue(manager.listIngredients("tenant-robust").isEmpty());
    }

    @Test
    void useIngredientRejectsExpiredIngredient() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient ingredient = new Ingredient("tenant-robust", "Lettuce", 2.0, "kg", LocalDate.now().minusDays(1), 0.5);
        manager.addIngredient(ingredient);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.useIngredient("tenant-robust", ingredient.getId(), 1.0));
        assertEquals("Expired ingredients can only be discarded", ex.getMessage());

        Ingredient persisted = manager.findById("tenant-robust", ingredient.getId()).orElseThrow();
        assertEquals(2.0, persisted.getQuantity(), 1e-9);
        assertEquals(IngredientLifecycle.EXPIRED, persisted.getLifecycle());
    }

    @Test
    void listIngredientsReturnsImmutableSnapshot() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        manager.addIngredient(new Ingredient("tenant-robust", "Onion", 3, "kg", LocalDate.now().plusDays(4), 1));

        List<Ingredient> ingredients = manager.listIngredients("tenant-robust");
        assertThrows(UnsupportedOperationException.class,
                () -> ingredients.add(new Ingredient("tenant-robust", "Potato", 1, "kg", LocalDate.now().plusDays(1), 1)));
    }

    @Test
    void addIngredientRejectsNaNQuantity() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient invalid = new Ingredient("tenant-robust", "Salt", Double.NaN, "kg", LocalDate.now().plusDays(5), 1);
        assertThrows(IllegalArgumentException.class, () -> manager.addIngredient(invalid));
    }

    @Test
    void mutatingReturnedIngredientDoesNotAffectStoredEntity() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient added = manager.addIngredient(
                new Ingredient("tenant-robust", "Carrot", 6, "kg", LocalDate.now().plusDays(5), 2)
        );

        Ingredient fromList = manager.listIngredients("tenant-robust").get(0);
        fromList.setQuantity(0);
        fromList.setDiscarded(true);

        Ingredient persisted = manager.findById("tenant-robust", added.getId()).orElseThrow();
        assertEquals(6, persisted.getQuantity());
        assertFalse(persisted.isDiscarded());
    }

    @Test
    void singletonRejectsConflictingInitialization() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repoA = new IngredientRepository();
        IngredientRepository repoB = new IngredientRepository();

        InventoryManager first = InventoryManager.getInstance(repoA, 3);
        InventoryManager second = InventoryManager.getInstance(repoA, 3);

        assertSame(first, second);
        assertThrows(IllegalStateException.class, () -> InventoryManager.getInstance(repoB, 3));
        // Bundle 3: nearExpiryDays is now a live-reloadable IntSupplier, so a
        // different window on re-init is no longer an error — only repository
        // identity mismatches are rejected.
        assertSame(first, InventoryManager.getInstance(repoA, 5));
    }

    @Test
    void useIngredientRoundsRemainingQuantityToTwoDecimals() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        Ingredient added = manager.addIngredient(
                new Ingredient("tenant-robust", "Milk", 1.0, "liters", LocalDate.now().plusDays(3), 0.5)
        );

        manager.useIngredient("tenant-robust", added.getId(), 0.335);

        Ingredient persisted = manager.findById("tenant-robust", added.getId()).orElseThrow();
        assertEquals(0.67, persisted.getQuantity(), 1e-9);
    }
}
