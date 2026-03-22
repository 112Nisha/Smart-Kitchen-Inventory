package app;

import app.model.Ingredient;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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
}
