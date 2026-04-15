package app;

import app.model.NotificationMessage;
import app.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationServiceResilienceTest {
    @Test
    void noStrategiesConfiguredFailsFast() {
        NotificationService service = new NotificationService(2);

        assertThrows(IllegalStateException.class,
                () -> service.sendWithRetry(new NotificationMessage("tenant-a", "ing-1", "CHEF", "Sub", "Body")));
    }

    @Test
    void successfulStrategyStillDeliversWhenAnotherStrategyFails() {
        NotificationService service = new NotificationService(2);
        AtomicInteger successfulDeliveries = new AtomicInteger();

        service.registerStrategy(message -> {
            throw new RuntimeException("provider down");
        });
        service.registerStrategy(message -> successfulDeliveries.incrementAndGet());

        assertDoesNotThrow(() -> service.sendWithRetry(new NotificationMessage("tenant-a", "ing-1", "CHEF", "Sub", "Body")));
        assertThrows(RuntimeException.class, () -> {
            NotificationService allFail = new NotificationService(1);
            allFail.registerStrategy(message -> {
                throw new RuntimeException("all failed");
            });
            allFail.sendWithRetry(new NotificationMessage("tenant-a", "ing-1", "CHEF", "Sub", "Body"));
        });
        assertEquals(1, successfulDeliveries.get());
    }
}
