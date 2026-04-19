package app.web;

import app.model.ShoppingListItem;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
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
        List<ShoppingListItem> items = services().shoppingListService().generateShoppingList(tenantId);
        req.setAttribute("tenant", tenantId);
        req.setAttribute("items", items);
        req.getRequestDispatcher("/WEB-INF/views/shopping-list.jsp").forward(req, resp);
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
            if (ingredientId == null || ingredientId.isBlank()) {
                throw new IllegalArgumentException("ingredientId is required");
            }

            switch (action) {
                case "mark-purchased" -> services().shoppingListService().markPurchased(tenantId, ingredientId);
                case "mark-ignored" -> services().shoppingListService().markIgnored(tenantId, ingredientId);
                case "reset" -> services().shoppingListService().resetItem(tenantId, ingredientId);
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
