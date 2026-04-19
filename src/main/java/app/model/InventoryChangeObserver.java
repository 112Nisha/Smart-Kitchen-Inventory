package app.alerts;

/**
 * Observer interface for reacting to inventory mutations.
 * Implemented by services that need to react when any ingredient is added, updated, used, or discarded.
 *
 * Pattern: Observer (allows decoupling of InventoryManager from dependent services)
 * Mirrors: ExpiryObserver pattern
 */
public interface InventoryChangeObserver {
    /**
     * Called when any ingredient in the tenant's inventory changes.
     * @param tenantId the tenant whose inventory changed
     */
    void onInventoryChanged(String tenantId);
}
