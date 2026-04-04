package app;

import app.model.DishRecipe;
import app.model.Ingredient;
import app.repository.DishRepository;
import app.repository.IngredientRepository;
import app.service.DishRecommendationService;
import app.service.InventoryManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishRecommendationFlowTest {

    @Test
    void logAsCookedSubtractsInventoryAndRefreshesSuggestions() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository ingredientRepository = new IngredientRepository();
        InventoryManager inventoryManager = InventoryManager.getInstance(ingredientRepository, 3);
        DishRecommendationService recommendationService =
                new DishRecommendationService(inventoryManager, new DishRepository());

        String tenant = "tenant-dish-flow";

        inventoryManager.addIngredient(new Ingredient(tenant, "Tomato", 0.3, "kg", LocalDate.now().plusDays(2), 0.1));
        inventoryManager.addIngredient(new Ingredient(tenant, "Basil", 0.05, "kg", LocalDate.now().plusDays(2), 0.01));
        inventoryManager.addIngredient(new Ingredient(tenant, "Garlic", 0.02, "kg", LocalDate.now().plusDays(2), 0.01));
        inventoryManager.addIngredient(new Ingredient(tenant, "Pasta", 0.2, "kg", LocalDate.now().plusDays(2), 0.1));

        List<DishRecipe> beforeCook = recommendationService.suggestDishes(tenant);
        assertTrue(beforeCook.stream().anyMatch(dish -> dish.getName().equalsIgnoreCase("Tomato Basil Pasta")));

        recommendationService.logDishAsCooked(tenant, "Tomato Basil Pasta");

        List<Ingredient> remaining = inventoryManager.listIngredients(tenant);
        assertEquals(0.0, quantityOf(remaining, "Tomato", "kg"), 1e-9);
        assertEquals(0.0, quantityOf(remaining, "Basil", "kg"), 1e-9);
        assertEquals(0.0, quantityOf(remaining, "Garlic", "kg"), 1e-9);
        assertEquals(0.0, quantityOf(remaining, "Pasta", "kg"), 1e-9);

        List<DishRecipe> afterCook = recommendationService.suggestDishes(tenant);
        assertFalse(afterCook.stream().anyMatch(dish -> dish.getName().equalsIgnoreCase("Tomato Basil Pasta")));
    }

    private double quantityOf(List<Ingredient> inventory, String ingredientName, String unit) {
        return inventory.stream()
                .filter(ingredient -> ingredient.getName().equalsIgnoreCase(ingredientName))
                .filter(ingredient -> ingredient.getUnit().equalsIgnoreCase(unit))
                .mapToDouble(Ingredient::getQuantity)
                .sum();
    }
}
