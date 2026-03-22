package app.repository;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.List;

public class DishRepository {
    private final List<DishRecipe> recipes = List.of(
            new DishRecipe("Tomato Basil Pasta", List.of("Tomato", "Basil", "Garlic", "Pasta")),
            new DishRecipe("Veggie Stir Fry", List.of("Bell Pepper", "Onion", "Carrot", "Soy Sauce")),
            new DishRecipe("Herb Omelette", List.of("Egg", "Spinach", "Onion", "Parsley")),
            new DishRecipe("Potato Soup", List.of("Potato", "Onion", "Cream", "Garlic")),
            new DishRecipe("Citrus Salad", List.of("Lettuce", "Orange", "Olive Oil", "Lemon"))
    );

    public List<DishRecipe> findAll() {
        return recipes;
    }
}
