package app.service;

import app.model.NotificationMessage;
import app.repository.UserRepository;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public class EmailNotificationStrategy implements NotificationStrategy {
    private final String fromEmail;
    private final String appPassword;
    private final UserRepository userRepository;
    private final Set<String> sentToday = Collections.synchronizedSet(new HashSet<>());
    private LocalDate sentDate = LocalDate.now();

    public EmailNotificationStrategy(String fromEmail, String appPassword, UserRepository userRepository) {
        this.fromEmail = Objects.requireNonNull(fromEmail, "fromEmail is required");
        this.appPassword = Objects.requireNonNull(appPassword, "appPassword is required");
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
            System.out.printf("[EmailNotificationStrategy] No user found for tenant=%s role=%s — skipping%n",
                    tenantId, message.getRecipientRole());
            return;
        }
        sendEmail(to, message.getSubject(), message.getBody());
    }

    private void sendEmail(String to, String subject, String body) {
        System.out.printf("[EmailNotificationStrategy] Gmail | From: %s | To: %s | Subject: %s%n",
                fromEmail, to, subject);
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, appPassword);
            }
        });

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromEmail));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject(subject);
            msg.setText(body);
            Transport.send(msg);
            System.out.printf("[EmailNotificationStrategy] Sent to %s%n", to);
        } catch (MessagingException e) {
            System.err.printf("[EmailNotificationStrategy] Failed to send to %s: %s%n", to, e.getMessage());
        }
    }
}
