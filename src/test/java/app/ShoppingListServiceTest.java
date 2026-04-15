package app;

import app.model.ExpiryAlertContext;
import app.service.ExpiryAlertScheduler;
import app.service.IngredientStateTracker;
import app.service.StakeholderNotificationHandler;
import app.model.*;
import app.repository.InMemoryNotificationStore;
import app.repository.NotificationStore;
import app.repository.SqliteNotificationStore;
import app.service.DashboardNotificationStrategy;
import app.service.NotificationService;
import app.service.NotificationStrategy;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoppingListServiceTest {
    @Test
    void lowStockIngredientsAreIncludedInShoppingList() {
        InventoryManager.resetInstanceForTests();
        IngredientRepository repository = new IngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        ShoppingListService shoppingListService = new ShoppingListService(manager);

        manager.addIngredient(new Ingredient("tenant-shopping", "Onion", 2, "kg", LocalDate.now().plusDays(5), 3));
        manager.addIngredient(new Ingredient("tenant-shopping", "Potato", 10, "kg", LocalDate.now().plusDays(5), 3));

        List<Ingredient> shoppingList = shoppingListService.generateShoppingList("tenant-shopping");
        assertEquals(1, shoppingList.size());
        assertEquals("Onion", shoppingList.get(0).getName());
    }
}
