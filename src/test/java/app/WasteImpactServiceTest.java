package app;

import app.service.WasteImpactService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WasteImpactServiceTest {
    @Test
    void rejectsNegativeOrNonFiniteSavedFood() {
        WasteImpactService service = new WasteImpactService();

        assertThrows(IllegalArgumentException.class, () -> service.estimateImpact(-1));
        assertThrows(IllegalArgumentException.class, () -> service.estimateImpact(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> service.estimateImpact(Double.POSITIVE_INFINITY));
    }

    @Test
    void computesExpectedImpactValues() {
        WasteImpactService service = new WasteImpactService();

        WasteImpactService.Impact impact = service.estimateImpact(2.0);
        assertEquals(2.0, impact.savedFoodKg());
        assertEquals(0.1, impact.methaneAvoidedKg());
        assertEquals(2.8, impact.co2EquivalentKg(), 1e-9);
    }
}
