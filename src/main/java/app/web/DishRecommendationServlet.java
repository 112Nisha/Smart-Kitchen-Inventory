package app.web;

import app.model.DishRecipe;
import app.service.WasteImpactService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public class DishRecommendationServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        List<DishRecipe> suggestions = services().dishRecommendationService().suggestForExpiringIngredients(tenantId);
        req.setAttribute("tenant", tenantId);
        req.setAttribute("suggestions", suggestions);
        req.getRequestDispatcher("/WEB-INF/views/recommendations.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        String dish = req.getParameter("dishName") == null ? "" : req.getParameter("dishName").trim();

        double savedQty = services().dishRecommendationService().estimatedSavedIngredientQuantity(tenantId, dish);
        WasteImpactService.Impact impact = services().wasteImpactService().estimateImpact(savedQty);

        String message = String.format(
                "You saved %.2f kg of food from landfill and avoided %.2f kg methane (%.2f kg CO2e).",
                impact.savedFoodKg(),
                impact.methaneAvoidedKg(),
                impact.co2EquivalentKg()
        );
        String encodedMessage = encodeQueryParam(message);
        resp.sendRedirect(req.getContextPath()
                + "/recommendations?tenant=" + encodeQueryParam(tenantId)
                + "&impact=" + encodedMessage);
    }
}
