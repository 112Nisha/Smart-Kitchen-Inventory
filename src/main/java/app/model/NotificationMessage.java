package app.model;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import java.time.LocalDateTime;
import java.util.Objects;

public class NotificationMessage {
    private final String tenantId;
    private final String recipientRole;
    private final String subject;
    private final String body;
    private final LocalDateTime createdAt;

    public NotificationMessage(String tenantId, String recipientRole, String subject, String body) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.recipientRole = Objects.requireNonNull(recipientRole, "recipientRole is required");
        this.subject = Objects.requireNonNull(subject, "subject is required");
        this.body = Objects.requireNonNull(body, "body is required");
        this.createdAt = LocalDateTime.now();
    }

    public String getTenantId() {
        return tenantId;
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
