package app.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live-reloading store for the tunable alert thresholds. Persists to the
 * app_config SQLite table and caches the active snapshot in an
 * AtomicReference so callers can resolve the current value from a hot path
 * (scheduler tick, refreshState call) without any locking or IO.
 *
 * Contract:
 *   - get() returns the currently-cached snapshot. Never null; lazily
 *     initialized on first call to the ctor. Thread-safe via AtomicReference.
 *   - update(newConfig) writes the new values (upsert) and swaps the cache.
 *     On DB failure, the cache is NOT swapped — callers keep observing the
 *     previous snapshot.
 */
public class AlertConfigService {
    static final String KEY_NEAR_EXPIRY = "near_expiry_days";
    static final String KEY_RETENTION = "retention_days";

    private static final String UPSERT_SQL =
            "INSERT INTO app_config (key, value, updated_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at";

    private static final String SELECT_ALL_SQL = "SELECT key, value FROM app_config";

    private final AtomicReference<AlertConfig> cache = new AtomicReference<>();

    public AlertConfigService() {
        DatabaseInitializer.initialize();
        cache.set(loadOrSeedDefaults());
    }

    public AlertConfig get() {
        AlertConfig snapshot = cache.get();
        return snapshot != null ? snapshot : AlertConfig.defaults();
    }

    public synchronized void update(AlertConfig newConfig) {
        Objects.requireNonNull(newConfig, "newConfig is required");
        // Persist first — if the DB write fails the in-memory cache does not
        // drift ahead of durable storage. Single transaction so a partial
        // failure can't leave two fields agreeing and one stale.
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl())) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                String now = LocalDateTime.now().toString();
                upsert(stmt, KEY_NEAR_EXPIRY, newConfig.nearExpiryDays(), now);
                upsert(stmt, KEY_RETENTION, newConfig.retentionDays(), now);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to persist alert config update", e);
        }
        cache.set(newConfig);
    }

    private void upsert(PreparedStatement stmt, String key, int value, String now) throws SQLException {
        stmt.setString(1, key);
        stmt.setString(2, Integer.toString(value));
        stmt.setString(3, now);
        stmt.executeUpdate();
    }

    private AlertConfig loadOrSeedDefaults() {
        Map<String, String> rows = readAll();
        AlertConfig defaults = AlertConfig.defaults();

        // First boot on an empty table: persist the defaults so the admin
        // page always has concrete rows to render. Done once; on subsequent
        // boots the rows are read back as-is.
        if (rows.isEmpty()) {
            update(defaults);
            return defaults;
        }

        int nearExpiry = parseOr(rows.get(KEY_NEAR_EXPIRY), defaults.nearExpiryDays());
        int retention = parseOr(rows.get(KEY_RETENTION), defaults.retentionDays());
        try {
            return new AlertConfig(nearExpiry, retention);
        } catch (IllegalArgumentException ex) {
            // A manual DB edit could have landed an out-of-range value. Fall
            // back to defaults rather than refusing to boot — an incorrect
            // row shouldn't take the whole app down.
            System.err.println("[AlertConfigService] invalid persisted config, falling back to defaults: "
                    + ex.getMessage());
            return defaults;
        }
    }

    private Map<String, String> readAll() {
        Map<String, String> rows = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rows.put(rs.getString("key"), rs.getString("value"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load alert config", e);
        }
        return rows;
    }

    private int parseOr(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
