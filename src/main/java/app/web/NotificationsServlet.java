package app.web;

import app.config.AlertConfig;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Notifications page: durable log view over the store (newest first), plus an
 * inline form to tune the alert thresholds. Config and log live together here
 * because the config only governs notification behavior — keeping them on one
 * page cuts a nav link and makes the settings discoverable right where they
 * take effect.
 *
 * Saving the config also triggers an immediate sweep so items that just
 * crossed the new near-expiry boundary produce notifications without waiting
 * for the next scheduled tick.
 */
public class NotificationsServlet extends BaseServlet {
    // Formatted here (not in the JSP) because LocalDateTime.toString() emits
    // 9-digit nanoseconds which JSTL's fmt:parseDate can't handle.
    private static final DateTimeFormatter WHEN_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // JavaBean-style getters (not just record accessors) because Tomcat 7's EL
    // resolver reads properties via getX() naming, not via record component
    // accessors like when().
    public static final class NotificationView {
        private final String when;
        private final String subject;
        private final String body;

        public NotificationView(String when, String subject, String body) {
            this.when = when;
            this.subject = subject;
            this.body = body;
        }

        public String getWhen() { return when; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = loggedInTenant(req);
        if (tenantId == null) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
            return;
        }

        List<NotificationView> notifications = services().notificationStore().allForTenant(tenantId).stream()
                .map(m -> new NotificationView(
                        WHEN_FORMAT.format(m.getCreatedAt()),
                        m.getSubject(),
                        m.getBody()))
                .toList();

        AlertConfig current = services().alertConfigService().get();
        req.setAttribute("tenant", tenantId);
        req.setAttribute("notifications", notifications);
        req.setAttribute("nearExpiryDays", current.nearExpiryDays());
        req.setAttribute("retentionDays", current.retentionDays());
        req.setAttribute("maxDays", AlertConfig.MAX_DAYS);
        req.setAttribute("successMessage", req.getParameter("success"));
        req.setAttribute("errorMessage", req.getParameter("error"));

        req.getRequestDispatcher("/WEB-INF/views/notifications.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenantId = loggedInTenant(req);
        if (tenantId == null) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
            return;
        }

        try {
            int nearExpiry = parseIntField(req.getParameter("nearExpiryDays"), "Near-expiry days");
            int retention = parseIntField(req.getParameter("retentionDays"), "Retention days");

            // AlertConfig's canonical constructor enforces the valid range;
            // any violation comes back as IllegalArgumentException which we
            // surface to the user verbatim.
            AlertConfig updated = new AlertConfig(nearExpiry, retention);
            services().alertConfigService().update(updated);

            // Run a sweep right away so items that just crossed the new
            // near-expiry boundary produce notifications immediately instead
            // of waiting for the next scheduled tick. Failures here are
            // logged but don't roll back the save — the config is already
            // durable, the sweep will retry on the next tick.
            try {
                services().expiryAlertService().evaluateAllTenants();
            } catch (RuntimeException ex) {
                System.err.println("[NotificationsServlet] post-save sweep failed: " + ex.getMessage());
            }

            resp.sendRedirect(req.getContextPath()
                    + "/notifications?success=" + encodeQueryParam("Settings updated."));
        } catch (IllegalArgumentException ex) {
            resp.sendRedirect(req.getContextPath()
                    + "/notifications?error=" + encodeQueryParam(ex.getMessage()));
        }
    }

    private int parseIntField(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number");
        }
    }
}
