package app.web;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class InventoryServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        List<Ingredient> ingredients = services().inventoryManager().listIngredients(tenantId);
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
                services().inventoryManager().useIngredient(
                        tenantId,
                        req.getParameter("id"),
                        Double.parseDouble(req.getParameter("usedQuantity"))
                );
            } else if ("discard".equals(action)) {
                services().inventoryManager().discardIngredient(tenantId, req.getParameter("id"));
            } else if ("update".equals(action)) {
                services().inventoryManager().updateIngredient(
                        tenantId,
                        req.getParameter("id"),
                        req.getParameter("name"),
                        Double.parseDouble(req.getParameter("quantity")),
                        req.getParameter("unit"),
                        LocalDate.parse(req.getParameter("expiryDate")),
                        Double.parseDouble(req.getParameter("lowStockThreshold"))
                );
            }
        } catch (RuntimeException ex) {
            resp.sendRedirect(req.getContextPath() + "/inventory?tenant=" + tenantId + "&error=" + ex.getMessage());
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/inventory?tenant=" + tenantId);
    }
}
