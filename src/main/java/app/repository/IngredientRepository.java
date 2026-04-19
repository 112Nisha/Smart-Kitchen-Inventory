package app.repository;

import app.model.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IngredientRepository {
    private final Map<String, Map<String, Ingredient>> byTenant = new ConcurrentHashMap<>();

    // Exposed so the scheduled expiry-alert runner can enumerate which tenants
    // to evaluate on each tick. Defensive copy prevents callers from mutating
    // the repository's internal keyset.
    public Set<String> findAllTenantIds() {
        return Set.copyOf(byTenant.keySet());
    }

    public Ingredient save(Ingredient ingredient) {
        Ingredient copy = ingredient.copy();
        byTenant.computeIfAbsent(ingredient.getTenantId(), key -> new ConcurrentHashMap<>())
                .put(ingredient.getId(), copy);
        return copy.copy();
    }

    public Optional<Ingredient> findById(String tenantId, String ingredientId) {
        Ingredient found = byTenant.getOrDefault(tenantId, Map.of()).get(ingredientId);
        return Optional.ofNullable(found).map(Ingredient::copy);
    }

    public List<Ingredient> findByTenant(String tenantId) {
        return byTenant.getOrDefault(tenantId, Map.of()).values().stream()
                .map(Ingredient::copy)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public boolean deleteById(String tenantId, String ingredientId) {
        Map<String, Ingredient> tenantInventory = byTenant.get(tenantId);
        if (tenantInventory == null) {
            return false;
        }

        boolean removed = tenantInventory.remove(ingredientId) != null;
        if (tenantInventory.isEmpty()) {
            byTenant.remove(tenantId, tenantInventory);
        }
        return removed;
    }
}
