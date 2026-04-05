package app.service;

import app.model.DishRecipe;
import app.model.Ingredient;
import app.model.IngredientLifecycle;
import app.model.RecipeIngredient;
import app.repository.DishRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DishRecommendationService {
        private static final double COVERAGE_WEIGHT = 0.45;
        private static final double URGENCY_WEIGHT = 0.35;
        private static final double QUANTITY_WEIGHT = 0.20;

        private final InventoryManager inventoryManager;
        private final DishRepository dishRepository;

        /* returns a list of recommended dishes based on available ingredients */
        public DishRecommendationService(InventoryManager inventoryManager, DishRepository dishRepository) {
                this.inventoryManager = inventoryManager;
                this.dishRepository = dishRepository;
        }

        public List<DishRecipe> suggestDishes(String tenantId) {

                return suggestDishesByExpiryPriority(tenantId).stream()
                                .map(DishSuggestion::getDish)
                                .toList();
        }

        public List<DishSuggestion> suggestDishesByExpiryPriority(String tenantId) {

                List<Ingredient> inventory = inventoryManager.listIngredients(tenantId);
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                Map<String, List<Ingredient>> inventoryMap = inventory.stream()
                                .filter(ingredient -> ingredient.getState().canRecommendInDish())
                                .filter(ingredient -> ingredient.getQuantity() > 1e-9)
                                .collect(Collectors.groupingBy(
                                                ingredient -> ingredient.getName().toLowerCase(Locale.ROOT)));

                LocalDate today = LocalDate.now();
                List<DishSuggestion> suggestions = dishRepository.findAll(restaurantId).stream()
                                .map(dish -> buildDishSuggestion(dish, inventoryMap, today))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toCollection(ArrayList::new));

                suggestions.sort(
                                Comparator.comparingDouble(DishSuggestion::getExpiryRescueScore).reversed()
                                                .thenComparing(
                                                                Comparator.comparingInt(
                                                                                DishSuggestion::getExpiringIngredientCount)
                                                                                .reversed())
                                                .thenComparing(
                                                                suggestion -> suggestion.getDish().getName(),
                                                                String.CASE_INSENSITIVE_ORDER));

                return List.copyOf(suggestions);
        }

        private Optional<DishSuggestion> buildDishSuggestion(
                        DishRecipe dish,
                        Map<String, List<Ingredient>> inventoryMap,
                        LocalDate today) {
                List<SuggestionIngredient> suggestionIngredients = new ArrayList<>();

                double totalRequiredQuantity = 0.0;
                double nearExpiryUsedQuantity = 0.0;
                double urgencyAccumulator = 0.0;
                int nearExpiryIngredientCount = 0;

                for (RecipeIngredient recipeIngredient : dish.getIngredients()) {
                        String ingredientKey = recipeIngredient.getName().toLowerCase(Locale.ROOT);
                        String recipeUnit = normalizeComparableUnit(recipeIngredient.getUnit());

                        List<Ingredient> matchingInventory = inventoryMap.getOrDefault(ingredientKey, List.of())
                                        .stream()
                                        .filter(ingredient -> normalizeComparableUnit(ingredient.getUnit())
                                                        .equals(recipeUnit))
                                        .toList();

                        double availableQuantity = matchingInventory.stream()
                                        .mapToDouble(Ingredient::getQuantity)
                                        .sum();

                        if (availableQuantity + 1e-9 < recipeIngredient.getQuantity()) {
                                return Optional.empty();
                        }

                        totalRequiredQuantity += Math.max(0, recipeIngredient.getQuantity());

                        double nearExpiryAvailableQuantity = matchingInventory.stream()
                                        .filter(ingredient -> ingredient
                                                        .getLifecycle() == IngredientLifecycle.NEAR_EXPIRY)
                                        .mapToDouble(Ingredient::getQuantity)
                                        .sum();

                        double nearExpiryUsedForIngredient = Math.min(
                                        recipeIngredient.getQuantity(),
                                        nearExpiryAvailableQuantity);
                        boolean expiringSoon = nearExpiryUsedForIngredient > 1e-9;

                        String emoji = "";
                        String expiryHint = "";

                        if (expiringSoon) {
                                nearExpiryIngredientCount++;
                                nearExpiryUsedQuantity += nearExpiryUsedForIngredient;

                                long daysUntilExpiry = matchingInventory.stream()
                                                .filter(ingredient -> ingredient
                                                                .getLifecycle() == IngredientLifecycle.NEAR_EXPIRY)
                                                .mapToLong(ingredient -> Math.max(0,
                                                                ChronoUnit.DAYS.between(today,
                                                                                ingredient.getExpiryDate())))
                                                .min()
                                                .orElse(0L);

                                urgencyAccumulator += urgencyScoreByDays(daysUntilExpiry);
                                emoji = buildExpiryEmoji(daysUntilExpiry);
                                expiryHint = buildExpiryHint(daysUntilExpiry);
                        }

                        suggestionIngredients.add(new SuggestionIngredient(
                                        recipeIngredient.getName(),
                                        roundToTwoDecimals(recipeIngredient.getQuantity()),
                                        normalizeUnitLabel(recipeIngredient.getUnit()),
                                        expiringSoon,
                                        emoji,
                                        expiryHint));
                }

                double expiryRescueScore = calculateExpiryRescueScore(
                                dish.getIngredients().size(),
                                nearExpiryIngredientCount,
                                urgencyAccumulator,
                                totalRequiredQuantity,
                                nearExpiryUsedQuantity);

                return Optional.of(new DishSuggestion(
                                dish,
                                expiryRescueScore,
                                nearExpiryIngredientCount,
                                List.copyOf(suggestionIngredients)));
        }

        private double calculateExpiryRescueScore(
                        int totalIngredientCount,
                        int nearExpiryIngredientCount,
                        double urgencyAccumulator,
                        double totalRequiredQuantity,
                        double nearExpiryUsedQuantity) {
                if (totalIngredientCount <= 0) {
                        return 0.0;
                }

                double nearExpiryCoverage = nearExpiryIngredientCount / (double) totalIngredientCount;
                double urgency = nearExpiryIngredientCount == 0 ? 0.0 : urgencyAccumulator / nearExpiryIngredientCount;
                double rescueQuantityShare = totalRequiredQuantity <= 1e-9 ? 0.0
                                : nearExpiryUsedQuantity / totalRequiredQuantity;

                double score = 100.0 * ((COVERAGE_WEIGHT * nearExpiryCoverage)
                                + (URGENCY_WEIGHT * urgency)
                                + (QUANTITY_WEIGHT * rescueQuantityShare));

                return roundToTwoDecimals(Math.max(0.0, Math.min(100.0, score)));
        }

        private double urgencyScoreByDays(long daysUntilExpiry) {
                long clampedDays = Math.max(0, Math.min(daysUntilExpiry, 7));
                return 1.0 - (clampedDays / 7.0);
        }

        private String buildExpiryEmoji(long daysUntilExpiry) {
                if (daysUntilExpiry <= 1) {
                        return "&#128308;";
                }
                if (daysUntilExpiry <= 3) {
                        return "&#128992;";
                }
                return "&#128993;";
        }

        private String buildExpiryHint(long daysUntilExpiry) {
                if (daysUntilExpiry <= 0) {
                        return "This is expiring today! Try to use now.";
                }
                if (daysUntilExpiry == 1) {
                        return "This is expiring tomorrow! Try to use.";
                }
                if (daysUntilExpiry <= 3) {
                        return "This is expiring soon! Try to use.";
                }
                return "Use this week to avoid waste.";
        }

        private String normalizeComparableUnit(String unit) {
                String normalizedUnit = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
                return switch (normalizedUnit) {
                        case "kilogram", "kilograms", "kg", "kgs", "unit", "units" -> "kg";
                        case "liter", "litre", "liters", "litres" -> "liters";
                        default -> normalizedUnit;
                };
        }

        private double roundToTwoDecimals(double value) {
                return Math.round(value * 100.0) / 100.0;
        }

        /**
         * estimates the quantity of ingredients that would be saved by cooking a
         * specific dish
         */
        public double estimatedSavedIngredientQuantity(String tenantId, String dishName) {
                Long restaurantId = getRestaurantIdFromTenant(tenantId);

                List<Ingredient> nearExpiry = inventoryManager.listIngredients(tenantId).stream()
                                .filter(ingredient -> ingredient.getState().canRecommendInDish())
                                .toList();

                return dishRepository.findAll(restaurantId).stream()
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
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                DishRecipe dish = dishRepository.findAll(restaurantId).stream()
                                .filter(candidate -> candidate.getName().equalsIgnoreCase(dishName))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Dish not found: " + dishName));

                List<Ingredient> inventorySnapshot = inventoryManager.listIngredients(tenantId);
                List<PlannedConsumption> plannedConsumptions = new ArrayList<>();

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
                                        .filter(ingredient -> ingredient.getName().toLowerCase(Locale.ROOT)
                                                        .equals(targetName))
                                        .filter(ingredient -> ingredient.getUnit().toLowerCase(Locale.ROOT)
                                                        .equals(targetUnit))
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

                        String unitLabel = normalizeUnitLabel(recipeIngredient.getUnit());
                        boolean weightUnit = isWeightUnit(recipeIngredient.getUnit());
                        double remainingToConsume = requiredQuantity;

                        for (Ingredient candidate : candidates) {
                                if (remainingToConsume <= 1e-9) {
                                        break;
                                }

                                double consumed = Math.min(candidate.getQuantity(), remainingToConsume);
                                if (consumed <= 0) {
                                        continue;
                                }

                                // Reserve quantities in-memory first so validation and deduction are
                                // consistent.
                                candidate.setQuantity(candidate.getQuantity() - consumed);
                                remainingToConsume -= consumed;
                                plannedConsumptions.add(new PlannedConsumption(
                                                candidate.getId(),
                                                candidate.getName(),
                                                consumed,
                                                unitLabel,
                                                weightUnit,
                                                candidate.getLifecycle() == IngredientLifecycle.NEAR_EXPIRY));
                        }

                        if (remainingToConsume > 1e-9) {
                                throw new IllegalStateException("Insufficient inventory for ingredient: "
                                                + recipeIngredient.getName() + " (required " + requiredQuantity + " "
                                                + recipeIngredient.getUnit() + ")");
                        }
                }

                for (PlannedConsumption plannedConsumption : plannedConsumptions) {
                        Optional<Ingredient> updated = inventoryManager.useIngredient(
                                        tenantId,
                                        plannedConsumption.ingredientId(),
                                        plannedConsumption.quantity());
                        if (updated.isEmpty()) {
                                throw new IllegalStateException("Ingredient disappeared during cook log: "
                                                + plannedConsumption.ingredientName());
                        }

                        usedByUnit.merge(plannedConsumption.unitLabel(), plannedConsumption.quantity(), Double::sum);

                        if (plannedConsumption.weightUnit()) {
                                totalUsedWeightKg += plannedConsumption.quantity();
                        }

                        if (plannedConsumption.nearExpiry()) {
                                if (plannedConsumption.unitLabel().equals("kg")) {
                                        nearExpiryUsedKg += plannedConsumption.quantity();
                                } else if (plannedConsumption.unitLabel().equals("liters")) {
                                        nearExpiryUsedLiters += plannedConsumption.quantity();
                                }
                                rescuedNearExpiryIngredients.add(plannedConsumption.ingredientName());
                        }
                }

                return new CookDishResult(
                                dish.getName(),
                                totalUsedWeightKg,
                                nearExpiryUsedKg,
                                nearExpiryUsedLiters,
                                Map.copyOf(usedByUnit),
                                List.copyOf(rescuedNearExpiryIngredients));
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

        public List<DishRecipe> getAllRecipes(String tenantId) {
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                return dishRepository.findAll(restaurantId);
        }

        public void addRecipe(String tenantId, DishRecipe recipe) {
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                dishRepository.save(recipe, restaurantId);
        }

        public void updateRecipe(String tenantId, DishRecipe recipe) {
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                dishRepository.update(recipe, restaurantId);
        }

        public void deleteRecipe(String tenantId, Long recipeId) {
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                dishRepository.deleteById(recipeId, restaurantId);
        }

        public DishRecipe getRecipeById(String tenantId, Long recipeId) {
                Long restaurantId = getRestaurantIdFromTenant(tenantId);
                return dishRepository.findById(recipeId, restaurantId);
        }

        public static final class DishSuggestion {
                private final DishRecipe dish;
                private final double expiryRescueScore;
                private final int expiringIngredientCount;
                private final List<SuggestionIngredient> ingredients;

                public DishSuggestion(
                                DishRecipe dish,
                                double expiryRescueScore,
                                int expiringIngredientCount,
                                List<SuggestionIngredient> ingredients) {
                        this.dish = dish;
                        this.expiryRescueScore = expiryRescueScore;
                        this.expiringIngredientCount = expiringIngredientCount;
                        this.ingredients = ingredients;
                }

                public DishRecipe getDish() {
                        return dish;
                }

                public double getExpiryRescueScore() {
                        return expiryRescueScore;
                }

                public int getExpiringIngredientCount() {
                        return expiringIngredientCount;
                }

                public List<SuggestionIngredient> getIngredients() {
                        return ingredients;
                }
        }

        public static final class SuggestionIngredient {
                private final String name;
                private final double quantity;
                private final String unit;
                private final boolean expiringSoon;
                private final String emoji;
                private final String expiryHint;

                public SuggestionIngredient(
                                String name,
                                double quantity,
                                String unit,
                                boolean expiringSoon,
                                String emoji,
                                String expiryHint) {
                        this.name = name;
                        this.quantity = quantity;
                        this.unit = unit;
                        this.expiringSoon = expiringSoon;
                        this.emoji = emoji;
                        this.expiryHint = expiryHint;
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

                public boolean isExpiringSoon() {
                        return expiringSoon;
                }

                public String getEmoji() {
                        return emoji;
                }

                public String getExpiryHint() {
                        return expiryHint;
                }
        }

        public record CookDishResult(
                        String dishName,
                        double totalUsedWeightKg,
                        double nearExpiryUsedKg,
                        double nearExpiryUsedLiters,
                        Map<String, Double> usedByUnit,
                        List<String> rescuedNearExpiryIngredients) {
        }

        private record PlannedConsumption(
                        String ingredientId,
                        String ingredientName,
                        double quantity,
                        String unitLabel,
                        boolean weightUnit,
                        boolean nearExpiry) {
        }

        /** gets the restaurant id when given the restaurant name as tenant ID */
        private Long getRestaurantIdFromTenant(String tenantId) {
                String sql = "SELECT id FROM restaurants WHERE name = ?";

                try (java.sql.Connection conn = java.sql.DriverManager
                                .getConnection(app.config.DatabaseInitializer.getUrl());
                                java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setString(1, tenantId);

                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                        return rs.getLong("id");
                                }
                        }

                } catch (java.sql.SQLException e) {
                        throw new RuntimeException("Failed to find restaurant ID for tenant: " + tenantId, e);
                }

                throw new IllegalArgumentException("Unknown tenant: " + tenantId);
        }
}