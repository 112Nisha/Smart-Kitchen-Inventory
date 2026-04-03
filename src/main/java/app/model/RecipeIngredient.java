package app.model;

import java.util.Objects;

public class RecipeIngredient {
    private final String name;
    private final double quantity;
    private final String unit;

    public RecipeIngredient(String name, double quantity, String unit) {
        this.name = Objects.requireNonNull(name, "name is required").trim();
        this.quantity = quantity;
        this.unit = Objects.requireNonNull(unit, "unit is required").trim();
    }

    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }
}