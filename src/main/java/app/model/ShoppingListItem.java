package app.model;

import java.util.Objects;

/**
 * Immutable value object representing a single item on the shopping list.
 * All fields are final and determined at construction time.
 */
public final class ShoppingListItem {
    private final String ingredientId;
    private final String name;
    private final double currentQuantity;
    private final double threshold;
    private final double suggestedReorderQty;
    private final String unit;
    private final ShoppingItemStatus status;

    public ShoppingListItem(
            String ingredientId,
            String name,
            double currentQuantity,
            double threshold,
            double suggestedReorderQty,
            String unit,
            ShoppingItemStatus status) {
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.currentQuantity = currentQuantity;
        this.threshold = threshold;
        this.suggestedReorderQty = suggestedReorderQty;
        this.unit = Objects.requireNonNull(unit, "unit is required");
        this.status = Objects.requireNonNull(status, "status is required");
    }

    public String getIngredientId() {
        return ingredientId;
    }

    public String getName() {
        return name;
    }

    public double getCurrentQuantity() {
        return currentQuantity;
    }

    public double getThreshold() {
        return threshold;
    }

    public double getSuggestedReorderQty() {
        return suggestedReorderQty;
    }

    public String getUnit() {
        return unit;
    }

    public ShoppingItemStatus getStatus() {
        return status;
    }
}
