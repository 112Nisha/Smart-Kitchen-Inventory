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
  
    /** finds all dish recipes in the database. */
    public List<DishRecipe> findAll() {
        List<DishRecipe> recipes = new ArrayList<>();

        String recipeSql = "SELECT id, name, instructions FROM dish_recipes ORDER BY id";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement recipeStmt = conn.prepareStatement(recipeSql);
             ResultSet recipeRs = recipeStmt.executeQuery()) {

            conn.createStatement().execute("PRAGMA foreign_keys = ON;");

            while (recipeRs.next()) {
                Long recipeId = recipeRs.getLong("id");
                String name = recipeRs.getString("name");
                String instructions = recipeRs.getString("instructions");

                List<RecipeIngredient> ingredients = findIngredientsByRecipeId(conn, recipeId);

                recipes.add(new DishRecipe(recipeId, name, ingredients, instructions));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recipes", e);
        }

        return recipes;
    }

    /** finds a dish recipe by its ID. */
    public DishRecipe findById(Long id) {
        String recipeSql = "SELECT id, name, instructions FROM dish_recipes WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(recipeSql)) {

            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long recipeId = rs.getLong("id");
                    String name = rs.getString("name");
                    String instructions = rs.getString("instructions");

                    List<RecipeIngredient> ingredients = findIngredientsByRecipeId(conn, recipeId);

                    return new DishRecipe(recipeId, name, ingredients, instructions);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recipe by id", e);
        }

        return null;
    }

    /** saves a dish recipe to the database. */
    public void save(DishRecipe recipe) {
        String recipeSql = "INSERT INTO dish_recipes (name, instructions) VALUES (?, ?)";
        String ingredientSql = "INSERT INTO recipe_ingredients (recipe_id, ingredient_name, quantity, unit) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl())) {
            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            conn.setAutoCommit(false);

            try (PreparedStatement recipeStmt = conn.prepareStatement(recipeSql, Statement.RETURN_GENERATED_KEYS)) {
                recipeStmt.setString(1, recipe.getName());
                recipeStmt.setString(2, recipe.getInstructions());
                recipeStmt.executeUpdate();

                Long recipeId = null;

                try (ResultSet rs = recipeStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        recipeId = rs.getLong(1);
                    }
                }

                if (recipeId == null) {
                    throw new SQLException("Failed to insert recipe.");
                }

                try (PreparedStatement ingredientStmt = conn.prepareStatement(ingredientSql)) {
                    for (RecipeIngredient ingredient : recipe.getIngredients()) {
                        ingredientStmt.setLong(1, recipeId);
                        ingredientStmt.setString(2, ingredient.getName());
                        ingredientStmt.setDouble(3, ingredient.getQuantity());
                        ingredientStmt.setString(4, ingredient.getUnit());
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

    /** deletes a dish recipe by its ID. */
    public void deleteById(Long id) {
        String sql = "DELETE FROM dish_recipes WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.createStatement().execute("PRAGMA foreign_keys = ON;");
            stmt.setLong(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete recipe", e);
        }
    }

    /** finds all ingredients for a given recipe ID. */
    private List<RecipeIngredient> findIngredientsByRecipeId(Connection conn, Long recipeId) throws SQLException {
        List<RecipeIngredient> ingredients = new ArrayList<>();

        String ingredientSql = """
            SELECT ingredient_name, quantity, unit
            FROM recipe_ingredients
            WHERE recipe_id = ?
            ORDER BY id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(ingredientSql)) {
            stmt.setLong(1, recipeId);

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