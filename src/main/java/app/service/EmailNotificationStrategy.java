package app.service;

import app.model.NotificationMessage;

import java.util.Objects;

public class EmailNotificationStrategy implements NotificationStrategy {
    private final String smtpHost;
    private final int smtpPort;
    private final String fromAddress;

    public EmailNotificationStrategy(String smtpHost, int smtpPort, String fromAddress) {
        this.smtpHost = Objects.requireNonNull(smtpHost, "smtpHost is required");
        this.smtpPort = smtpPort;
        this.fromAddress = Objects.requireNonNull(fromAddress, "fromAddress is required");
    }

    @Override
    public void send(NotificationMessage message) {
        // Resolve recipient email from role. Extend this lookup
        // (e.g. a DB-backed role→email map) as tenants are onboarded.
        String toAddress = resolveRecipientEmail(message.getTenantId(), message.getRecipientRole());
        sendEmail(toAddress, message.getSubject(), message.getBody());
    }

    private void sendEmail(String to, String subject, String body) {
        // JavaMail (jakarta.mail) integration point. Keeping this as a stub
        // until SMTP credentials are configured in the deployment environment.
        System.out.printf("[EmailNotificationStrategy] SMTP %s:%d | From: %s | To: %s | Subject: %s%n",
                smtpHost, smtpPort, fromAddress, to, subject);
    }

    private String resolveRecipientEmail(String tenantId, String role) {
        // Default fallback — replace with a proper tenant/role → email lookup.
        return tenantId + "-" + role.toLowerCase() + "@kitchen.local";
    }
}
