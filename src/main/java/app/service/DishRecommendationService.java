package app.service;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DishRecommendationService {
    private final InventoryManager inventoryManager;
    private final DishRepository dishRepository;

    public DishRecommendationService(InventoryManager inventoryManager, DishRepository dishRepository) {
        this.inventoryManager = inventoryManager;
        this.dishRepository = dishRepository;
    }

    public List<DishRecipe> suggestForExpiringIngredients(String tenantId) {
        Set<String> nearExpiryIngredients = inventoryManager.listIngredients(tenantId).stream()
                .filter(ingredient -> ingredient.getState().canRecommendInDish())
                .map(ingredient -> ingredient.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return dishRepository.findAll()
                .stream()
                .filter(dish -> dish.getIngredientNames()
                        .stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .anyMatch(nearExpiryIngredients::contains))
                .toList();
    }

    public double estimatedSavedIngredientQuantity(String tenantId, String dishName) {
        List<Ingredient> nearExpiry = inventoryManager.listIngredients(tenantId).stream()
                .filter(ingredient -> ingredient.getState().canRecommendInDish())
                .toList();

        return dishRepository.findAll().stream()
                .filter(dish -> dish.getName().equalsIgnoreCase(dishName))
                .findFirst()
                .map(dish -> nearExpiry.stream()
                        .filter(ingredient -> dish.getIngredientNames()
                                .stream()
                                .anyMatch(name -> name.equalsIgnoreCase(ingredient.getName())))
                        .mapToDouble(ingredient -> Math.min(ingredient.getQuantity(), Math.max(0.2, ingredient.getQuantity() * 0.25)))
                        .sum())
                .orElse(0.0);
    }
}
