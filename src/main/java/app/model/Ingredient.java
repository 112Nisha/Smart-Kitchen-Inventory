package app.model;

import app.state.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Ingredient {
    private final String id;
    private final String tenantId;
    private String name;
    private double quantity;
    private String unit;
    private LocalDate expiryDate;
    private double lowStockThreshold;
    private boolean discarded;
    private IngredientState state;

    public Ingredient(String tenantId,
                      String name,
                      double quantity,
                      String unit,
                      LocalDate expiryDate,
                      double lowStockThreshold) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.name = Objects.requireNonNull(name, "name is required").trim();
        this.quantity = quantity;
        this.unit = Objects.requireNonNull(unit, "unit is required").trim();
        this.expiryDate = Objects.requireNonNull(expiryDate, "expiryDate is required");
        this.lowStockThreshold = lowStockThreshold;
        this.discarded = false;
        this.state = new FreshState();
    }

    private Ingredient(String id,
                       String tenantId,
                       String name,
                       double quantity,
                       String unit,
                       LocalDate expiryDate,
                       double lowStockThreshold,
                       boolean discarded,
                       IngredientState state) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.expiryDate = expiryDate;
        this.lowStockThreshold = lowStockThreshold;
        this.discarded = discarded;
        this.state = state;
    }

    public Ingredient copy() {
        IngredientState copiedState = discarded ? new DiscardedState() : switch (state.getLifecycle()) {
            case FRESH -> new FreshState();
            case NEAR_EXPIRY -> new NearExpiryState();
            case EXPIRED -> new ExpiredState();
            case DISCARDED -> new DiscardedState();
        };

        return new Ingredient(
                id,
                tenantId,
                name,
                quantity,
                unit,
                expiryDate,
                lowStockThreshold,
                discarded,
                copiedState
        );
    }

    public void refreshState(LocalDate today, int nearExpiryDays) {
        if (discarded) {
            state = new DiscardedState();
            return;
        }
        if (expiryDate.isBefore(today)) {
            state = new ExpiredState();
            return;
        }
        if (!expiryDate.isAfter(today.plusDays(nearExpiryDays))) {
            state = new NearExpiryState();
            return;
        }
        state = new FreshState();
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name is required").trim();
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = Objects.requireNonNull(unit, "unit is required").trim();
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = Objects.requireNonNull(expiryDate, "expiryDate is required");
    }

    public double getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(double lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isDiscarded() {
        return discarded;
    }

    public void setDiscarded(boolean discarded) {
        this.discarded = discarded;
    }

    public IngredientState getState() {
        return state;
    }

    public IngredientLifecycle getLifecycle() {
        return state.getLifecycle();
    }
}
