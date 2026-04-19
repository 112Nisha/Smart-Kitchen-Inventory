package app.web;

import app.model.Ingredient;
import app.model.IngredientLifecycle;
import app.model.NotificationMessage;
import app.model.ShoppingListItem;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;

import java.io.IOException;
import java.util.List;

@WebServlet("/dashboard")
public class DashboardServlet extends BaseServlet {
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
                List<ShoppingListItem> shoppingList = services().shoppingListService().generateShoppingList(tenantId);
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
