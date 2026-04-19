package app.web;

import app.model.Ingredient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class InventoryServlet extends BaseServlet {
    private static final Pattern ISO_DATE_WITH_FOUR_DIGIT_YEAR = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = loggedInTenant(req);
        if (tenantId == null) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
            return;
        }
        List<Ingredient> ingredients = services().inventoryManager().listIngredients(tenantId).stream()
            .filter(ingredient -> !ingredient.isDiscarded())
            .filter(ingredient -> ingredient.getQuantity() > 1e-9)
            .toList();
        req.setAttribute("tenant", tenantId);
        req.setAttribute("today", LocalDate.now());
        req.setAttribute("ingredients", ingredients);
        req.getRequestDispatcher("/WEB-INF/views/inventory.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenantId = loggedInTenant(req);
        if (tenantId == null) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
            return;
        }
        String action = req.getParameter("action");
        LocalDate today = LocalDate.now();

        try {
            if ("add".equals(action)) {
                Ingredient ingredient = new Ingredient(
                        tenantId,
                        req.getParameter("name"),
                        Double.parseDouble(req.getParameter("quantity")),
                        req.getParameter("unit"),
                        parseExpiryDate(req.getParameter("expiryDate"), today),
                        Double.parseDouble(req.getParameter("lowStockThreshold"))
                );
                services().inventoryManager().addIngredient(ingredient);
            } else if ("use".equals(action)) {
                boolean found = services().inventoryManager().useIngredient(
                        tenantId,
                        req.getParameter("id"),
                        Double.parseDouble(req.getParameter("usedQuantity"))
                ).isPresent();
                if (!found) {
                    throw new IllegalArgumentException("Ingredient not found for use action");
                }
            } else if ("discard".equals(action)) {
                boolean found = services().inventoryManager().discardIngredient(tenantId, req.getParameter("id")).isPresent();
                if (!found) {
                    throw new IllegalArgumentException("Ingredient not found for discard action");
                }
            } else if ("update".equals(action)) {
                boolean found = services().inventoryManager().updateIngredient(
                        tenantId,
                        req.getParameter("id"),
                        req.getParameter("name"),
                        Double.parseDouble(req.getParameter("quantity")),
                        req.getParameter("unit"),
                        parseExpiryDate(req.getParameter("expiryDate"), today),
                        Double.parseDouble(req.getParameter("lowStockThreshold"))
                ).isPresent();
                if (!found) {
                    throw new IllegalArgumentException("Ingredient not found for update action");
                }
            } else {
                throw new IllegalArgumentException("Unsupported action: " + (action == null ? "<null>" : action));
            }
        } catch (RuntimeException ex) {
            String errorMessage = ex.getMessage() == null ? "Request failed" : ex.getMessage();
            resp.sendRedirect(req.getContextPath()
                    + "/inventory?tenant=" + encodeQueryParam(tenantId)
                    + "&error=" + encodeQueryParam(errorMessage));
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/inventory?tenant=" + encodeQueryParam(tenantId));
    }

    static LocalDate parseExpiryDate(String rawExpiryDate, LocalDate today) {
        LocalDate currentDate = Objects.requireNonNull(today, "today is required");
        String normalized = rawExpiryDate == null ? "" : rawExpiryDate.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Expiry date is required");
        }
        if (!ISO_DATE_WITH_FOUR_DIGIT_YEAR.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Expiry date must use yyyy-MM-dd with a 4-digit year");
        }

        LocalDate expiryDate;
        try {
            expiryDate = LocalDate.parse(normalized);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Expiry date is invalid", ex);
        }

        if (expiryDate.isBefore(currentDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before today");
        }
        return expiryDate;
    }
}
