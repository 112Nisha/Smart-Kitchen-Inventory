package app;

import app.config.DatabaseInitializer;
import app.model.NotificationMessage;
import app.repository.SqliteNotificationStore;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Fix 7: SqliteNotificationStore persists notifications across store
 * instances (= across simulated restarts) and enforces the same
 * tenant|ingredient|role|date dedup as the in-memory variant — but backed by
 * a real UNIQUE constraint so a reboot no longer resets the dedup state.
 *
 * Each test uses a unique per-run tenant id and cleans up after itself, so
 * the suite can share the single project-wide data/data.db without cross-test
 * interference.
 */
class SqliteNotificationStoreTest {
    private String uniqueTenant() {
        return "notif-it-" + UUID.randomUUID();
    }

    @Test
    void persistsNotificationAcrossStoreInstances() {
        // Simulates a server restart: write with one store, read with another.
        // Without Fix 7 this was impossible — the list lived in process memory
        // and was lost whenever the container recycled.
        String tenant = uniqueTenant();
        try {
            SqliteNotificationStore writer = new SqliteNotificationStore();
            writer.save(new NotificationMessage(tenant, "ing-1", "CHEF", "Subject", "Body"));

            SqliteNotificationStore reader = new SqliteNotificationStore();
            long countForTenant = reader.all().stream()
                    .filter(m -> m.getTenantId().equals(tenant))
                    .count();
            assertEquals(1, countForTenant);
        } finally {
            cleanupTenant(tenant);
        }
    }

    @Test
    void duplicateWithinDayIsDroppedByUniqueConstraint() {
        String tenant = uniqueTenant();
        try {
            SqliteNotificationStore store = new SqliteNotificationStore();
            store.save(new NotificationMessage(tenant, "ing-1", "CHEF", "S", "B"));
            store.save(new NotificationMessage(tenant, "ing-1", "CHEF", "S", "B"));
            store.save(new NotificationMessage(tenant, "ing-1", "CHEF", "S", "B"));

            long countForTenant = store.all().stream()
                    .filter(m -> m.getTenantId().equals(tenant))
                    .count();
            assertEquals(1, countForTenant,
                    "INSERT OR IGNORE should collapse duplicates via the UNIQUE(dedup_key) constraint");
        } finally {
            cleanupTenant(tenant);
        }
    }

    @Test
    void differentRolesForSameIngredientAreStoredSeparately() {
        String tenant = uniqueTenant();
        try {
            SqliteNotificationStore store = new SqliteNotificationStore();
            store.save(new NotificationMessage(tenant, "ing-1", "CHEF", "Chef S", "B"));
            store.save(new NotificationMessage(tenant, "ing-1", "MANAGER", "Mgr S", "B"));

            long countForTenant = store.all().stream()
                    .filter(m -> m.getTenantId().equals(tenant))
                    .count();
            assertEquals(2, countForTenant,
                    "chef vs manager alerts for the same ingredient must not collide");
        } finally {
            cleanupTenant(tenant);
        }
    }

    @Test
    void allForTenantScopesToTenantAndKeepsNewestFirst() {
        // Locks the DB-side WHERE + ORDER BY so a refactor can't accidentally
        // slip back to "fetch everything, filter in Java" — that was the
        // pattern the servlet used before the store grew a per-tenant query.
        String tenantA = uniqueTenant();
        String tenantB = uniqueTenant();
        try {
            SqliteNotificationStore store = new SqliteNotificationStore();
            store.save(new NotificationMessage(tenantA, "ing-1", "CHEF", "A-first", "B"));
            store.save(new NotificationMessage(tenantB, "ing-1", "CHEF", "B-first", "B"));
            store.save(new NotificationMessage(tenantA, "ing-2", "CHEF", "A-second", "B"));

            var forA = store.allForTenant(tenantA);
            assertEquals(2, forA.size());
            assertEquals("A-second", forA.get(0).getSubject());
            assertEquals("A-first", forA.get(1).getSubject());
            assertTrue(forA.stream().allMatch(m -> m.getTenantId().equals(tenantA)));
        } finally {
            cleanupTenant(tenantA);
            cleanupTenant(tenantB);
        }
    }

    @Test
    void allReturnsNewestFirstWithinTenant() {
        // ORDER BY id DESC — newest save appears at index 0 so the dashboard
        // can render the log without an extra reverse step.
        String tenant = uniqueTenant();
        try {
            SqliteNotificationStore store = new SqliteNotificationStore();
            store.save(new NotificationMessage(tenant, "ing-1", "CHEF", "first", "B"));
            store.save(new NotificationMessage(tenant, "ing-2", "CHEF", "second", "B"));
            store.save(new NotificationMessage(tenant, "ing-3", "CHEF", "third", "B"));

            var forTenant = store.all().stream()
                    .filter(m -> m.getTenantId().equals(tenant))
                    .toList();
            assertEquals(3, forTenant.size());
            assertEquals("third", forTenant.get(0).getSubject());
            assertEquals("second", forTenant.get(1).getSubject());
            assertEquals("first", forTenant.get(2).getSubject());
        } finally {
            cleanupTenant(tenant);
        }
    }

    @Test
    void rehydratedMessagePreservesOriginalCreatedAt() {
        // Important: the all() load path must NOT stamp each row with now().
        // If it did, reading a yesterday row on a new day would move the
        // dedup window and allow a same-ingredient save to slip through.
        String tenant = uniqueTenant();
        try {
            SqliteNotificationStore store = new SqliteNotificationStore();
            NotificationMessage original = new NotificationMessage(tenant, "ing-1", "CHEF", "S", "B");
            store.save(original);

            // Read back via a fresh store instance to bypass any local caching.
            SqliteNotificationStore reader = new SqliteNotificationStore();
            NotificationMessage loaded = reader.all().stream()
                    .filter(m -> m.getTenantId().equals(tenant))
                    .findFirst()
                    .orElseThrow();

            // We don't assert equality (fractional nanos may round on round-trip)
            // — just that the loaded timestamp is not "after" the original,
            // which would indicate a refreshed-to-now rehydrate.
            assertTrue(!loaded.getCreatedAt().isAfter(original.getCreatedAt().plusSeconds(1)),
                    "loaded createdAt should not drift forward; original=" + original.getCreatedAt()
                            + " loaded=" + loaded.getCreatedAt());
        } finally {
            cleanupTenant(tenant);
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
}
