package app.model;

public sealed interface IngredientEvent {

    record Used(Ingredient ingredient, double quantityUsed) implements IngredientEvent {}

    record Discarded(Ingredient ingredient) implements IngredientEvent {}

    record ConsumedToZero(Ingredient ingredient) implements IngredientEvent {}
}
