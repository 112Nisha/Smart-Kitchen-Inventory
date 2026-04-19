package app.web;

import app.model.ExpiryAlertContext;
import app.service.ExpiryAlertScheduler;
import app.service.IngredientStateTracker;
import app.service.StakeholderNotificationHandler;
import app.model.*;
import app.repository.InMemoryNotificationStore;
import app.repository.NotificationStore;
import app.repository.SqliteNotificationStore;
import app.service.DashboardNotificationStrategy;
import app.service.NotificationService;
import app.service.NotificationStrategy;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;

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
        List<Ingredient> items = services().shoppingListService().generateShoppingList(tenantId);
        req.setAttribute("tenant", tenantId);
        req.setAttribute("items", items);
        req.getRequestDispatcher("/WEB-INF/views/shopping-list.jsp").forward(req, resp);
    }
}
