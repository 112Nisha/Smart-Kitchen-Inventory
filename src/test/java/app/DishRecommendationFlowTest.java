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

        @Test
        void suggestionsPrioritizeDishesWithMoreExpiringIngredients() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository ingredientRepository = new IngredientRepository();
        InventoryManager inventoryManager = InventoryManager.getInstance(ingredientRepository, 3);
        DishRecommendationService recommendationService =
            new DishRecommendationService(inventoryManager, new DishRepository());

        String tenant = "tenant-dish-ranking";

        inventoryManager.addIngredient(new Ingredient(tenant, "Tomato", 1.0, "kg", LocalDate.now().plusDays(1), 0.2));
        inventoryManager.addIngredient(new Ingredient(tenant, "Basil", 1.0, "kg", LocalDate.now().plusDays(1), 0.2));
        inventoryManager.addIngredient(new Ingredient(tenant, "Garlic", 1.0, "kg", LocalDate.now().plusDays(1), 0.2));
        inventoryManager.addIngredient(new Ingredient(tenant, "Pasta", 1.0, "kg", LocalDate.now().plusDays(1), 0.2));

        inventoryManager.addIngredient(new Ingredient(tenant, "Onion", 1.0, "kg", LocalDate.now().plusDays(6), 0.2));
        inventoryManager.addIngredient(new Ingredient(tenant, "Olive Oil", 1.0, "liters", LocalDate.now().plusDays(6), 0.2));

        List<DishRecommendationService.DishSuggestion> ranked =
            recommendationService.suggestDishesByExpiryPriority(tenant);

        int pastaIndex = indexOfDish(ranked, "Tomato Basil Pasta");
        int sauteIndex = indexOfDish(ranked, "Onion Garlic Saute");

        assertTrue(pastaIndex >= 0);
        assertTrue(sauteIndex >= 0);
        assertTrue(pastaIndex < sauteIndex);

        DishRecommendationService.DishSuggestion pastaSuggestion = ranked.get(pastaIndex);
        DishRecommendationService.DishSuggestion sauteSuggestion = ranked.get(sauteIndex);

        assertTrue(pastaSuggestion.getExpiryRescueScore() > sauteSuggestion.getExpiryRescueScore());
        assertTrue(pastaSuggestion.getIngredients().stream()
            .anyMatch(ingredient -> ingredient.isExpiringSoon()
                && ingredient.getExpiryHint().toLowerCase().contains("try to use")));
        }

    private double quantityOf(List<Ingredient> inventory, String ingredientName, String unit) {
        return inventory.stream()
                .filter(ingredient -> ingredient.getName().equalsIgnoreCase(ingredientName))
                .filter(ingredient -> ingredient.getUnit().equalsIgnoreCase(unit))
                .mapToDouble(Ingredient::getQuantity)
                .sum();
    }

    private int indexOfDish(List<DishRecommendationService.DishSuggestion> suggestions, String dishName) {
        for (int i = 0; i < suggestions.size(); i++) {
            if (suggestions.get(i).getDish().getName().equalsIgnoreCase(dishName)) {
                return i;
            }
        }
        return -1;
    }
}
