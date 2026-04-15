package app;

import app.model.NotificationMessage;
import app.notification.InMemoryNotificationStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Fix 2: InMemoryNotificationStore collapses duplicate saves keyed by
 * (tenantId, ingredientId, recipientRole, createdAt-date). These tests are the
 * guard against a regression where `save()` goes back to an unconditional
 * ArrayList.add() and the "refresh = spam" bug returns.
 */
class InMemoryNotificationStoreDedupTest {
    @Test
    void duplicateSameTenantIngredientRoleIsDropped() {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "Subject", "Body"));
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "Subject", "Body"));
        assertEquals(1, store.all().size());
    }

    @Test
    void differentRolesForSameIngredientAreStoredSeparately() {
        // Chef and Manager notifications are conceptually different alerts —
        // the role must be part of the dedup key or escalations get swallowed.
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "Chef subject", "B"));
        store.save(new NotificationMessage("t1", "ing-1", "MANAGER", "Manager subject", "B"));
        assertEquals(2, store.all().size());
    }

    @Test
    void differentIngredientsSameTenantAreStoredSeparately() {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "S", "B"));
        store.save(new NotificationMessage("t1", "ing-2", "CHEF", "S", "B"));
        assertEquals(2, store.all().size());
    }

    @Test
    void differentTenantsSameIngredientAreStoredSeparately() {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "S", "B"));
        store.save(new NotificationMessage("t2", "ing-1", "CHEF", "S", "B"));
        assertEquals(2, store.all().size());
    }

    @Test
    void allForTenantFiltersAndReturnsNewestFirst() {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "t1-first", "B"));
        store.save(new NotificationMessage("t2", "ing-1", "CHEF", "t2-first", "B"));
        store.save(new NotificationMessage("t1", "ing-2", "CHEF", "t1-second", "B"));

        var forT1 = store.allForTenant("t1");
        assertEquals(2, forT1.size());
        assertEquals("t1-second", forT1.get(0).getSubject());
        assertEquals("t1-first", forT1.get(1).getSubject());
    }
}
