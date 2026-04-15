package app.service;

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



import java.util.List;

public class ShoppingListService {
    private final InventoryManager inventoryManager;

    public ShoppingListService(InventoryManager inventoryManager) {
        this.inventoryManager = inventoryManager;
    }

    public List<Ingredient> generateShoppingList(String tenantId) {
        return inventoryManager.listIngredients(tenantId)
                .stream()
                .filter(ingredient -> ingredient.getQuantity() <= ingredient.getLowStockThreshold())
                .toList();
    }
}
