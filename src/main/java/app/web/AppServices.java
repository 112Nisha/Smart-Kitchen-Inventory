package app.web;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public record AppServices(
        InventoryManager inventoryManager,
        ExpiryAlertService expiryAlertService,
        ShoppingListService shoppingListService,
        DishRecommendationService dishRecommendationService,
        WasteImpactService wasteImpactService,
        NavigationAssistantService navigationAssistantService,
        InMemoryNotificationStore notificationStore
) {
}
