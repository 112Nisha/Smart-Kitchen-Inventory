package app.service;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryManager {
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
        if (instance == null) {
            synchronized (InventoryManager.class) {
                if (instance == null) {
                    instance = new InventoryManager(ingredientRepository, nearExpiryDays);
                }
            }
        }
        return instance;
    }

    public Ingredient addIngredient(Ingredient ingredient) {
        validateIngredient(ingredient);
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
        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.setName(name);
            item.setQuantity(quantity);
            item.setUnit(unit);
            item.setExpiryDate(expiryDate);
            item.setLowStockThreshold(lowStockThreshold);
            item.refreshState(LocalDate.now(), nearExpiryDays);
            ingredientRepository.save(item);
            invalidateTenantCache(tenantId);
        });
        return existing;
    }

    public Optional<Ingredient> useIngredient(String tenantId, String ingredientId, double usedQuantity) {
        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            double updated = Math.max(0, item.getQuantity() - usedQuantity);
            item.setQuantity(updated);
            item.refreshState(LocalDate.now(), nearExpiryDays);
            ingredientRepository.save(item);
            invalidateTenantCache(tenantId);
        });
        return existing;
    }

    public Optional<Ingredient> discardIngredient(String tenantId, String ingredientId) {
        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.setDiscarded(true);
            item.refreshState(LocalDate.now(), nearExpiryDays);
            ingredientRepository.save(item);
            invalidateTenantCache(tenantId);
        });
        return existing;
    }

    public List<Ingredient> listIngredients(String tenantId) {
        return tenantCache.computeIfAbsent(tenantId, key -> {
            List<Ingredient> items = ingredientRepository.findByTenant(tenantId);
            items.forEach(item -> item.refreshState(LocalDate.now(), nearExpiryDays));
            return items;
        });
    }

    public Optional<Ingredient> findById(String tenantId, String ingredientId) {
        Optional<Ingredient> found = ingredientRepository.findById(tenantId, ingredientId);
        found.ifPresent(item -> item.refreshState(LocalDate.now(), nearExpiryDays));
        return found;
    }

    private void validateIngredient(Ingredient ingredient) {
        if (ingredient.getName().isBlank()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        if (ingredient.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity must be >= 0");
        }
        if (ingredient.getLowStockThreshold() < 0) {
            throw new IllegalArgumentException("Low stock threshold must be >= 0");
        }
    }

    private void invalidateTenantCache(String tenantId) {
        tenantCache.remove(tenantId);
    }
}
