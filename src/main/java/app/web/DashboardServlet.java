package app.web;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public class DashboardServlet extends BaseServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                String tenantId = loggedInTenant(req);
                if (tenantId == null) {
                        resp.sendRedirect(req.getContextPath() + "/auth?error=Please log in first.");
                        return;
                }
                List<Ingredient> ingredients = services().inventoryManager().listIngredients(tenantId);
                List<Ingredient> shoppingList = services().shoppingListService().generateShoppingList(tenantId);
                List<NotificationMessage> notifications = services().notificationStore().all().stream()
                                .filter(item -> item.getTenantId().equals(tenantId))
                                .toList();

                long nearExpiryCount = ingredients.stream()
                                .filter(item -> item.getLifecycle() == IngredientLifecycle.NEAR_EXPIRY)
                                .count();
                long expiredCount = ingredients.stream()
                                .filter(item -> item.getLifecycle() == IngredientLifecycle.EXPIRED)
                                .count();

                req.setAttribute("tenant", tenantId);
                req.setAttribute("ingredientCount", ingredients.size());
                req.setAttribute("nearExpiryCount", nearExpiryCount);
                req.setAttribute("expiredCount", expiredCount);
                req.setAttribute("shoppingCount", shoppingList.size());
                req.setAttribute("notificationCount", notifications.size());

                req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, resp);
        }
}
