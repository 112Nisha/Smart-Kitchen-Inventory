package app.notification;

import app.config.DatabaseInitializer;
import app.model.NotificationMessage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable notification store backed by SQLite. Replaces the in-memory list
 * used in earlier builds so alerts survive container restarts — Fix 7 of the
 * expiry-alerts subsystem.
 *
 * Dedup strategy is the same as {@link InMemoryNotificationStore}: a composite
 * key built from (tenantId, ingredientId-or-subject, recipientRole, date).
 * Here the key is persisted alongside the row and the column is UNIQUE, so
 * dedup is enforced by SQLite itself — INSERT OR IGNORE turns a duplicate
 * save() into a cheap no-op even across restarts.
 */
public class SqliteNotificationStore implements NotificationStore {
    // INSERT OR IGNORE — relies on the UNIQUE constraint on dedup_key to
    // silently drop repeats. Cheaper and safer than a SELECT-then-INSERT:
    // the DB handles concurrent duplicate saves atomically.
    private static final String INSERT_SQL = """
            INSERT OR IGNORE INTO notifications (
                tenant_id,
                ingredient_id,
                recipient_role,
                subject,
                body,
                created_at,
                dedup_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    // ORDER BY id DESC: newest-first, matching the dashboard's natural reading
    // order. Pushing the sort into SQL removes the redundant Comparator step
    // in the servlet and lets the index on id do the work.
    private static final String SELECT_ALL_SQL = """
            SELECT tenant_id, ingredient_id, recipient_role, subject, body, created_at
            FROM notifications
            ORDER BY id DESC
            """;

    private static final String SELECT_BY_TENANT_SQL = """
            SELECT tenant_id, ingredient_id, recipient_role, subject, body, created_at
            FROM notifications
            WHERE tenant_id = ?
            ORDER BY id DESC
            """;

    // Compare on the ISO date prefix of created_at — matches the format written
    // by LocalDateTime.toString() at save time. Avoids pulling SQLite's
    // date()/datetime() functions which have their own quirks around
    // fractional-second strings.
    private static final String DELETE_OLDER_THAN_SQL = """
            DELETE FROM notifications
            WHERE substr(created_at, 1, 10) < ?
            """;

    private static final DateTimeFormatter CUTOFF_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public SqliteNotificationStore() {
        // Idempotent — safe to call even after the servlet context has already
        // initialised the DB. Guarantees the notifications table exists before
        // the first save().
        DatabaseInitializer.initialize();
    }

    @Override
    public void save(NotificationMessage message) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {

            stmt.setString(1, message.getTenantId());
            stmt.setString(2, message.getIngredientId());
            stmt.setString(3, message.getRecipientRole());
            stmt.setString(4, message.getSubject());
            stmt.setString(5, message.getBody());
            stmt.setString(6, message.getCreatedAt().toString());
            stmt.setString(7, buildDedupKey(message));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save notification", e);
        }
    }

    @Override
    public List<NotificationMessage> all() {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {
            return readAll(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load notifications", e);
        }
    }

    @Override
    public List<NotificationMessage> allForTenant(String tenantId) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
            stmt.setString(1, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load notifications for tenant " + tenantId, e);
        }
    }

    @Override
    public int pruneOlderThan(LocalDate cutoff) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(DELETE_OLDER_THAN_SQL)) {
            stmt.setString(1, CUTOFF_FORMAT.format(cutoff));
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prune notifications older than " + cutoff, e);
        }
    }

    private List<NotificationMessage> readAll(ResultSet rs) throws SQLException {
        List<NotificationMessage> messages = new ArrayList<>();
        while (rs.next()) {
            // rehydrate() — not the public constructor — so the original
            // createdAt is preserved. Using the public constructor here
            // would stamp every load with now() and break same-day dedup.
            messages.add(NotificationMessage.rehydrate(
                    rs.getString("tenant_id"),
                    rs.getString("ingredient_id"),
                    rs.getString("recipient_role"),
                    rs.getString("subject"),
                    rs.getString("body"),
                    LocalDateTime.parse(rs.getString("created_at"))
            ));
        }
        return List.copyOf(messages);
    }

    // Shared key-building logic with InMemoryNotificationStore. Kept separate
    // (rather than extracted to a helper) because the two stores are otherwise
    // independent and a future DB migration might want to version the key
    // format without touching in-memory behaviour.
    private String buildDedupKey(NotificationMessage message) {
        LocalDate day = message.getCreatedAt().toLocalDate();
        return message.getTenantId() + "|" + message.getIngredientId() + "|" + message.getRecipientRole() + "|" + day;
    }
}
