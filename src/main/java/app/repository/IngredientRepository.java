package app.repository;

import app.model.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class IngredientRepository {
    private final Map<String, Map<String, Ingredient>> byTenant = new ConcurrentHashMap<>();

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
}
