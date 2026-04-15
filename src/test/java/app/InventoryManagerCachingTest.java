package app;

import app.model.Ingredient;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryManagerCachingTest {

    @AfterEach
    void tearDown() {
        InventoryManager.resetInstanceForTests();
    }

    @Test
    void repeatedListIngredientsForSameTenantUsesCache() {
        CountingIngredientRepository repository = new CountingIngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        String tenant = "tenant-cache-hit";

        manager.addIngredient(new Ingredient(tenant, "Tomato", 2.0, "kg", LocalDate.now().plusDays(4), 0.5));

        manager.listIngredients(tenant);
        manager.listIngredients(tenant);
        manager.listIngredients(tenant);

        assertEquals(1, repository.findByTenantCalls(tenant));
    }

    @Test
    void addIngredientInvalidatesTenantCache() {
        CountingIngredientRepository repository = new CountingIngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        String tenant = "tenant-cache-add";

        manager.addIngredient(new Ingredient(tenant, "Onion", 2.0, "kg", LocalDate.now().plusDays(4), 0.5));
        manager.listIngredients(tenant);
        manager.listIngredients(tenant);
        assertEquals(1, repository.findByTenantCalls(tenant));

        manager.addIngredient(new Ingredient(tenant, "Garlic", 1.0, "kg", LocalDate.now().plusDays(5), 0.3));
        manager.listIngredients(tenant);

        assertEquals(2, repository.findByTenantCalls(tenant));
    }

    @Test
    void updateIngredientInvalidatesTenantCache() {
        CountingIngredientRepository repository = new CountingIngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        String tenant = "tenant-cache-update";

        Ingredient added = manager.addIngredient(
                new Ingredient(tenant, "Milk", 5.0, "liters", LocalDate.now().plusDays(4), 1.0)
        );

        manager.listIngredients(tenant);
        manager.listIngredients(tenant);
        assertEquals(1, repository.findByTenantCalls(tenant));

        manager.updateIngredient(
                tenant,
                added.getId(),
                "Milk",
                4.2,
                "liters",
                LocalDate.now().plusDays(3),
                1.0
        );
        manager.listIngredients(tenant);

        assertEquals(2, repository.findByTenantCalls(tenant));
    }

    @Test
    void useIngredientInvalidatesTenantCache() {
        CountingIngredientRepository repository = new CountingIngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        String tenant = "tenant-cache-use";

        Ingredient added = manager.addIngredient(
                new Ingredient(tenant, "Pasta", 5.0, "kg", LocalDate.now().plusDays(4), 1.0)
        );

        manager.listIngredients(tenant);
        manager.listIngredients(tenant);
        assertEquals(1, repository.findByTenantCalls(tenant));

        manager.useIngredient(tenant, added.getId(), 1.0);
        manager.listIngredients(tenant);

        assertEquals(2, repository.findByTenantCalls(tenant));
    }

    @Test
    void discardIngredientInvalidatesTenantCache() {
        CountingIngredientRepository repository = new CountingIngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        String tenant = "tenant-cache-discard";

        Ingredient added = manager.addIngredient(
                new Ingredient(tenant, "Basil", 1.0, "kg", LocalDate.now().plusDays(2), 0.3)
        );

        manager.listIngredients(tenant);
        manager.listIngredients(tenant);
        assertEquals(1, repository.findByTenantCalls(tenant));

        manager.discardIngredient(tenant, added.getId());
        manager.listIngredients(tenant);

        assertEquals(2, repository.findByTenantCalls(tenant));
    }

    @Test
    void cacheIsIsolatedPerTenant() {
        CountingIngredientRepository repository = new CountingIngredientRepository();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        String tenantA = "tenant-cache-a";
        String tenantB = "tenant-cache-b";

        manager.addIngredient(new Ingredient(tenantA, "Tomato", 2.0, "kg", LocalDate.now().plusDays(4), 0.5));
        manager.addIngredient(new Ingredient(tenantB, "Onion", 2.0, "kg", LocalDate.now().plusDays(4), 0.5));

        manager.listIngredients(tenantA);
        manager.listIngredients(tenantB);
        manager.listIngredients(tenantA);
        manager.listIngredients(tenantB);

        assertEquals(1, repository.findByTenantCalls(tenantA));
        assertEquals(1, repository.findByTenantCalls(tenantB));

        manager.addIngredient(new Ingredient(tenantA, "Garlic", 1.0, "kg", LocalDate.now().plusDays(5), 0.3));
        manager.listIngredients(tenantB);
        manager.listIngredients(tenantA);

        assertEquals(2, repository.findByTenantCalls(tenantA));
        assertEquals(1, repository.findByTenantCalls(tenantB));
    }

    private static final class CountingIngredientRepository extends IngredientRepository {
        private final Map<String, AtomicInteger> callsByTenant = new ConcurrentHashMap<>();

        @Override
        public List<Ingredient> findByTenant(String tenantId) {
            callsByTenant.computeIfAbsent(tenantId, key -> new AtomicInteger()).incrementAndGet();
            return super.findByTenant(tenantId);
        }

        int findByTenantCalls(String tenantId) {
            AtomicInteger count = callsByTenant.get(tenantId);
            return count == null ? 0 : count.get();
        }
    }
}