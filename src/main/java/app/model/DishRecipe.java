package app.model;

import java.util.List;

public class DishRecipe {
    private Long id; 
    private Long restaurantId;
    private String name;
    private List<RecipeIngredient> ingredients;
    private String instructions;

    public DishRecipe(Long restaurantId, Long id, String name, List<RecipeIngredient> ingredients, String instructions) {
        this.restaurantId = restaurantId;
        this.id = id;
        this.name = name;
        this.ingredients = ingredients;
        this.instructions = instructions;
    }

    public DishRecipe(Long restaurantId, String name, List<RecipeIngredient> ingredients, String instructions) {
        this(restaurantId, null, name, ingredients, instructions);
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    public String getInstructions() {
        return instructions;
    }
}