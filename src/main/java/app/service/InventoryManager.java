package app.service;

import app.alerts.InventoryChangeObserver;
import app.model.Ingredient;
import app.model.IngredientEvent;
import app.repository.IngredientRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

public final class InventoryManager {
    private static final double COMPARISON_EPSILON = 1e-9;

    private static volatile InventoryManager instance;

    private final IngredientRepository ingredientRepository;
    // Supplier so the near-expiry window can be retuned at runtime from the
    // admin config page. refreshState calls ask for the current value each
    // time; a static int would freeze whatever was passed at boot.
    private final IntSupplier nearExpiryDays;
    private final Map<String, List<Ingredient>> tenantCache = new ConcurrentHashMap<>();
    // Listeners notified when an ingredient effectively drops out of the
    // alertable pool — discarded, or consumed to zero quantity. Used by the
    // expiry-alert subsystem to forget per-ingredient tracker state so the
    // state map doesn't grow forever. CopyOnWriteArrayList keeps add/fire
    // lock-free under concurrent read/write, which is fine given listeners
    // register once at startup.
    private final List<IngredientEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final List<InventoryChangeObserver> inventoryObservers = new ArrayList<>();
    // When true on the current thread, Used events are suppressed. Set by
    // callers (e.g. DishRecommendationService) that batch-consume ingredients
    // and fire their own higher-level notification instead.
    private final ThreadLocal<Boolean> suppressUsedEvents = ThreadLocal.withInitial(() -> false);

    private InventoryManager(IngredientRepository ingredientRepository, IntSupplier nearExpiryDays) {
        this.ingredientRepository = ingredientRepository;
        this.nearExpiryDays = nearExpiryDays;
    }

    public static synchronized void resetInstanceForTests() {
        instance = null;
    }

    public static InventoryManager getInstance(IngredientRepository ingredientRepository, int nearExpiryDays) {
        if (nearExpiryDays < 0) {
            throw new IllegalArgumentException("nearExpiryDays must be >= 0");
        }
        // Wrap the literal int in a constant supplier for the legacy call sites
        // (tests, fixtures) that don't need live reload.
        return getInstance(ingredientRepository, (IntSupplier) () -> nearExpiryDays);
    }

    public static InventoryManager getInstance(IngredientRepository ingredientRepository,
                                               IntSupplier nearExpiryDays) {
        Objects.requireNonNull(ingredientRepository, "ingredientRepository is required");
        Objects.requireNonNull(nearExpiryDays, "nearExpiryDays supplier is required");

        if (instance == null) {
            synchronized (InventoryManager.class) {
                if (instance == null) {
                    instance = new InventoryManager(ingredientRepository, nearExpiryDays);
                }
            }
        } else if (instance.ingredientRepository != ingredientRepository) {
            // Repository identity still has to match — swapping repositories
            // under a live singleton would silently double-initialize state.
            // The nearExpiryDays supplier check is intentionally dropped: with
            // live-reload a supplier instance may legitimately change between
            // calls and we can't meaningfully compare two IntSuppliers.
            throw new IllegalStateException("InventoryManager is already initialized with different configuration");
        }
        return instance;
    }

    /**
     * Register an observer to be notified when any ingredient changes.
     * @param observer the observer to add
     */
    public void addInventoryObserver(InventoryChangeObserver observer) {
        Objects.requireNonNull(observer, "observer is required");
        inventoryObservers.add(observer);
    }

    /**
     * Notify all registered observers that inventory has changed for a tenant.
     * @param tenantId the tenant whose inventory changed
     */
    private void notifyInventoryObservers(String tenantId) {
        inventoryObservers.forEach(observer -> observer.onInventoryChanged(tenantId));
    }

