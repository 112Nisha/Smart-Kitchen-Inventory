package app.repository;

import app.model.ShoppingItemStatus;
import java.util.Optional;

/**
 * Repository interface for shopping list item metadata (purchase/ignore status).
 * Abstracts storage of shopping list overrides away from business logic.
 */
public interface ShoppingListRepository {
    /**
     * Save the status of an ingredient on the shopping list.
     * @param tenantId the tenant
     * @param ingredientId the ingredient
     * @param status the status (PENDING, PURCHASED, or IGNORED)
     */
    void saveStatus(String tenantId, String ingredientId, ShoppingItemStatus status);

    /**
     * Find the status of an ingredient on the shopping list.
     * @param tenantId the tenant
     * @param ingredientId the ingredient
     * @return the status if one was saved, or empty if the ingredient has no override (defaults to PENDING)
     */
    Optional<ShoppingItemStatus> findStatus(String tenantId, String ingredientId);

    /**
     * Delete the status override for an ingredient (resets to PENDING).
     * @param tenantId the tenant
     * @param ingredientId the ingredient
     */
    void deleteStatus(String tenantId, String ingredientId);
}
