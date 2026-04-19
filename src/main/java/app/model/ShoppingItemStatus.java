package app.model;

public enum ShoppingItemStatus {
    PENDING,    // Item is on the shopping list, needs ordering
    PURCHASED,  // Item has been ordered/purchased, removed from list
    IGNORED     // User doesn't want to reorder this item now, removed from list
}
