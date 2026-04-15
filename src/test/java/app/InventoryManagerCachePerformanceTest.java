package app;

import app.model.Ingredient;
import app.repository.IngredientRepository;
import app.service.InventoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryManagerCachePerformanceTest {
    private static final int WARMUP_ITERATIONS = 30;
    private static final int MEASURED_ITERATIONS = 200;

    @AfterEach
    void tearDown() {
        InventoryManager.resetInstanceForTests();
    }

    @Test
    void cacheHitPathHasLowerLatencyAndFewerRepositoryReads() {
        DelayedIngredientRepository repository = new DelayedIngredientRepository(Duration.ofMillis(4));
        String tenant = "tenant-cache-perf";
        seed(repository, tenant, 30);

        BenchmarkStats missStats = measureCacheMissLatency(repository, tenant);
        BenchmarkStats hitStats = measureCacheHitLatency(repository, tenant);

        double latencyImprovementPercent = percentImprovement(missStats.meanMillis(), hitStats.meanMillis());
        double readReductionPercent = percentImprovement(
                missStats.readsPerRequest(),
                hitStats.readsPerRequest()
        );

        System.out.printf(
                "\nCaching benchmark (InventoryManager, tenant=%s)%n"
                        + "Cache miss: mean=%.3f ms, median=%.3f ms, p95=%.3f ms, reads/request=%.3f%n"
                        + "Cache hit:  mean=%.3f ms, median=%.3f ms, p95=%.3f ms, reads/request=%.3f%n"
                        + "Latency improvement: %.2f%%%n"
                        + "Repository read reduction: %.2f%%%n",
                tenant,
                missStats.meanMillis(),
                missStats.medianMillis(),
                missStats.p95Millis(),
                missStats.readsPerRequest(),
                hitStats.meanMillis(),
                hitStats.medianMillis(),
                hitStats.p95Millis(),
                hitStats.readsPerRequest(),
                latencyImprovementPercent,
                readReductionPercent
        );

        assertTrue(hitStats.meanMillis() < missStats.meanMillis(),
                "Expected cache hit mean latency to be lower than cache miss latency");
        assertTrue(hitStats.readsPerRequest() < missStats.readsPerRequest(),
                "Expected cache hits to reduce repository reads per request");
    }

    private BenchmarkStats measureCacheMissLatency(DelayedIngredientRepository repository, String tenantId) {
        List<Long> measuredSamples = new ArrayList<>(MEASURED_ITERATIONS);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            InventoryManager.resetInstanceForTests();
            InventoryManager manager = InventoryManager.getInstance(repository, 3);
            manager.listIngredients(tenantId);
        }

        int callsBefore = repository.findByTenantCallCount();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            InventoryManager.resetInstanceForTests();
            InventoryManager manager = InventoryManager.getInstance(repository, 3);

            long startNanos = System.nanoTime();
            manager.listIngredients(tenantId);
            measuredSamples.add(System.nanoTime() - startNanos);
        }
        int callsAfter = repository.findByTenantCallCount();

        return summarize(measuredSamples, callsAfter - callsBefore);
    }

    private BenchmarkStats measureCacheHitLatency(DelayedIngredientRepository repository, String tenantId) {
        List<Long> measuredSamples = new ArrayList<>(MEASURED_ITERATIONS);

        InventoryManager.resetInstanceForTests();
        InventoryManager manager = InventoryManager.getInstance(repository, 3);
        manager.listIngredients(tenantId);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            manager.listIngredients(tenantId);
        }

        int callsBefore = repository.findByTenantCallCount();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long startNanos = System.nanoTime();
            manager.listIngredients(tenantId);
            measuredSamples.add(System.nanoTime() - startNanos);
        }
        int callsAfter = repository.findByTenantCallCount();

        return summarize(measuredSamples, callsAfter - callsBefore);
    }

    private BenchmarkStats summarize(List<Long> measuredSamples, int repositoryCalls) {
        List<Long> sorted = new ArrayList<>(measuredSamples);
        sorted.sort(Long::compareTo);

        double meanNanos = measuredSamples.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        int middle = sorted.size() / 2;
        double medianNanos = sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0
                : sorted.get(middle);

        int p95Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        double p95Nanos = sorted.get(p95Index);

        return new BenchmarkStats(
                nanosToMillis(meanNanos),
                nanosToMillis(medianNanos),
                nanosToMillis(p95Nanos),
                repositoryCalls / (double) measuredSamples.size()
        );
    }

    private double percentImprovement(double baseline, double improved) {
        if (baseline <= 0) {
            return 0;
        }
        return ((baseline - improved) / baseline) * 100.0;
    }

    private double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private void seed(IngredientRepository repository, String tenantId, int ingredientCount) {
        for (int i = 0; i < ingredientCount; i++) {
            repository.save(new Ingredient(
                    tenantId,
                    "Ingredient-" + i,
                    2.0 + i,
                    "kg",
                    LocalDate.now().plusDays(5 + (i % 3)),
                    0.5
            ));
        }
    }

    private record BenchmarkStats(
            double meanMillis,
            double medianMillis,
            double p95Millis,
            double readsPerRequest
    ) {
    }

    private static final class DelayedIngredientRepository extends IngredientRepository {
        private final Duration readDelay;
        private final AtomicInteger findByTenantCalls = new AtomicInteger();

        private DelayedIngredientRepository(Duration readDelay) {
            this.readDelay = readDelay;
        }

        @Override
        public List<Ingredient> findByTenant(String tenantId) {
            findByTenantCalls.incrementAndGet();
            LockSupport.parkNanos(readDelay.toNanos());
            return super.findByTenant(tenantId);
        }

        private int findByTenantCallCount() {
            return findByTenantCalls.get();
        }
    }
}