package app.web;

import app.model.DishRecipe;
import app.model.RecipeIngredient;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/recipes/add")
public class AddRecipeServlet extends BaseServlet {

    /** handles GET requests to display the add recipe form */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String tenant = tenantOrDefault(req.getParameter("tenant"));
        req.setAttribute("tenant", tenant);
        req.setAttribute("successMessage", req.getParameter("success"));
        req.setAttribute("errorMessage", req.getParameter("error"));

        req.getRequestDispatcher("/WEB-INF/views/add-recipe.jsp")
                .forward(req, resp);
    }

    /** handles POST requests to add a new recipe */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenant = tenantOrDefault(req.getParameter("tenant"));

        try {
            String name = req.getParameter("recipeName") == null ? "" : req.getParameter("recipeName").trim();
            String instructions = req.getParameter("instructions") == null ? ""
                    : req.getParameter("instructions").trim();

            String[] ingredientNames = req.getParameterValues("ingredientName");
            String[] ingredientQuantities = req.getParameterValues("ingredientQuantity");
            String[] ingredientUnits = req.getParameterValues("ingredientUnit");

            List<RecipeIngredient> ingredients = new ArrayList<>();

            if (ingredientNames != null && ingredientQuantities != null && ingredientUnits != null) {
                for (int i = 0; i < ingredientNames.length; i++) {
                    String ingredientName = ingredientNames[i] == null ? "" : ingredientNames[i].trim();
                    String quantityStr = ingredientQuantities[i] == null ? "" : ingredientQuantities[i].trim();
                    String unit = ingredientUnits[i] == null ? "" : ingredientUnits[i].trim();

                    if (ingredientName.isBlank() || quantityStr.isBlank() || unit.isBlank()) {
                        continue;
                    }

                    try {
                        double quantity = Double.parseDouble(quantityStr);
                        ingredients.add(new RecipeIngredient(ingredientName, quantity, unit));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (name.isBlank() || instructions.isBlank() || ingredients.isEmpty()) {
                resp.sendRedirect(req.getContextPath()
                        + "/recipes/add?tenant=" + encodeQueryParam(tenant)
                        + "&error=" + encodeQueryParam("Please fill in all recipe details."));
                return;
            }

            DishRecipe recipe = new DishRecipe(null, name, ingredients, instructions);
            services().dishRecommendationService().addRecipe(tenant, recipe);
            resp.sendRedirect(req.getContextPath()
                    + "/recipes/add?tenant=" + encodeQueryParam(tenant)
                    + "&success=" + encodeQueryParam("Recipe added successfully."));

        } catch (Exception e) {
            e.printStackTrace();

            resp.sendRedirect(req.getContextPath()
                    + "/recipes/add?tenant=" + encodeQueryParam(tenant)
                    + "&error=" + encodeQueryParam("Failed to add recipe."));
        }
    }
}