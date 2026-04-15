package app;

import app.config.AlertConfig;
import app.config.AlertConfigService;
import app.config.DatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers Bundle 3's live-reloadable config service. The service writes to the
 * shared {@code data/data.db}; to keep the production rows intact, each test
 * snapshots the existing rows, clears the table, exercises the behaviour, and
 * restores the snapshot in tearDown.
 */
class AlertConfigServiceTest {

    private Map<String, String> savedRows;

    @BeforeEach
    void snapshotExistingConfig() throws SQLException {
        DatabaseInitializer.initialize();
        savedRows = readAllRows();
        truncateAppConfig();
    }

    @AfterEach
    void restoreExistingConfig() throws SQLException {
        truncateAppConfig();
        String now = LocalDateTime.now().toString();
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO app_config (key, value, updated_at) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> entry : savedRows.entrySet()) {
                stmt.setString(1, entry.getKey());
                stmt.setString(2, entry.getValue());
                stmt.setString(3, now);
                stmt.executeUpdate();
            }
        }
    }

    @Test
    void defaultsAreSeededOnEmptyTable() throws SQLException {
        AlertConfigService service = new AlertConfigService();
        AlertConfig snapshot = service.get();

        assertNotNull(snapshot);
        assertEquals(AlertConfig.defaults(), snapshot);

        // Seeding must persist so a second process sees the same values.
        Map<String, String> rowsAfterBoot = readAllRows();
        assertEquals(2, rowsAfterBoot.size(), "seed must write both keys");
        assertEquals("3", rowsAfterBoot.get("near_expiry_days"));
        assertEquals("30", rowsAfterBoot.get("retention_days"));
    }

    @Test
    void updatePersistsAndFreshServiceReadsItBack() {
        AlertConfigService writer = new AlertConfigService();
        AlertConfig updated = new AlertConfig(7, 45);
        writer.update(updated);
        assertEquals(updated, writer.get());

        // A fresh service simulates a container restart — the update must have
        // landed in the DB, not just the in-memory cache.
        AlertConfigService reader = new AlertConfigService();
        assertEquals(updated, reader.get());
    }

    @Test
    void malformedPersistedRowsFallBackToDefaults() throws SQLException {
        String now = LocalDateTime.now().toString();
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO app_config (key, value, updated_at) VALUES (?, ?, ?)")) {
            seedRow(stmt, "near_expiry_days", "not-a-number", now);
            seedRow(stmt, "retention_days", "-5", now);
        }

        AlertConfigService service = new AlertConfigService();

        // parseOr swallows the garbage nearExpiry, retentionDays=-5 then trips
        // AlertConfig validation, so the service must fall all the way back
        // to defaults.
        assertEquals(AlertConfig.defaults(), service.get());
    }

    @Test
    void alertConfigRejectsNegativeNearExpiry() {
        assertThrows(IllegalArgumentException.class, () -> new AlertConfig(-1, 30));
    }

    @Test
    void alertConfigRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new AlertConfig(AlertConfig.MAX_DAYS + 1, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new AlertConfig(3, AlertConfig.MAX_DAYS + 1));
    }

    @Test
    void alertConfigRejectsZeroRetention() {
        // Retention of 0 days would prune every row on every tick, which is
        // almost certainly not what an operator wants; the record rejects it.
        assertThrows(IllegalArgumentException.class, () -> new AlertConfig(3, 0));
    }

    private void seedRow(PreparedStatement stmt, String key, String value, String now) throws SQLException {
        stmt.setString(1, key);
        stmt.setString(2, value);
        stmt.setString(3, now);
        stmt.executeUpdate();
    }

    private Map<String, String> readAllRows() throws SQLException {
        Map<String, String> rows = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement("SELECT key, value FROM app_config");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rows.put(rs.getString("key"), rs.getString("value"));
            }
        }
        return rows;
    }

    private void truncateAppConfig() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM app_config");
        }
    }
}
