package app.model;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DishRecipe {
    private final String name;
    private final List<String> ingredientNames;

    public DishRecipe(String name, List<String> ingredientNames) {
        this.name = Objects.requireNonNull(name, "name is required").trim();
        this.ingredientNames = List.copyOf(ingredientNames);
    }

    public String getName() {
        return name;
    }

    public List<String> getIngredientNames() {
        return Collections.unmodifiableList(ingredientNames);
    }
}