    public Ingredient addIngredient(Ingredient ingredient) {
        validateTenantId(Objects.requireNonNull(ingredient, "ingredient is required").getTenantId());
        validateIngredient(ingredient);
        ingredient.setQuantity(roundToTwoDecimals(ingredient.getQuantity()));
        ingredient.setLowStockThreshold(roundToTwoDecimals(ingredient.getLowStockThreshold()));
        Ingredient saved = ingredientRepository.save(ingredient);
        invalidateTenantCache(ingredient.getTenantId());
        notifyInventoryObservers(ingredient.getTenantId());
        return saved;
    }

    public Optional<Ingredient> updateIngredient(String tenantId,
                                                 String ingredientId,
                                                 String name,
                                                 double quantity,
                                                 String unit,
                                                 LocalDate expiryDate,
                                                 double lowStockThreshold) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);
        validateIngredientFields(name, quantity, lowStockThreshold);
        validateUnit(unit);
        Objects.requireNonNull(expiryDate, "expiryDate is required");

        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.setName(name);
            item.setQuantity(roundToTwoDecimals(quantity));
            item.setUnit(unit);
            item.setExpiryDate(expiryDate);
            item.setLowStockThreshold(roundToTwoDecimals(lowStockThreshold));
            item.refreshState(LocalDate.now(), nearExpiryDays.getAsInt());
            ingredientRepository.save(item);
            invalidateTenantCache(tenantId);
            notifyInventoryObservers(tenantId);
            fireEvent(new IngredientEvent.Updated(item));
        });
        return existing;
    }

    public Optional<Ingredient> useIngredient(String tenantId, String ingredientId, double usedQuantity) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);
        if (!Double.isFinite(usedQuantity) || usedQuantity <= 0) {
            throw new IllegalArgumentException("Used quantity must be > 0");
        }

        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.refreshState(LocalDate.now(), nearExpiryDays.getAsInt());
            if (item.getLifecycle() == app.model.IngredientLifecycle.EXPIRED) {
                throw new IllegalArgumentException("Expired ingredients can only be discarded");
            }

            double availableQuantity = item.getQuantity();
            if (usedQuantity - availableQuantity > COMPARISON_EPSILON) {
                throw new IllegalArgumentException("Used quantity cannot exceed available inventory");
            }
            double updated = roundToTwoDecimals(Math.max(0, availableQuantity - usedQuantity));

            if (updated <= COMPARISON_EPSILON) {
                ingredientRepository.deleteById(tenantId, ingredientId);
            } else {
                item.setQuantity(updated);
                item.refreshState(LocalDate.now(), nearExpiryDays.getAsInt());
                ingredientRepository.save(item);
            }
            invalidateTenantCache(tenantId);
            fireEvent(new IngredientEvent.Used(item, usedQuantity));
            if (updated <= COMPARISON_EPSILON) {
                fireEvent(new IngredientEvent.ConsumedToZero(item));
            }
            notifyInventoryObservers(tenantId);
        });
        return existing;
    }

    public Optional<Ingredient> discardIngredient(String tenantId, String ingredientId) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);

        Optional<Ingredient> existing = ingredientRepository.findById(tenantId, ingredientId);
        existing.ifPresent(item -> {
            item.setDiscarded(true);
            item.refreshState(LocalDate.now(), nearExpiryDays.getAsInt());
            ingredientRepository.save(item);
            invalidateTenantCache(tenantId);
            fireEvent(new IngredientEvent.Discarded(item));
            notifyInventoryObservers(tenantId);
        });
        return existing;
    }

    /**
     * Register a callback that fires when an ingredient effectively leaves the
     * alertable pool (discarded or consumed to zero). Listeners must be
     * registered at wiring time; there is no unregister path because the
     * subsystems that use this (expiry tracker) share the lifecycle of the
     * manager itself.
     */
    public void addListener(IngredientEventListener listener) {
        eventListeners.add(Objects.requireNonNull(listener, "listener is required"));
    }

    public void setSuppressUsedEvents(boolean suppress) {
        suppressUsedEvents.set(suppress);
    }

    private void fireEvent(IngredientEvent event) {
        if (event instanceof IngredientEvent.Used && suppressUsedEvents.get()) {
            return;
        }
        for (IngredientEventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                System.err.println("[InventoryManager] event listener failed: " + ex.getMessage());
            }
        }
    }

    // Returns every tenant id currently known to the repository. Used by the
    // scheduled expiry-alert runner so it can iterate tenants without a
    // hard-coded list. Delegates to the repository so SQLite vs. in-memory
    // backing stays transparent to callers.
    public Set<String> listKnownTenants() {
        return ingredientRepository.findAllTenantIds();
    }

    public List<Ingredient> listIngredients(String tenantId) {
        // Discarded items stay in the repository (audit trail for waste
        // reporting) but are not "current inventory" from the app's
        // perspective — hiding them here keeps the UI, dashboard counts, and
        // expiry sweep consistent without each caller re-implementing the
        // filter. Callers that need discarded rows use
        // listIngredientsIncludingDiscarded or findById.
        return listIngredientsIncludingDiscarded(tenantId).stream()
                .filter(item -> !item.isDiscarded())
                .toList();
    }

    // Returns every row for the tenant, discarded or not. Used by views that
    // need to render a discarded item differently (e.g. the notifications page
    // pushes discarded rows to the bottom rather than hiding them entirely).
    public List<Ingredient> listIngredientsIncludingDiscarded(String tenantId) {
        validateTenantId(tenantId);
        List<Ingredient> items = tenantCache.computeIfAbsent(tenantId, key -> ingredientRepository.findByTenant(tenantId));
        items.forEach(item -> {
            normalizePrecision(item);
            item.refreshState(LocalDate.now(), nearExpiryDays.getAsInt());
        });
        return items;
    }

    public Optional<Ingredient> findById(String tenantId, String ingredientId) {
        validateTenantId(tenantId);
        validateIngredientId(ingredientId);
        Optional<Ingredient> found = ingredientRepository.findById(tenantId, ingredientId);
        found.ifPresent(item -> {
            normalizePrecision(item);
            item.refreshState(LocalDate.now(), nearExpiryDays.getAsInt());
        });
        return found;
    }

    private void normalizePrecision(Ingredient ingredient) {
        ingredient.setQuantity(roundToTwoDecimals(ingredient.getQuantity()));
        ingredient.setLowStockThreshold(roundToTwoDecimals(ingredient.getLowStockThreshold()));
    }

    private double roundToTwoDecimals(double value) {
        double rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return rounded == -0.0d ? 0.0d : rounded;
    }

    private void validateIngredient(Ingredient ingredient) {
        validateIngredientFields(
                ingredient.getName(),
                ingredient.getQuantity(),
                ingredient.getLowStockThreshold()
        );
        validateUnit(ingredient.getUnit());
        Objects.requireNonNull(ingredient.getExpiryDate(), "expiryDate is required");
    }

    private void validateIngredientFields(String name, double quantity, double lowStockThreshold) {
        if (Objects.requireNonNull(name, "name is required").isBlank()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        if (!Double.isFinite(quantity) || quantity < 0) {
            throw new IllegalArgumentException("Quantity must be >= 0");
        }
        if (!Double.isFinite(lowStockThreshold) || lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low stock threshold must be >= 0");
        }
    }

    private void validateTenantId(String tenantId) {
        if (Objects.requireNonNull(tenantId, "tenantId is required").isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    private void validateIngredientId(String ingredientId) {
        if (Objects.requireNonNull(ingredientId, "ingredientId is required").isBlank()) {
            throw new IllegalArgumentException("ingredientId must not be blank");
        }
    }

    private void validateUnit(String unit) {
        if (Objects.requireNonNull(unit, "unit is required").isBlank()) {
            throw new IllegalArgumentException("Unit must not be blank");
        }
    }

    private void invalidateTenantCache(String tenantId) {
        tenantCache.remove(tenantId);
    }
}
