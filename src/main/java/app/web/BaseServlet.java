package app.web;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public abstract class BaseServlet extends HttpServlet {
    private static final String DEFAULT_TENANT = "restaurant-a";

    protected AppServices services() {
        return (AppServices) getServletContext().getAttribute(AppContextListener.APP_SERVICES_KEY);
    }

    protected String tenantOrDefault(String tenantIdParam) {
        if (tenantIdParam == null || tenantIdParam.isBlank()) {
            return DEFAULT_TENANT;
        }

        String normalized = tenantIdParam.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.isBlank() ? DEFAULT_TENANT : normalized;
    }

    protected String encodeQueryParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    protected String loggedInTenant(HttpServletRequest req) {
        Object tenant = req.getSession().getAttribute("tenant");
        return tenant == null ? null : tenant.toString();
    }

    protected boolean isLoggedIn(HttpServletRequest req) {
        return loggedInTenant(req) != null;
    }

    protected void requireLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!isLoggedIn(req)) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
        }
    }
}
