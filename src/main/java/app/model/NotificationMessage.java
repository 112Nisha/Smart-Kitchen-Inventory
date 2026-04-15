package app.model;

import app.model.ExpiryAlertContext;
import app.service.ExpiryAlertScheduler;
import app.service.IngredientStateTracker;
import app.service.StakeholderNotificationHandler;
import app.model.*;
import app.repository.InMemoryNotificationStore;
import app.repository.NotificationStore;
import app.repository.SqliteNotificationStore;
import app.service.DashboardNotificationStrategy;
import app.service.NotificationService;
import app.service.NotificationStrategy;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import java.time.LocalDateTime;
import java.util.Objects;

public class NotificationMessage {
    private final String tenantId;
    private final String ingredientId;
    private final String recipientRole;
    private final String subject;
    private final String body;
    private final LocalDateTime createdAt;

    public NotificationMessage(String tenantId, String ingredientId, String recipientRole, String subject, String body) {
        this(tenantId, ingredientId, recipientRole, subject, body, LocalDateTime.now());
    }

    // All-args constructor kept private so only the rehydrate factory (for
    // SQLite loads) can supply a non-"now" timestamp. Prevents callers from
    // accidentally constructing a message with a backdated createdAt, which
    // would confuse the day-based dedup key.
    private NotificationMessage(String tenantId, String ingredientId, String recipientRole,
                                String subject, String body, LocalDateTime createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId is required");
        this.recipientRole = Objects.requireNonNull(recipientRole, "recipientRole is required");
        this.subject = Objects.requireNonNull(subject, "subject is required");
        this.body = Objects.requireNonNull(body, "body is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    /**
     * Restores a persisted message without bumping the createdAt clock.
     * Only the SQLite store should call this — production callers go through
     * the public constructors so the "same-day dedup" semantics stay honest.
     */
    public static NotificationMessage rehydrate(String tenantId, String ingredientId, String recipientRole,
                                                String subject, String body, LocalDateTime createdAt) {
        return new NotificationMessage(tenantId, ingredientId, recipientRole, subject, body, createdAt);
    }

    public String getTenantId() {
        return tenantId;
    }

    // Exposed so the store can build its dedup key without reflecting on the subject string.
    public String getIngredientId() {
        return ingredientId;
    }

    public String getRecipientRole() {
        return recipientRole;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
