package app;

import app.model.*;
import app.repository.*;
import app.service.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoppingListServiceTest {
    @Test
    void lowStockIngredientsAreIncludedInShoppingList() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);

        // Create a mock shopping list repository for testing
        ShoppingListRepository mockRepo = new ShoppingListRepository() {
            @Override
            public void saveStatus(String tenantId, String ingredientId, ShoppingItemStatus status) {}

            @Override
            public Optional<ShoppingItemStatus> findStatus(String tenantId, String ingredientId) {
                return Optional.empty();
            }

            @Override
            public void deleteStatus(String tenantId, String ingredientId) {}
        };

        ShoppingListService shoppingListService = new ShoppingListService(manager, mockRepo);
        manager.addInventoryObserver(shoppingListService);

        manager.addIngredient(new Ingredient("tenant-shopping", "Onion", 2, "kg", LocalDate.now().plusDays(5), 3));
        manager.addIngredient(new Ingredient("tenant-shopping", "Potato", 10, "kg", LocalDate.now().plusDays(5), 3));

        List<ShoppingListItem> shoppingList = shoppingListService.generateShoppingList("tenant-shopping");
        assertEquals(1, shoppingList.size());
        assertEquals("Onion", shoppingList.get(0).getName());
    }
}
