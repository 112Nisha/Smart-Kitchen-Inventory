package app.repository;

import app.config.DatabaseInitializer;
import app.model.DishRecipe;
import app.model.RecipeIngredient;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DishRepository {

    public DishRepository() {
        DatabaseInitializer.initialize();
    }

    /** finds all dish recipes in the database for a given restaurant. */
    public List<DishRecipe> findAll(Long restaurantId) {
        List<DishRecipe> recipes = new ArrayList<>();

        String recipeSql = """
                SELECT id, name, instructions
                FROM dish_recipes
                WHERE restaurant_id = ?
                ORDER BY id
                """;

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement recipeStmt = conn.prepareStatement(recipeSql)) {

            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            recipeStmt.setLong(1, restaurantId);

            try (ResultSet recipeRs = recipeStmt.executeQuery()) {
                while (recipeRs.next()) {
                    Long recipeId = recipeRs.getLong("id");
                    String name = recipeRs.getString("name");
                    String instructions = recipeRs.getString("instructions");
                    List<RecipeIngredient> ingredients = findIngredientsByRecipeId(conn, restaurantId, recipeId);
                    recipes.add(new DishRecipe(restaurantId, recipeId, name, ingredients, instructions));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recipes", e);
        }

        return recipes;
    }

    /** finds a dish recipe by its ID for a given restaurant. */
    public DishRecipe findById(Long id, Long restaurantId) {
        String recipeSql = """
                SELECT id, name, instructions
                FROM dish_recipes
                WHERE id = ? AND restaurant_id = ?
                """;

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(recipeSql)) {

            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            stmt.setLong(1, id);
            stmt.setLong(2, restaurantId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long recipeId = rs.getLong("id");
                    String name = rs.getString("name");
                    String instructions = rs.getString("instructions");
                    List<RecipeIngredient> ingredients = findIngredientsByRecipeId(conn, restaurantId, recipeId);
                    return new DishRecipe(restaurantId, recipeId, name, ingredients, instructions);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recipe by id", e);
        }

        return null;
    }

    /** saves a dish recipe to the database for a given restaurant. */
    public void save(DishRecipe recipe, Long restaurantId) {
        String nextIdSql = "SELECT COALESCE(MAX(id), 0) + 1 FROM dish_recipes WHERE restaurant_id = ?";
        String recipeSql = "INSERT INTO dish_recipes (restaurant_id, id, name, instructions) VALUES (?, ?, ?, ?)";
        String ingredientSql = "INSERT INTO recipe_ingredients (restaurant_id, recipe_id, ingredient_name, quantity, unit) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl())) {
            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            conn.setAutoCommit(false);

            try {
                Long recipeId;
                try (PreparedStatement nextIdStmt = conn.prepareStatement(nextIdSql)) {
                    nextIdStmt.setLong(1, restaurantId);

                    try (ResultSet rs = nextIdStmt.executeQuery()) {
                        if (rs.next()) {
                            recipeId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to generate next recipe ID.");
                        }
                    }
                }

                try (PreparedStatement recipeStmt = conn.prepareStatement(recipeSql)) {
                    recipeStmt.setLong(1, restaurantId);
                    recipeStmt.setLong(2, recipeId);
                    recipeStmt.setString(3, recipe.getName());
                    recipeStmt.setString(4, recipe.getInstructions());
                    recipeStmt.executeUpdate();
                }

                try (PreparedStatement ingredientStmt = conn.prepareStatement(ingredientSql)) {
                    for (RecipeIngredient ingredient : recipe.getIngredients()) {
                        ingredientStmt.setLong(1, restaurantId);
                        ingredientStmt.setLong(2, recipeId);
                        ingredientStmt.setString(3, ingredient.getName());
                        ingredientStmt.setDouble(4, ingredient.getQuantity());
                        ingredientStmt.setString(5, ingredient.getUnit());
                        ingredientStmt.addBatch();
                    }

                    ingredientStmt.executeBatch();
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save recipe", e);
        }
    }

    /** updates a dish recipe in the database for a given restaurant. */
    public void update(DishRecipe recipe, Long restaurantId) {
    String updateRecipeSql = """
            UPDATE dish_recipes
            SET name = ?, instructions = ?
            WHERE id = ? AND restaurant_id = ?
            """;

    String deleteIngredientsSql = """
            DELETE FROM recipe_ingredients
            WHERE recipe_id = ? AND restaurant_id = ?
            """;

    String insertIngredientSql = """
            INSERT INTO recipe_ingredients (restaurant_id, recipe_id, ingredient_name, quantity, unit)
            VALUES (?, ?, ?, ?, ?)
            """;

    try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl())) {
        conn.createStatement().execute("PRAGMA foreign_keys = ON;");
        conn.setAutoCommit(false);

        try {
            try (PreparedStatement recipeStmt = conn.prepareStatement(updateRecipeSql)) {
                recipeStmt.setString(1, recipe.getName());
                recipeStmt.setString(2, recipe.getInstructions());
                recipeStmt.setLong(3, recipe.getId());
                recipeStmt.setLong(4, restaurantId);
                recipeStmt.executeUpdate();
            }

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteIngredientsSql)) {
                deleteStmt.setLong(1, recipe.getId());
                deleteStmt.setLong(2, restaurantId);
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement ingredientStmt = conn.prepareStatement(insertIngredientSql)) {
                for (RecipeIngredient ingredient : recipe.getIngredients()) {
                    ingredientStmt.setLong(1, restaurantId);
                    ingredientStmt.setLong(2, recipe.getId());
                    ingredientStmt.setString(3, ingredient.getName());
                    ingredientStmt.setDouble(4, ingredient.getQuantity());
                    ingredientStmt.setString(5, ingredient.getUnit());
                    ingredientStmt.addBatch();
                }

                ingredientStmt.executeBatch();
            }

            conn.commit();

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }

    } catch (SQLException e) {
        throw new RuntimeException("Failed to update recipe", e);
    }
}

    /** deletes a dish recipe by its ID for a given restaurant. */
    public void deleteById(Long id, Long restaurantId) {
        String sql = "DELETE FROM dish_recipes WHERE id = ? AND restaurant_id = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            stmt.setLong(1, id);
            stmt.setLong(2, restaurantId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete recipe", e);
        }
    }

    /** finds all ingredients for a given recipe ID and restaurant ID. */
    private List<RecipeIngredient> findIngredientsByRecipeId(Connection conn, Long restaurantId, Long recipeId) throws SQLException {
        List<RecipeIngredient> ingredients = new ArrayList<>();

        String ingredientSql = """
            SELECT ingredient_name, quantity, unit
            FROM recipe_ingredients
            WHERE restaurant_id = ? AND recipe_id = ?
            ORDER BY id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(ingredientSql)) {
            stmt.setLong(1, restaurantId);
            stmt.setLong(2, recipeId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ingredients.add(new RecipeIngredient(
                            rs.getString("ingredient_name"),
                            rs.getDouble("quantity"),
                            rs.getString("unit")
                    ));
                }
            }
        }

        return ingredients;
    }
}