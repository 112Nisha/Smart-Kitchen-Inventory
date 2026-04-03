package app.web;

import app.model.DishRecipe;
import app.service.WasteImpactService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/recommendations")
public class DishRecommendationServlet extends BaseServlet {

    /** gets recommended dishes for expiring ingredients */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String tenant = tenantOrDefault(req.getParameter("tenant"));

        List<DishRecipe> recommendations =
                services().dishRecommendationService().suggestDishes(tenant);

        req.setAttribute("tenant", tenant);
        req.setAttribute("recommendations", recommendations);
        req.setAttribute("impactMessage", req.getParameter("impact"));
        req.setAttribute("successMessage", req.getParameter("success"));

        req.getRequestDispatcher("/WEB-INF/views/recommendations.jsp")
                .forward(req, resp);
    }

    /** handles POST requests to display waste impact information */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenant = tenantOrDefault(req.getParameter("tenant"));
        String dish = req.getParameter("dishName") == null ? "" : req.getParameter("dishName").trim();

        double savedQty = services().dishRecommendationService()
                .estimatedSavedIngredientQuantity(tenant, dish);

        WasteImpactService.Impact impact =
                services().wasteImpactService().estimateImpact(savedQty);

        String message = String.format(
                "You saved %.2f kg of food from landfill and avoided %.2f kg methane (%.2f kg CO2e).",
                impact.savedFoodKg(),
                impact.methaneAvoidedKg(),
                impact.co2EquivalentKg()
        );

        resp.sendRedirect(req.getContextPath()
                + "/recommendations?tenant=" + encodeQueryParam(tenant)
                + "&impact=" + encodeQueryParam(message));
    }
}