package app.service;

import app.alerts.InventoryChangeObserver;
import app.model.Ingredient;
import app.model.ShoppingItemStatus;
import app.model.ShoppingListItem;
import app.repository.ShoppingListRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing shopping lists.
 * Generates shopping lists based on ingredients below low stock threshold.
 * Caches results and reacts to inventory changes via Observer pattern.
 *
 * Patterns:
 * - Observer: implements InventoryChangeObserver to react to inventory mutations
 * - Caching: caches generated lists per tenant, invalidates on inventory changes
 * - Repository: delegates persistence to ShoppingListRepository
 */
public class ShoppingListService implements InventoryChangeObserver {
    private final InventoryManager inventoryManager;
    private final ShoppingListRepository shoppingListRepository;
    private final ConcurrentHashMap<String, List<ShoppingListItem>> cache = new ConcurrentHashMap<>();

    public ShoppingListService(InventoryManager inventoryManager, ShoppingListRepository shoppingListRepository) {
        this.inventoryManager = Objects.requireNonNull(inventoryManager, "inventoryManager is required");
        this.shoppingListRepository = Objects.requireNonNull(shoppingListRepository, "shoppingListRepository is required");
    }

    /**
     * Observer callback: when inventory changes, invalidate this tenant's cached list.
     * Next call to generateShoppingList will recompute from fresh inventory.
     */
    @Override
    public void onInventoryChanged(String tenantId) {
        cache.remove(tenantId);
    }

    /**
     * Generate shopping list for a tenant.
     * Returns cached result if available, otherwise computes and caches.
     * Filters out items marked as PURCHASED or IGNORED.
     *
     * @param tenantId the tenant
     * @return list of items currently needing to be ordered
     */
    public List<ShoppingListItem> generateShoppingList(String tenantId) {
        return cache.computeIfAbsent(tenantId, this::computeShoppingList);
    }

    /**
     * Internal computation of shopping list.
     * 1. Get all ingredients for tenant
     * 2. Filter: not discarded, quantity <= threshold
     * 3. For each, look up status override from repository
     * 4. Create ShoppingListItem with calculated reorder qty
     * 5. Filter OUT items marked PURCHASED or IGNORED
     */
    private List<ShoppingListItem> computeShoppingList(String tenantId) {
        List<ShoppingListItem> items = new ArrayList<>();

        for (Ingredient ingredient : inventoryManager.listIngredients(tenantId)) {
            // Only consider non-discarded ingredients below threshold
            if (ingredient.isDiscarded() || ingredient.getQuantity() > ingredient.getLowStockThreshold()) {
                continue;
            }

            // Look up any persisted status override (PURCHASED, IGNORED)
            ShoppingItemStatus status = shoppingListRepository
                .findStatus(tenantId, ingredient.getId())
                .orElse(ShoppingItemStatus.PENDING);

            // Calculate suggested reorder quantity: (threshold × 2) - current quantity
            double suggestedReorderQty = (ingredient.getLowStockThreshold() * 2) - ingredient.getQuantity();

            ShoppingListItem item = new ShoppingListItem(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getQuantity(),
                ingredient.getLowStockThreshold(),
                suggestedReorderQty,
                ingredient.getUnit(),
                status
            );

            items.add(item);
        }

        // Filter out items that are PURCHASED or IGNORED
        // Only return items that are PENDING (need to be ordered)
        return items.stream()
            .filter(item -> item.getStatus() == ShoppingItemStatus.PENDING)
            .toList();
    }

    /**
     * Mark an ingredient as purchased (remove from shopping list).
     * @param tenantId the tenant
     * @param ingredientId the ingredient to mark as purchased
     */
    public void markPurchased(String tenantId, String ingredientId) {
        shoppingListRepository.saveStatus(tenantId, ingredientId, ShoppingItemStatus.PURCHASED);
        cache.remove(tenantId);
    }

    /**
     * Mark an ingredient as ignored (remove from shopping list temporarily).
     * @param tenantId the tenant
     * @param ingredientId the ingredient to ignore
     */
    public void markIgnored(String tenantId, String ingredientId) {
        shoppingListRepository.saveStatus(tenantId, ingredientId, ShoppingItemStatus.IGNORED);
        cache.remove(tenantId);
    }

    /**
     * Reset an ingredient to PENDING status (removes any PURCHASED or IGNORED override).
     * The item will reappear on the shopping list if still below threshold.
     * @param tenantId the tenant
     * @param ingredientId the ingredient to reset
     */
    public void resetItem(String tenantId, String ingredientId) {
        shoppingListRepository.deleteStatus(tenantId, ingredientId);
        cache.remove(tenantId);
    }
}
