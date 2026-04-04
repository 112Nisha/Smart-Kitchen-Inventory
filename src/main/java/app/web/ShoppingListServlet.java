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

public class ShoppingListServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        List<Ingredient> items = services().shoppingListService().generateShoppingList(tenantId);
        req.setAttribute("tenant", tenantId);
        req.setAttribute("items", items);
        req.getRequestDispatcher("/WEB-INF/views/shopping-list.jsp").forward(req, resp);
    }
}
