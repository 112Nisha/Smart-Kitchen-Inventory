package app.service;

import app.alerts.*;
import app.model.*;
import app.notification.*;
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
