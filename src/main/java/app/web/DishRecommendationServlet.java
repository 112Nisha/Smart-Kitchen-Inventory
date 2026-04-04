package app.web;

import app.model.DishRecipe;
import app.service.DishRecommendationService;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet("/recommendations")
public class DishRecommendationServlet extends BaseServlet {

    /** gets recommended dishes for expiring ingredients */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");

        String tenant = tenantOrDefault(req.getParameter("tenant"));

        List<DishRecipe> recommendations =
                services().dishRecommendationService().suggestDishes(tenant);

        req.setAttribute("tenant", tenant);
        req.setAttribute("recommendations", recommendations);
        req.setAttribute("impactDish", req.getParameter("impactDish"));
        req.setAttribute("impactUsedByUnit", req.getParameter("impactUsedByUnit"));
        req.setAttribute("impactNearExpiryKg", req.getParameter("impactNearExpiryKg"));
        req.setAttribute("impactNearExpiryLiters", req.getParameter("impactNearExpiryLiters"));
        req.setAttribute("impactSavedIngredientCount", req.getParameter("impactSavedIngredientCount"));
        req.setAttribute("impactSavedIngredients", req.getParameter("impactSavedIngredients"));
        req.setAttribute("errorMessage", req.getParameter("error"));

        req.getRequestDispatcher("/WEB-INF/views/recommendations.jsp")
                .forward(req, resp);
    }

    /** handles POST requests to log cooked dishes and show near-expiry usage summary */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenant = tenantOrDefault(req.getParameter("tenant"));
        String dish = req.getParameter("dishName") == null ? "" : req.getParameter("dishName").trim();

        try {
            DishRecommendationService.CookDishResult cookResult = services().dishRecommendationService()
                    .logDishAsCooked(tenant, dish);

            String usedByUnit = formatUsedByUnit(cookResult.usedByUnit());
            String rescuedIngredients = cookResult.rescuedNearExpiryIngredients().stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining("|"));

            resp.sendRedirect(req.getContextPath()
                    + "/recommendations?tenant=" + encodeQueryParam(tenant)
                    + "&impactDish=" + encodeQueryParam(cookResult.dishName())
                    + "&impactUsedByUnit=" + encodeQueryParam(usedByUnit)
                    + "&impactNearExpiryKg=" + encodeQueryParam(String.format("%.2f", cookResult.nearExpiryUsedKg()))
                    + "&impactNearExpiryLiters=" + encodeQueryParam(String.format("%.2f", cookResult.nearExpiryUsedLiters()))
                    + "&impactSavedIngredientCount=" + encodeQueryParam(String.valueOf(cookResult.rescuedNearExpiryIngredients().size()))
                    + "&impactSavedIngredients=" + encodeQueryParam(rescuedIngredients));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            resp.sendRedirect(req.getContextPath()
                    + "/recommendations?tenant=" + encodeQueryParam(tenant)
                    + "&error=" + encodeQueryParam(ex.getMessage()));
        }
    }

    private String formatUsedByUnit(Map<String, Double> usedByUnit) {
        if (usedByUnit == null || usedByUnit.isEmpty()) {
            return "0.00 kg";
        }

        List<String> parts = new ArrayList<>();
        appendUnitPart(parts, usedByUnit, "kg");
        appendUnitPart(parts, usedByUnit, "liters");
        appendUnitPart(parts, usedByUnit, "units");

        usedByUnit.entrySet().stream()
                .filter(entry -> !entry.getKey().equalsIgnoreCase("kg"))
                .filter(entry -> !entry.getKey().equalsIgnoreCase("liters"))
                .filter(entry -> !entry.getKey().equalsIgnoreCase("units"))
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted((left, right) -> left.getKey().compareToIgnoreCase(right.getKey()))
                .forEach(entry -> parts.add(String.format("%.2f %s", entry.getValue(), entry.getKey())));

        return parts.isEmpty() ? "0.00 kg" : String.join(" and ", parts);
    }

    private void appendUnitPart(List<String> parts, Map<String, Double> usedByUnit, String unitKey) {
        Double value = usedByUnit.get(unitKey);
        if (value != null && value > 0) {
            parts.add(String.format("%.2f %s", value, unitKey));
        }
    }
}