package app.repository;

import app.config.DatabaseInitializer;
import app.model.Ingredient;
import app.model.IngredientLifecycle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SqliteIngredientRepository extends IngredientRepository {
    private static final String UPSERT_SQL = """
            INSERT INTO inventory_ingredients (
                id,
                tenant_id,
                name,
                quantity,
                unit,
                expiry_date,
                low_stock_threshold,
                discarded
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                tenant_id = excluded.tenant_id,
                name = excluded.name,
                quantity = excluded.quantity,
                unit = excluded.unit,
                expiry_date = excluded.expiry_date,
                low_stock_threshold = excluded.low_stock_threshold,
                discarded = excluded.discarded
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT id, tenant_id, name, quantity, unit, expiry_date, low_stock_threshold, discarded
            FROM inventory_ingredients
            WHERE tenant_id = ? AND id = ?
            """;

    private static final String FIND_BY_TENANT_SQL = """
            SELECT id, tenant_id, name, quantity, unit, expiry_date, low_stock_threshold, discarded
            FROM inventory_ingredients
            WHERE tenant_id = ?
            ORDER BY name COLLATE NOCASE, expiry_date
            """;

    // Used by the scheduled alert runner to enumerate tenants without needing
    // to know the full ingredient set. DISTINCT keeps the result small even
    // when a tenant has hundreds of rows.
    private static final String FIND_ALL_TENANTS_SQL = """
            SELECT DISTINCT tenant_id FROM inventory_ingredients
            """;

    public SqliteIngredientRepository() {
        DatabaseInitializer.initialize();
    }

    @Override
    public Ingredient save(Ingredient ingredient) {
        Ingredient copy = ingredient.copy();

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {

            enableForeignKeys(conn);

            stmt.setString(1, copy.getId());
            stmt.setString(2, copy.getTenantId());
            stmt.setString(3, copy.getName());
            stmt.setDouble(4, copy.getQuantity());
            stmt.setString(5, copy.getUnit());
            stmt.setString(6, copy.getExpiryDate().toString());
            stmt.setDouble(7, copy.getLowStockThreshold());
            stmt.setInt(8, copy.isDiscarded() ? 1 : 0);
            stmt.executeUpdate();

            return copy.copy();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save ingredient", e);
        }
    }

    @Override
    public Optional<Ingredient> findById(String tenantId, String ingredientId) {
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {

            enableForeignKeys(conn);
            stmt.setString(1, tenantId);
            stmt.setString(2, ingredientId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs).copy());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find ingredient by id", e);
        }
    }

    @Override
    public List<Ingredient> findByTenant(String tenantId) {
        List<Ingredient> ingredients = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_TENANT_SQL)) {

            enableForeignKeys(conn);
            stmt.setString(1, tenantId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ingredients.add(mapRow(rs).copy());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find ingredients by tenant", e);
        }

        return ingredients;
    }

    @Override
    public Set<String> findAllTenantIds() {
        // Query the DB directly rather than delegating to the in-memory parent
        // implementation — the parent's map is never populated in the SQLite
        // variant, so relying on it would always return an empty set.
        Set<String> tenantIds = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(FIND_ALL_TENANTS_SQL);
             ResultSet rs = stmt.executeQuery()) {
            enableForeignKeys(conn);
            while (rs.next()) {
                tenantIds.add(rs.getString("tenant_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to enumerate tenant ids", e);
        }
        return Set.copyOf(tenantIds);
    }

    private Ingredient mapRow(ResultSet rs) throws SQLException {
        boolean discarded = rs.getInt("discarded") == 1;
        LocalDate expiryDate = LocalDate.parse(rs.getString("expiry_date"));

        IngredientLifecycle lifecycle;
        if (discarded) {
            lifecycle = IngredientLifecycle.DISCARDED;
        } else if (expiryDate.isBefore(LocalDate.now())) {
            lifecycle = IngredientLifecycle.EXPIRED;
        } else {
            lifecycle = IngredientLifecycle.FRESH;
        }

        return Ingredient.rehydrate(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("name"),
                rs.getDouble("quantity"),
                rs.getString("unit"),
                expiryDate,
                rs.getDouble("low_stock_threshold"),
                discarded,
                lifecycle
        );
    }

    private void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }
}
