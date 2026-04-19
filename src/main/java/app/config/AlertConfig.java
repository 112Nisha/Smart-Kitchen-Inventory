package app.config;

/**
 * Immutable snapshot of the operator-tunable thresholds that drive the
 * expiry-alert pipeline. Held behind an AtomicReference in AlertConfigService
 * so every read is a lock-free atomic load and every update swaps atomically.
 *
 * Validation lives in the canonical constructor so an invalid AlertConfig
 * cannot exist — callers reading via {@code get()} never have to null-check
 * or range-check individual fields.
 */
public record AlertConfig(int nearExpiryDays, int retentionDays) {

    // Sensible upper bounds so a fat-fingered admin can't set a 10000-day
    // window that would, for example, mark every ingredient as NEAR_EXPIRY
    // from the moment it's added.
    public static final int MAX_DAYS = 365;

    public AlertConfig {
        if (nearExpiryDays < 0 || nearExpiryDays > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "nearExpiryDays must be between 0 and " + MAX_DAYS + ", got " + nearExpiryDays);
        }
        if (retentionDays <= 0 || retentionDays > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "retentionDays must be between 1 and " + MAX_DAYS + ", got " + retentionDays);
        }
    }

    public static AlertConfig defaults() {
        return new AlertConfig(3, 30);
    }
}
