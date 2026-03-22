package app.repository;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class IngredientRepository {
    private final Map<String, Map<String, Ingredient>> byTenant = new ConcurrentHashMap<>();

    public Ingredient save(Ingredient ingredient) {
        byTenant.computeIfAbsent(ingredient.getTenantId(), key -> new ConcurrentHashMap<>())
                .put(ingredient.getId(), ingredient);
        return ingredient;
    }

    public Optional<Ingredient> findById(String tenantId, String ingredientId) {
        return Optional.ofNullable(byTenant.getOrDefault(tenantId, Map.of()).get(ingredientId));
    }

    public List<Ingredient> findByTenant(String tenantId) {
        return new ArrayList<>(byTenant.getOrDefault(tenantId, Map.of()).values());
    }
}
