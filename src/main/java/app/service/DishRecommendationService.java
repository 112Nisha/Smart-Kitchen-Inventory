package app.service;

import app.model.DishRecipe;
import app.model.Ingredient;
import app.model.IngredientLifecycle;
import app.model.RecipeIngredient;
import app.repository.DishRepository;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DishRecommendationService {
        private final InventoryManager inventoryManager;
        private final DishRepository dishRepository;

        /* returns a list of recommended dishes based on available ingredients */
        public DishRecommendationService(InventoryManager inventoryManager, DishRepository dishRepository) {
                this.inventoryManager = inventoryManager;
                this.dishRepository = dishRepository;
        }

        public List<DishRecipe> suggestDishes(String tenantId) {

                List<Ingredient> inventory = inventoryManager.listIngredients(tenantId);
                Map<String, List<Ingredient>> inventoryMap = inventory.stream()
                                .filter(ingredient -> ingredient.getState().canRecommendInDish())
                                .collect(Collectors.groupingBy(
                                                ingredient -> ingredient.getName().toLowerCase(Locale.ROOT)));

                return dishRepository.findAll().stream()
                                .filter(dish -> dish.getIngredients().stream()
                                                .allMatch(recipeIngredient -> {
                                                        String name = recipeIngredient.getName()
                                                                        .toLowerCase(Locale.ROOT);

                                                        if (!inventoryMap.containsKey(name)) {
                                                                return false;
                                                        }

                                                        double totalAvailable = inventoryMap.get(name).stream()
                                                                        .filter(inv -> inv.getUnit().equalsIgnoreCase(
                                                                                        recipeIngredient.getUnit()))
                                                                        .mapToDouble(Ingredient::getQuantity)
                                                                        .sum();

                                                        return totalAvailable >= recipeIngredient.getQuantity();
                                                }))
                                .toList();
        }

        /**
         * estimates the quantity of ingredients that would be saved by cooking a
         * specific dish
         */
        public double estimatedSavedIngredientQuantity(String tenantId, String dishName) {
                List<Ingredient> nearExpiry = inventoryManager.listIngredients(tenantId).stream()
                                .filter(ingredient -> ingredient.getState().canRecommendInDish())
                                .toList();

                return dishRepository.findAll().stream()
                                .filter(dish -> dish.getName().equalsIgnoreCase(dishName))
                                .findFirst()
                                .map(dish -> dish.getIngredients().stream()
                                                .filter(recipeIngredient -> nearExpiry.stream()
                                                                .anyMatch(inv -> inv.getName().equalsIgnoreCase(
                                                                                recipeIngredient.getName())))
                                                .mapToDouble(RecipeIngredient::getQuantity)
                                                .sum())
                                .orElse(0.0);
        }

                        /**
                         * Logs a dish as cooked, consumes the required ingredient quantities,
                         * and returns unit totals plus rescued near-expiry ingredient details.
                         */
                        public CookDishResult logDishAsCooked(String tenantId, String dishName) {
                                DishRecipe dish = dishRepository.findAll().stream()
                                                .filter(candidate -> candidate.getName().equalsIgnoreCase(dishName))
                                                .findFirst()
                                                .orElseThrow(() -> new IllegalArgumentException("Dish not found: " + dishName));

                                List<Ingredient> inventorySnapshot = inventoryManager.listIngredients(tenantId);

                                double totalUsedWeightKg = 0.0;
                                double nearExpiryUsedKg = 0.0;
                                double nearExpiryUsedLiters = 0.0;
                                Map<String, Double> usedByUnit = new LinkedHashMap<>();
                                Set<String> rescuedNearExpiryIngredients = new LinkedHashSet<>();

                                for (RecipeIngredient recipeIngredient : dish.getIngredients()) {
                                        String targetName = recipeIngredient.getName().toLowerCase(Locale.ROOT);
                                        String targetUnit = recipeIngredient.getUnit().toLowerCase(Locale.ROOT);
                                        double requiredQuantity = recipeIngredient.getQuantity();

                                        List<Ingredient> candidates = inventorySnapshot.stream()
                                                        .filter(ingredient -> ingredient.getState().canRecommendInDish())
                                                        .filter(ingredient -> ingredient.getName().toLowerCase(Locale.ROOT).equals(targetName))
                                                        .filter(ingredient -> ingredient.getUnit().toLowerCase(Locale.ROOT).equals(targetUnit))
                                                        .sorted((left, right) -> left.getExpiryDate().compareTo(right.getExpiryDate()))
                                                        .toList();

                                        double availableQuantity = candidates.stream()
                                                        .mapToDouble(Ingredient::getQuantity)
                                                        .sum();

                                        if (availableQuantity + 1e-9 < requiredQuantity) {
                                                throw new IllegalStateException("Insufficient inventory for ingredient: "
                                                                + recipeIngredient.getName() + " (required " + requiredQuantity + " "
                                                                + recipeIngredient.getUnit() + ")");
                                        }

                                        double remainingToConsume = requiredQuantity;
                                        for (Ingredient candidate : candidates) {
                                                if (remainingToConsume <= 1e-9) {
                                                        break;
                                                }

                                                double consumed = Math.min(candidate.getQuantity(), remainingToConsume);
                                                if (consumed <= 0) {
                                                        continue;
                                                }

                                                Optional<Ingredient> updated = inventoryManager.useIngredient(
                                                                tenantId,
                                                                candidate.getId(),
                                                                consumed
                                                );
                                                if (updated.isEmpty()) {
                                                        throw new IllegalStateException("Ingredient disappeared during cook log: "
                                                                        + candidate.getName());
                                                }

                                                candidate.setQuantity(candidate.getQuantity() - consumed);
                                                remainingToConsume -= consumed;

                                                String unitLabel = normalizeUnitLabel(recipeIngredient.getUnit());
                                                usedByUnit.merge(unitLabel, consumed, Double::sum);

                                                if (isWeightUnit(recipeIngredient.getUnit())) {
                                                        totalUsedWeightKg += consumed;
                                                }

                                                if (candidate.getLifecycle() == IngredientLifecycle.NEAR_EXPIRY) {
                                                        if (unitLabel.equals("kg")) {
                                                                nearExpiryUsedKg += consumed;
                                                        } else if (unitLabel.equals("liters")) {
                                                                nearExpiryUsedLiters += consumed;
                                                        }
                                                        rescuedNearExpiryIngredients.add(candidate.getName());
                                                }
                                        }
                                }

                                return new CookDishResult(
                                                dish.getName(),
                                                totalUsedWeightKg,
                                                nearExpiryUsedKg,
                                                nearExpiryUsedLiters,
                                                Map.copyOf(usedByUnit),
                                                List.copyOf(rescuedNearExpiryIngredients)
                                );
                        }

                        private boolean isWeightUnit(String unit) {
                                String normalizedUnit = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
                                return normalizedUnit.equals("kg")
                                                || normalizedUnit.equals("kgs")
                                                || normalizedUnit.equals("kilogram")
                                                || normalizedUnit.equals("kilograms")
                                                || normalizedUnit.equals("unit")
                                                || normalizedUnit.equals("units");
                        }

                        private String normalizeUnitLabel(String unit) {
                                String normalizedUnit = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
                                return switch (normalizedUnit) {
                                        case "kilogram", "kilograms", "kgs", "unit", "units" -> "kg";
                                        case "liter", "litre", "litres" -> "liters";
                                        default -> normalizedUnit.isBlank() ? "unknown" : normalizedUnit;
                                };
                        }

        public List<DishRecipe> getAllRecipes() {
                return dishRepository.findAll();
        }

        public void addRecipe(DishRecipe recipe) {
                dishRepository.save(recipe);
        }

        public void deleteRecipe(Long recipeId) {
                dishRepository.deleteById(recipeId);
        }

        public record CookDishResult(
                        String dishName,
                        double totalUsedWeightKg,
                        double nearExpiryUsedKg,
                        double nearExpiryUsedLiters,
                        Map<String, Double> usedByUnit,
                        List<String> rescuedNearExpiryIngredients
        ) {
        }
}