package app.web;

import app.model.Ingredient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class InventoryServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        List<Ingredient> ingredients = services().inventoryManager().listIngredients(tenantId).stream()
            .filter(ingredient -> ingredient.getQuantity() > 1e-9)
            .toList();
        req.setAttribute("tenant", tenantId);
        req.setAttribute("ingredients", ingredients);
        req.getRequestDispatcher("/WEB-INF/views/inventory.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        String action = req.getParameter("action");

        try {
            if ("add".equals(action)) {
                Ingredient ingredient = new Ingredient(
                        tenantId,
                        req.getParameter("name"),
                        Double.parseDouble(req.getParameter("quantity")),
                        req.getParameter("unit"),
                        LocalDate.parse(req.getParameter("expiryDate")),
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
                        LocalDate.parse(req.getParameter("expiryDate")),
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
}
