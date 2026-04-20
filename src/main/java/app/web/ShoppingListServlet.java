package app.web;

import app.model.ShoppingListItem;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class ShoppingListServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (isChef(req)) {
            resp.sendRedirect(req.getContextPath() + "/recommendations");
            return;
        }
        String tenantId = loggedInTenant(req);
        if (tenantId == null) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
            return;
        }
        if ("csv".equals(req.getParameter("format"))) {
            handleCsvExport(resp, tenantId);
            return;
        }

        List<ShoppingListItem> items = services().shoppingListService().generateShoppingList(tenantId);
        List<ShoppingListItem> ignoredItems = services().shoppingListService().getIgnoredItems(tenantId);
        req.setAttribute("tenant", tenantId);
        req.setAttribute("items", items);
        req.setAttribute("ignoredItems", ignoredItems);
        req.getRequestDispatcher("/WEB-INF/views/shopping-list.jsp").forward(req, resp);
    }

    private void handleCsvExport(HttpServletResponse resp, String tenantId) throws IOException {
        List<ShoppingListItem> items = services().shoppingListService().generateShoppingList(tenantId);

        String filename = "shopping-list-" + LocalDate.now() + ".csv";
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        PrintWriter out = resp.getWriter();
        out.println("Ingredient,Current Qty,Threshold,Suggested Reorder Qty,Unit");
        for (ShoppingListItem item : items) {
            out.printf("%s,%.2f,%.2f,%.2f,%s%n",
                    escapeCsv(item.getName()),
                    item.getCurrentQuantity(),
                    item.getThreshold(),
                    item.getSuggestedReorderQty(),
                    escapeCsv(item.getUnit()));
        }
        out.flush();
    }

    private static void requireIngredientId(String ingredientId) {
        if (ingredientId == null || ingredientId.isBlank()) {
            throw new IllegalArgumentException("ingredientId is required");
        }
    }

    /** RFC 4180: quote fields that contain commas, double-quotes, or newlines. */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenantId = loggedInTenant(req);
        if (tenantId == null) {
            resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
            return;
        }

        String action = req.getParameter("action");
        String ingredientId = req.getParameter("ingredientId");

        try {
            switch (action) {
                case "mark-purchased" -> {
                    requireIngredientId(ingredientId);
                    services().shoppingListService().markPurchased(tenantId, ingredientId);
                }
                case "mark-ignored" -> {
                    requireIngredientId(ingredientId);
                    services().shoppingListService().markIgnored(tenantId, ingredientId);
                }
                case "reset" -> {
                    requireIngredientId(ingredientId);
                    services().shoppingListService().resetItem(tenantId, ingredientId);
                }
                case "mark-purchased-bulk" -> {
                    String[] ids = req.getParameterValues("ingredientId");
                    if (ids != null && ids.length > 0) {
                        services().shoppingListService().markPurchasedBulk(tenantId, Arrays.asList(ids));
                    }
                    // If nothing was selected, silently redirect — no error needed.
                }
                default -> throw new IllegalArgumentException("Unknown action: " + (action == null ? "<null>" : action));
            }
        } catch (RuntimeException ex) {
            String errorMessage = ex.getMessage() == null ? "Request failed" : ex.getMessage();
            resp.sendRedirect(req.getContextPath() + "/shopping-list?error=" + encodeQueryParam(errorMessage));
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/shopping-list");
    }
}
