package app.repository;

import app.config.DatabaseInitializer;
import app.model.ShoppingItemStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of ShoppingListRepository.
 * Stores shopping list item metadata (purchase/ignore status) in the shopping_list_items table.
 *
 * Pattern: Repository impl (mirrors SqliteIngredientRepository patterns)
 * - try-with-resources per method
 * - PRAGMA foreign_keys = ON
 * - INSERT OR REPLACE for upsert
 */
public class SqliteShoppingListRepository implements ShoppingListRepository {

    private static final String SAVE_STATUS_SQL =
        "INSERT OR REPLACE INTO shopping_list_items (id, tenant_id, ingredient_id, status, updated_at) " +
        "VALUES (?, ?, ?, ?, ?)";

    private static final String FIND_STATUS_SQL =
        "SELECT status FROM shopping_list_items WHERE tenant_id = ? AND ingredient_id = ?";

    private static final String DELETE_STATUS_SQL =
        "DELETE FROM shopping_list_items WHERE tenant_id = ? AND ingredient_id = ?";

    public SqliteShoppingListRepository() {
        DatabaseInitializer.initialize();
    }

    @Override
    public void saveStatus(String tenantId, String ingredientId, ShoppingItemStatus status) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(SAVE_STATUS_SQL)) {

            enableForeignKeys(conn);

            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, tenantId);
            stmt.setString(3, ingredientId);
            stmt.setString(4, status.name());
            stmt.setString(5, LocalDateTime.now().toString());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save shopping list item status", e);
        }
    }

    @Override
    public Optional<ShoppingItemStatus> findStatus(String tenantId, String ingredientId) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(FIND_STATUS_SQL)) {

            enableForeignKeys(conn);

            stmt.setString(1, tenantId);
            stmt.setString(2, ingredientId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String statusStr = rs.getString("status");
                    return Optional.of(ShoppingItemStatus.valueOf(statusStr));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find shopping list item status", e);
        }

        return Optional.empty();
    }

    @Override
    public void deleteStatus(String tenantId, String ingredientId) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(DELETE_STATUS_SQL)) {

            enableForeignKeys(conn);

            stmt.setString(1, tenantId);
            stmt.setString(2, ingredientId);

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete shopping list item status", e);
        }
    }

    private void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }
}
