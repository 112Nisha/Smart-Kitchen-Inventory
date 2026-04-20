package app.service;

import app.model.NotificationMessage;
import app.repository.UserRepository;

import java.util.Objects;

public class EmailNotificationStrategy implements NotificationStrategy {
    private final String smtpHost;
    private final int smtpPort;
    private final UserRepository userRepository;

    public EmailNotificationStrategy(String smtpHost, int smtpPort, UserRepository userRepository) {
        this.smtpHost = Objects.requireNonNull(smtpHost, "smtpHost is required");
        this.smtpPort = smtpPort;
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository is required");
    }

    @Override
    public void send(NotificationMessage message) {
        String tenantId = message.getTenantId();
        String from = "alert@" + tenantId;
        String to = userRepository.findUsernameByRestaurantAndRole(tenantId, message.getRecipientRole())
                .orElse(tenantId + "-" + message.getRecipientRole().toLowerCase());
        sendEmail(from, to, message.getSubject(), message.getBody());
    }

    private void sendEmail(String from, String to, String subject, String body) {
        System.out.printf("[EmailNotificationStrategy] SMTP %s:%d | From: %s | To: %s | Subject: %s%n",
                smtpHost, smtpPort, from, to, subject);
    }
}
