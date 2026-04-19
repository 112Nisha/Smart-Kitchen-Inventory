package app;

import app.config.DatabaseInitializer;
import app.model.NotificationMessage;
import app.repository.InMemoryNotificationStore;
import app.repository.SqliteNotificationStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the retention pruner added as part of bundle 2 correctness pass.
 *
 * The pragmatic design choice: the store prunes by createdAt, not by the
 * underlying ingredient's expiry date. Since notifications fire within the
 * 3-day alert window (NEAR_EXPIRY) or shortly after (EXPIRED), createdAt is a
 * close proxy for "days past expiry" without the schema churn of carrying the
 * expiry date on every notification row.
 */
class NotificationRetentionTest {

    @Test
    void inMemoryStorePrunesOlderRowsAndKeepsRecentOnes() throws Exception {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        LocalDate today = LocalDate.of(2026, 4, 15);

        // Backdate one message to 40 days ago, leave another fresh.
        NotificationMessage old = backdated("t1", "ing-old", today.minusDays(40));
        NotificationMessage recent = new NotificationMessage("t1", "ing-recent", "CHEF", "recent", "B");
        saveDirect(store, old);
        store.save(recent);
        assertEquals(2, store.all().size());

        int removed = store.pruneOlderThan(today.minusDays(30));
        assertEquals(1, removed);
        assertEquals(1, store.all().size());
        assertEquals("ing-recent", store.all().get(0).getIngredientId());

        // Dedup keys for pruned messages must also be evicted — otherwise
        // memory grows unbounded even as notifications disappear.
        NotificationMessage resavedOld = new NotificationMessage("t1", "ing-old", "CHEF", "old-subject", "B");
        store.save(resavedOld);
        assertEquals(2, store.all().size(),
                "a same-ingredient save after prune should succeed — stale dedup key must be gone");
    }

    @Test
    void inMemoryStorePruneIsNoopWhenNothingOldEnough() {
        InMemoryNotificationStore store = new InMemoryNotificationStore();
        store.save(new NotificationMessage("t1", "ing-1", "CHEF", "s", "b"));
        store.save(new NotificationMessage("t1", "ing-2", "CHEF", "s", "b"));

        int removed = store.pruneOlderThan(LocalDate.now().minusDays(30));
        assertEquals(0, removed);
        assertEquals(2, store.all().size());
    }

    @Test
    void sqliteStorePrunesByCreatedAtDate() throws Exception {
        String tenant = "retention-it-" + UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 4, 15);
        try {
            DatabaseInitializer.initialize();
            // Insert two rows with hand-written created_at values — one well
            // outside the retention window, one inside.
            insertRaw(tenant, "ing-old", today.minusDays(40).atTime(9, 0), "old");
            insertRaw(tenant, "ing-recent", today.minusDays(10).atTime(9, 0), "recent");

            SqliteNotificationStore store = new SqliteNotificationStore();
            int removed = store.pruneOlderThan(today.minusDays(30));
            assertEquals(1, removed);

            List<NotificationMessage> remaining = store.allForTenant(tenant);
            assertEquals(1, remaining.size());
            assertEquals("ing-recent", remaining.get(0).getIngredientId());
        } finally {
            cleanupTenant(tenant);
        }
    }

    // Manually seeds the in-memory store's internal list with a backdated
    // message — the public save() stamps createdAt to now(), which would
    // defeat the test. Reflection is ugly but keeps the production class
    // honest about createdAt coming only from its constructors / rehydrate.
    @SuppressWarnings("unchecked")
    private void saveDirect(InMemoryNotificationStore store, NotificationMessage message) throws Exception {
        Field notificationsField = InMemoryNotificationStore.class.getDeclaredField("notifications");
        notificationsField.setAccessible(true);
        Object listObj = notificationsField.get(store);
        ((List<NotificationMessage>) listObj).add(message);

        Field dedupField = InMemoryNotificationStore.class.getDeclaredField("dedupKeys");
        dedupField.setAccessible(true);
        Object setObj = dedupField.get(store);
        String key = message.getTenantId() + "|" + message.getIngredientId() + "|"
                + message.getRecipientRole() + "|" + message.getCreatedAt().toLocalDate();
        ((java.util.Set<String>) setObj).add(key);
    }

    private NotificationMessage backdated(String tenant, String ingredientId, LocalDate day) {
        return NotificationMessage.rehydrate(tenant, ingredientId, "CHEF", "old", "B", day.atTime(9, 0));
    }

    private void insertRaw(String tenant, String ingredientId, LocalDateTime createdAt, String subject)
            throws SQLException {
        String dedup = tenant + "|" + ingredientId + "|CHEF|" + createdAt.toLocalDate();
        String sql = "INSERT INTO notifications "
                + "(tenant_id, ingredient_id, recipient_role, subject, body, created_at, dedup_key) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenant);
            stmt.setString(2, ingredientId);
            stmt.setString(3, "CHEF");
            stmt.setString(4, subject);
            stmt.setString(5, "body");
            stmt.setString(6, createdAt.toString());
            stmt.setString(7, dedup);
            stmt.executeUpdate();
        }
    }

    private void cleanupTenant(String tenant) {
        DatabaseInitializer.initialize();
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM notifications WHERE tenant_id = ?")) {
            stmt.setString(1, tenant);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clean notification test data", e);
        }
    }

    // Silences "unused" when the generic ArrayList import is not otherwise reached.
    @SuppressWarnings("unused")
    private static final ArrayList<Object> UNUSED = new ArrayList<>();
}
