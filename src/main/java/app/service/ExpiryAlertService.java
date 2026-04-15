package app.service;

import app.model.ExpiryAlertContext;
import app.service.IngredientStateTracker;
import app.service.StakeholderNotificationHandler;
import app.model.Ingredient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpiryAlertService {
    private static final double MIN_ALERTABLE_QUANTITY = 1e-9;

    private final InventoryManager inventoryManager;
    private final StakeholderNotificationHandler stakeholderNotifier;
    private final IngredientStateTracker stateTracker;

    public ExpiryAlertService(InventoryManager inventoryManager,
                              StakeholderNotificationHandler stakeholderNotifier) {
        this.inventoryManager = inventoryManager;
        this.stakeholderNotifier = stakeholderNotifier;
        this.stateTracker = new IngredientStateTracker();
    }

    // Subscribes the internal state tracker to the manager's removal events so
    // discards/zeroings drop stale lifecycle entries immediately. Kept out of
    // the constructor so the service has no side effects at construction time
    // (e.g. scheduler tests can build it against a null manager).
    public void attachLifecycleListeners() {
        if (inventoryManager != null) {
            inventoryManager.addIngredientRemovedListener(stateTracker::forget);
        }
    }

    public Map<String, List<ExpiryAlertContext>> evaluateAllTenants() {
        Map<String, List<ExpiryAlertContext>> byTenant = new LinkedHashMap<>();
        for (String tenantId : inventoryManager.listKnownTenants()) {
            try {
                byTenant.put(tenantId, evaluateAndNotify(tenantId));
            } catch (RuntimeException ex) {
                // Log so one poisoned tenant doesn't become silent zero-alert sweeps.
                System.err.println("[ExpiryAlertService] sweep failed for tenant "
                        + tenantId + ": " + ex.getMessage());
                byTenant.put(tenantId, List.of());
            }
        }
        return byTenant;
    }

    public List<ExpiryAlertContext> evaluateAndNotify(String tenantId) {
        List<ExpiryAlertContext> contexts = new ArrayList<>();

        for (Ingredient ingredient : inventoryManager.listIngredients(tenantId)) {
            if (ingredient.getQuantity() <= MIN_ALERTABLE_QUANTITY) {
                continue;
            }
            if (!ingredient.getState().shouldTriggerExpiryAlert()) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(LocalDate.now(), ingredient.getExpiryDate());
            ExpiryAlertContext context = new ExpiryAlertContext(ingredient, days);

            boolean transitioned = stateTracker.recordAndCheckTransition(
                    ingredient.getId(),
                    ingredient.getLifecycle()
            );

            if (transitioned) {
                dispatch(context);
            } else {
                context.addEvent("ExpiryAlertService: No state transition — notifications skipped");
            }

            contexts.add(context);
        }
        return contexts;
    }

    // Single notification per alertable transition; failures are swallowed onto
    // the context so a broken provider doesn't abort the sweep.
    private void dispatch(ExpiryAlertContext context) {
        try {
            stakeholderNotifier.notifyStakeholder(context);
        } catch (RuntimeException ex) {
            context.addEvent("Alert processing failed: " + ex.getMessage());
        }
    }
}
