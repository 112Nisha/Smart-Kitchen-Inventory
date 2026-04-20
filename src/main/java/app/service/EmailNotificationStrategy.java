package app.service;

import app.model.NotificationMessage;
import app.repository.UserRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EmailNotificationStrategy implements NotificationStrategy {
    private final String smtpHost;
    private final int smtpPort;
    private final UserRepository userRepository;
    // Tracks emails sent today so repeated sweeps don't re-send the same alert.
    // Keyed on tenant|ingredient|role|subject|date — includes subject so a
    // near-expiry and a low-stock alert for the same ingredient both go out.
    private final Set<String> sentToday = Collections.synchronizedSet(new HashSet<>());
    private LocalDate sentDate = LocalDate.now();

    public EmailNotificationStrategy(String smtpHost, int smtpPort, UserRepository userRepository) {
        this.smtpHost = Objects.requireNonNull(smtpHost, "smtpHost is required");
        this.smtpPort = smtpPort;
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository is required");
    }

    @Override
    public void send(NotificationMessage message) {
        LocalDate today = LocalDate.now();
        if (!today.equals(sentDate)) {
            sentToday.clear();
            sentDate = today;
        }
        String dedupKey = message.getTenantId() + "|" + message.getIngredientId()
                + "|" + message.getRecipientRole() + "|" + message.getSubject() + "|" + today;
        if (!sentToday.add(dedupKey)) {
            return;
        }
        String tenantId = message.getTenantId();
        String to = userRepository.findUsernameByRestaurantAndRole(tenantId, message.getRecipientRole())
                .orElse(null);
        if (to == null) {
            return;
        }
        sendEmail("alert@" + tenantId, to, message.getSubject(), message.getBody());
    }

    private void sendEmail(String from, String to, String subject, String body) {
        System.out.printf("[EmailNotificationStrategy] SMTP %s:%d | From: %s | To: %s | Subject: %s%n",
                smtpHost, smtpPort, from, to, subject);
    }
}
