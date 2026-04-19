package app.service;

import app.model.Ingredient;
import app.repository.IngredientRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryManager {
    private static final double COMPARISON_EPSILON = 1e-9;

    private static volatile InventoryManager instance;

    private final IngredientRepository ingredientRepository;
    private final int nearExpiryDays;
    private final Map<String, List<Ingredient>> tenantCache = new ConcurrentHashMap<>();

    private InventoryManager(IngredientRepository ingredientRepository, int nearExpiryDays) {
        this.ingredientRepository = ingredientRepository;
        this.nearExpiryDays = nearExpiryDays;
    }

    public static synchronized void resetInstanceForTests() {
        instance = null;
    }

    public static InventoryManager getInstance(IngredientRepository ingredientRepository, int nearExpiryDays) {
        Objects.requireNonNull(ingredientRepository, "ingredientRepository is required");
        if (nearExpiryDays < 0) {
            throw new IllegalArgumentException("nearExpiryDays must be >= 0");
        }

        if (instance == null) {
            synchronized (InventoryManager.class) {
                if (instance == null) {
                    instance = new InventoryManager(ingredientRepository, nearExpiryDays);
                }
            }
        } else if (instance.ingredientRepository != ingredientRepository || instance.nearExpiryDays != nearExpiryDays) {
            throw new IllegalStateException("InventoryManager is already initialized with different configuration");
        }
        return instance;
    }

    public Ingredient addIngredient(Ingredient ingredient) {
        validateTenantId(Objects.requireNonNull(ingredient, "ingredient is required").getTenantId());
        validateIngredient(ingredient);
        ingredient.setQuantity(roundToTwoDecimals(ingredient.getQuantity()));
        ingredient.setLowStockThreshold(roundToTwoDecimals(ingredient.getLowStockThreshold()));
        Ingredient saved = ingredientRepository.save(ingredient);
        invalidateTenantCache(ingredient.getTenantId());
        return saved;
    }

    public Optional<Ingredient> updateIngredient(String tenantId,
                                                 String ingredientId,
                                                 String name,
                                                 double quantity,
                                                 String unit,
                                                 LocalDate expiryDate,
                                                 double lowStockThreshold) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);
        validateIngredientFields(name, quantity, lowStockThreshold);
        validateUnit(unit);
        Objects.requireNonNull(expiryDate, "expiryDate is required");

        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.setName(name);
            item.setQuantity(roundToTwoDecimals(quantity));
            item.setUnit(unit);
            item.setExpiryDate(expiryDate);
            item.setLowStockThreshold(roundToTwoDecimals(lowStockThreshold));
            item.refreshState(LocalDate.now(), nearExpiryDays);
            ingredientRepository.save(item);
            invalidateTenantCache(tenantId);
        });
        return existing;
    }

    public Optional<Ingredient> useIngredient(String tenantId, String ingredientId, double usedQuantity) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);
        if (!Double.isFinite(usedQuantity) || usedQuantity <= 0) {
            throw new IllegalArgumentException("Used quantity must be > 0");
        }

        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.refreshState(LocalDate.now(), nearExpiryDays);
            if (item.getLifecycle() == app.model.IngredientLifecycle.EXPIRED) {
                throw new IllegalArgumentException("Expired ingredients can only be discarded");
            }

            double availableQuantity = item.getQuantity();
            if (usedQuantity - availableQuantity > COMPARISON_EPSILON) {
                throw new IllegalArgumentException("Used quantity cannot exceed available inventory");
            }
            double updated = roundToTwoDecimals(Math.max(0, availableQuantity - usedQuantity));

            if (updated <= COMPARISON_EPSILON) {
                ingredientRepository.deleteById(tenantId, ingredientId);
            } else {
                item.setQuantity(updated);
                item.refreshState(LocalDate.now(), nearExpiryDays);
                ingredientRepository.save(item);
            }
            invalidateTenantCache(tenantId);
        });
        return existing;
    }

    public Optional<Ingredient> discardIngredient(String tenantId, String ingredientId) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);

        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            ingredientRepository.deleteById(tenantId, ingredientId);
            invalidateTenantCache(tenantId);
        });
        return existing;
    }

    public List<Ingredient> listIngredients(String tenantId) {
        validateTenantId(tenantId);
        List<Ingredient> items = tenantCache.computeIfAbsent(tenantId, key -> ingredientRepository.findByTenant(tenantId));
        items.forEach(item -> {
            normalizePrecision(item);
            item.refreshState(LocalDate.now(), nearExpiryDays);
        });
        return List.copyOf(items);
    }

    public Optional<Ingredient> findById(String tenantId, String ingredientId) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);
        Optional<Ingredient> found = ingredientRepository.findById(tenantId, ingredientId);
        found.ifPresent(item -> {
            normalizePrecision(item);
            item.refreshState(LocalDate.now(), nearExpiryDays);
        });
        return found;
    }

    private void normalizePrecision(Ingredient ingredient) {
        ingredient.setQuantity(roundToTwoDecimals(ingredient.getQuantity()));
        ingredient.setLowStockThreshold(roundToTwoDecimals(ingredient.getLowStockThreshold()));
    }

    private double roundToTwoDecimals(double value) {
        double rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return rounded == -0.0d ? 0.0d : rounded;
    }

    private void validateIngredient(Ingredient ingredient) {
        validateIngredientFields(
                ingredient.getName(),
                ingredient.getQuantity(),
                ingredient.getLowStockThreshold()
        );
        validateUnit(ingredient.getUnit());
        Objects.requireNonNull(ingredient.getExpiryDate(), "expiryDate is required");
    }

    private void validateIngredientFields(String name, double quantity, double lowStockThreshold) {
        if (Objects.requireNonNull(name, "name is required").isBlank()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        if (!Double.isFinite(quantity) || quantity < 0) {
            throw new IllegalArgumentException("Quantity must be >= 0");
        }
        if (!Double.isFinite(lowStockThreshold) || lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low stock threshold must be >= 0");
        }
    }

    private void validateTenantId(String tenantId) {
        if (Objects.requireNonNull(tenantId, "tenantId is required").isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    private void validateIngredientId(String ingredientId) {
        if (Objects.requireNonNull(ingredientId, "ingredientId is required").isBlank()) {
            throw new IllegalArgumentException("ingredientId must not be blank");
        }
    }

    private void validateUnit(String unit) {
        if (Objects.requireNonNull(unit, "unit is required").isBlank()) {
            throw new IllegalArgumentException("Unit must not be blank");
        }
    }

    private void invalidateTenantCache(String tenantId) {
        tenantCache.remove(tenantId);
    }
}
