package app.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryServletDateValidationTest {

    @Test
    void parseExpiryDateRejectsPastDate() {
        LocalDate today = LocalDate.of(2026, 4, 19);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> InventoryServlet.parseExpiryDate("2026-04-18", today)
        );

        assertEquals("Expiry date cannot be before today", ex.getMessage());
    }

    @Test
    void parseExpiryDateRejectsYearLongerThanFourDigits() {
        LocalDate today = LocalDate.of(2026, 4, 19);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> InventoryServlet.parseExpiryDate("123456-01-01", today)
        );

        assertEquals("Expiry date must use yyyy-MM-dd with a 4-digit year", ex.getMessage());
    }

    @Test
    void parseExpiryDateAcceptsTodayAndFutureDate() {
        LocalDate today = LocalDate.of(2026, 4, 19);

        assertEquals(LocalDate.of(2026, 4, 19), InventoryServlet.parseExpiryDate("2026-04-19", today));
        assertEquals(LocalDate.of(2026, 4, 20), InventoryServlet.parseExpiryDate("2026-04-20", today));
    }
}
