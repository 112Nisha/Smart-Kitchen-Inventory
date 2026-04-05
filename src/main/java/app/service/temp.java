// package app.service;

// import app.model.DishRecipe;
// import app.model.Ingredient;
// import app.model.RecipeIngredient;
// import app.repository.DishRepository;

// import java.util.List;
// import java.util.Locale;
// import java.util.Map;
// import java.util.stream.Collectors;

// public class DishRecommendationService {
//         private final InventoryManager inventoryManager;
//         private final DishRepository dishRepository;

//         /* returns a list of recommended dishes based on available ingredients */
//         public DishRecommendationService(InventoryManager inventoryManager, DishRepository dishRepository) {
//                 this.inventoryManager = inventoryManager;
//                 this.dishRepository = dishRepository;
//         }

//         public List<DishRecipe> suggestDishes(String tenantId) {
//                 Long restaurantId = getRestaurantIdFromTenant(tenantId);

//                 List<Ingredient> inventory = inventoryManager.listIngredients(tenantId);
//                 Map<String, List<Ingredient>> inventoryMap = inventory.stream()
//                                 .filter(ingredient -> ingredient.getState().canRecommendInDish())
//                                 .collect(Collectors.groupingBy(
//                                                 ingredient -> ingredient.getName().toLowerCase(Locale.ROOT)));

//                 return dishRepository.findAll(restaurantId).stream()
//                                 .filter(dish -> dish.getIngredients().stream()
//                                                 .allMatch(recipeIngredient -> {
//                                                         String name = recipeIngredient.getName()
//                                                                         .toLowerCase(Locale.ROOT);

//                                                         if (!inventoryMap.containsKey(name)) {
//                                                                 return false;
//                                                         }

//                                                         double totalAvailable = inventoryMap.get(name).stream()
//                                                                         .filter(inv -> inv.getUnit().equalsIgnoreCase(
//                                                                                         recipeIngredient.getUnit()))
//                                                                         .mapToDouble(Ingredient::getQuantity)
//                                                                         .sum();

//                                                         return totalAvailable >= recipeIngredient.getQuantity();
//                                                 }))
//                                 .toList();
//         }

//         /**
//          * estimates the quantity of ingredients that would be saved by cooking a
//          * specific dish
//          */
//         public double estimatedSavedIngredientQuantity(String tenantId, String dishName) {
//                 Long restaurantId = getRestaurantIdFromTenant(tenantId);

//                 List<Ingredient> nearExpiry = inventoryManager.listIngredients(tenantId).stream()
//                                 .filter(ingredient -> ingredient.getState().canRecommendInDish())
//                                 .toList();

//                 return dishRepository.findAll(restaurantId).stream()
//                                 .filter(dish -> dish.getName().equalsIgnoreCase(dishName))
//                                 .findFirst()
//                                 .map(dish -> dish.getIngredients().stream()
//                                                 .filter(recipeIngredient -> nearExpiry.stream()
//                                                                 .anyMatch(inv -> inv.getName().equalsIgnoreCase(
//                                                                                 recipeIngredient.getName())))
//                                                 .mapToDouble(RecipeIngredient::getQuantity)
//                                                 .sum())
//                                 .orElse(0.0);
//         }

//         public List<DishRecipe> getAllRecipes(String tenantId) {
//                 Long restaurantId = getRestaurantIdFromTenant(tenantId);
//                 return dishRepository.findAll(restaurantId);
//         }

//         public void addRecipe(String tenantId, DishRecipe recipe) {
//                 Long restaurantId = getRestaurantIdFromTenant(tenantId);
//                 dishRepository.save(recipe, restaurantId);
//         }

//         public void deleteRecipe(String tenantId, Long recipeId) {
//                 Long restaurantId = getRestaurantIdFromTenant(tenantId);
//                 dishRepository.deleteById(recipeId, restaurantId);
//         }

//         /** gets the restaurant id when given the restaurant name as tenant ID */
//         private Long getRestaurantIdFromTenant(String tenantId) {
//                 String sql = "SELECT id FROM restaurants WHERE name = ?";

//                 try (java.sql.Connection conn = java.sql.DriverManager
//                                 .getConnection(app.config.DatabaseInitializer.getUrl());
//                                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {

//                         stmt.setString(1, tenantId);

//                         try (java.sql.ResultSet rs = stmt.executeQuery()) {
//                                 if (rs.next()) {
//                                         return rs.getLong("id");
//                                 }
//                         }

//                 } catch (java.sql.SQLException e) {
//                         throw new RuntimeException("Failed to find restaurant ID for tenant: " + tenantId, e);
//                 }

//                 throw new IllegalArgumentException("Unknown tenant: " + tenantId);
//         }
// }