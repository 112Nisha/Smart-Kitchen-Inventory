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
     * Returns only PENDING items (need to be ordered).
     * Results are derived from the shared all-items cache.
     *
     * @param tenantId the tenant
     * @return list of items currently needing to be ordered
     */
    public List<ShoppingListItem> generateShoppingList(String tenantId) {
        return getAllItems(tenantId).stream()
                .filter(item -> item.getStatus() == ShoppingItemStatus.PENDING)
                .toList();
    }

    /**
     * Return items that the user has explicitly ignored.
     * These are below threshold but marked IGNORED — shown separately in the UI
     * so the user can restore them to PENDING without losing the context of why
     * they were dismissed.
     *
     * @param tenantId the tenant
     * @return list of items currently marked as IGNORED
     */
    public List<ShoppingListItem> getIgnoredItems(String tenantId) {
        return getAllItems(tenantId).stream()
                .filter(item -> item.getStatus() == ShoppingItemStatus.IGNORED)
                .toList();
    }

    /**
     * Shared cache accessor. Returns all low-stock items regardless of status.
     * generateShoppingList and getIgnoredItems both filter from this list so that
     * the underlying computation (and its DB calls) only happens once per cache
     * entry.
     */
    private List<ShoppingListItem> getAllItems(String tenantId) {
        return cache.computeIfAbsent(tenantId, this::computeAllItems);
    }

    /**
     * Internal computation of all low-stock items for a tenant.
     *
     * For each ingredient:
     *   - If quantity is above threshold: delete any stale PURCHASED or IGNORED
     *     record so the item re-appears the next time it drops below threshold.
     *   - If quantity is at or below threshold: include with its current status.
     *
     * listIngredients() already excludes discarded items, so no discard check
     * is needed here.
     */
    private List<ShoppingListItem> computeAllItems(String tenantId) {
        List<ShoppingListItem> items = new ArrayList<>();

        for (Ingredient ingredient : inventoryManager.listIngredients(tenantId)) {
            if (ingredient.getQuantity() > ingredient.getLowStockThreshold()) {
                // Ingredient is back above threshold — clean up any stale override
                // so it reappears on the list the next time it drops below threshold.
                shoppingListRepository.deleteStatus(tenantId, ingredient.getId());
                continue;
            }

            // Look up any persisted status override (PURCHASED, IGNORED)
            ShoppingItemStatus status = shoppingListRepository
                    .findStatus(tenantId, ingredient.getId())
                    .orElse(ShoppingItemStatus.PENDING);

            // Suggested reorder quantity: bring stock up to twice the threshold
            double suggestedReorderQty = (ingredient.getLowStockThreshold() * 2) - ingredient.getQuantity();

            items.add(new ShoppingListItem(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getQuantity(),
                    ingredient.getLowStockThreshold(),
                    suggestedReorderQty,
                    ingredient.getUnit(),
                    status
            ));
        }

        return items;
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
     * Mark multiple ingredients as purchased in one operation.
     * Each status write is individual; a single cache eviction follows all writes
     * so callers only pay one recompute cost regardless of selection size.
     *
     * A repository-level batch (prepared-statement addBatch) is intentionally
     * not added here: shopping list sizes are bounded by the number of low-stock
     * ingredients (typically small), so the overhead of N individual INSERTs is
     * negligible and the added interface complexity is not justified yet.
     *
     * @param tenantId      the tenant
     * @param ingredientIds the ingredients to mark as purchased
     */
    public void markPurchasedBulk(String tenantId, List<String> ingredientIds) {
        for (String ingredientId : ingredientIds) {
            shoppingListRepository.saveStatus(tenantId, ingredientId, ShoppingItemStatus.PURCHASED);
        }
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
