package app;

import app.model.Ingredient;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThrows(IllegalStateException.class, () -> InventoryManager.getInstance(repoA, 5));
    }
}
