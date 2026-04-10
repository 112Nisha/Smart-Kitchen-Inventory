package app;

import app.config.DatabaseInitializer;
import app.model.Ingredient;
import app.repository.SqliteIngredientRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientSqliteRepositoryIntegrationTest {
    @Test
    void persistsIngredientStockAndExpiryAcrossRepositoryInstances() {
        String tenant = "it-tenant-" + UUID.randomUUID();
        createRestaurant(tenant);

        try {
            SqliteIngredientRepository firstRepository = new SqliteIngredientRepository();
            Ingredient saved = firstRepository.save(
                    new Ingredient(tenant, "Mushroom", 10.0, "kg", LocalDate.now().plusDays(6), 2.0)
            );

            SqliteIngredientRepository secondRepository = new SqliteIngredientRepository();
            Ingredient loaded = secondRepository.findById(tenant, saved.getId()).orElseThrow();

            assertEquals("Mushroom", loaded.getName());
            assertEquals(10.0, loaded.getQuantity(), 1e-9);

            loaded.setQuantity(6.5);
            loaded.setExpiryDate(LocalDate.now().plusDays(2));
            loaded.setDiscarded(true);
            secondRepository.save(loaded);

            Ingredient reloaded = firstRepository.findById(tenant, saved.getId()).orElseThrow();
            assertEquals(6.5, reloaded.getQuantity(), 1e-9);
            assertEquals(LocalDate.now().plusDays(2), reloaded.getExpiryDate());
            assertTrue(reloaded.isDiscarded());
        } finally {
            cleanupTenant(tenant);
        }
    }

    @Test
    void saveFailsForUnknownTenantBecauseInventoryLinksToRestaurants() {
        SqliteIngredientRepository repository = new SqliteIngredientRepository();
        String missingTenant = "missing-tenant-" + UUID.randomUUID();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> repository.save(
                        new Ingredient(missingTenant, "Paprika", 1.0, "kg", LocalDate.now().plusDays(1), 0.2)
                ));

        assertTrue(ex.getMessage().contains("Failed to save ingredient"));
    }

    private void createRestaurant(String tenant) {
        DatabaseInitializer.initialize();

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO restaurants (name) VALUES (?)")) {

            enableForeignKeys(conn);
            stmt.setString(1, tenant);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare restaurant test data", e);
        }
    }

    private void cleanupTenant(String tenant) {
        DatabaseInitializer.initialize();

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement deleteInventory = conn.prepareStatement(
                     "DELETE FROM inventory_ingredients WHERE tenant_id = ?");
             PreparedStatement deleteRestaurant = conn.prepareStatement(
                     "DELETE FROM restaurants WHERE name = ?")) {

            enableForeignKeys(conn);

            deleteInventory.setString(1, tenant);
            deleteInventory.executeUpdate();

            deleteRestaurant.setString(1, tenant);
            deleteRestaurant.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clean test data", e);
        }
    }

    private void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }
}
