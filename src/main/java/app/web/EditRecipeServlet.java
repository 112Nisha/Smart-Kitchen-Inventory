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

@WebServlet("/recipes/edit")
public class EditRecipeServlet extends BaseServlet {

    /** loads the edit recipe form */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String tenant = tenantOrDefault(req.getParameter("tenant"));
        req.setAttribute("tenant", tenant);
        req.setAttribute("errorMessage", req.getParameter("error"));

        try {
            String recipeIdParam = req.getParameter("id");

            if (recipeIdParam == null || recipeIdParam.isBlank()) {
                resp.sendRedirect(req.getContextPath()
                        + "/recipes/manage?tenant=" + encodeQueryParam(tenant)
                        + "&error=" + encodeQueryParam("Recipe ID is required."));
                return;
            }

            Long recipeId = Long.parseLong(recipeIdParam);
            DishRecipe recipe = services().dishRecommendationService().getRecipeById(tenant, recipeId);

            if (recipe == null) {
                resp.sendRedirect(req.getContextPath()
                        + "/recipes/manage?tenant=" + encodeQueryParam(tenant)
                        + "&error=" + encodeQueryParam("Recipe not found."));
                return;
            }

            req.setAttribute("recipe", recipe);
            req.getRequestDispatcher("/WEB-INF/views/edit-recipe.jsp").forward(req, resp);

        } catch (Exception e) {
            e.printStackTrace();
            resp.sendRedirect(req.getContextPath()
                    + "/recipes/manage?tenant=" + encodeQueryParam(tenant)
                    + "&error=" + encodeQueryParam("Failed to load recipe for editing."));
        }
    }

    /** saves edited recipe */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenant = tenantOrDefault(req.getParameter("tenant"));

        try {
            String recipeIdParam = req.getParameter("recipeId");
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

            if (recipeIdParam == null || recipeIdParam.isBlank() || name.isBlank()
                    || instructions.isBlank() || ingredients.isEmpty()) {
                resp.sendRedirect(req.getContextPath()
                        + "/recipes/edit?tenant=" + encodeQueryParam(tenant)
                        + "&id=" + encodeQueryParam(recipeIdParam)
                        + "&error=" + encodeQueryParam("Please fill in all recipe details."));
                return;
            }

            Long recipeId = Long.parseLong(recipeIdParam);
            DishRecipe recipe = new DishRecipe(null, recipeId, name, ingredients, instructions);

            services().dishRecommendationService().updateRecipe(tenant, recipe);

            resp.sendRedirect(req.getContextPath()
                    + "/recipes/manage?tenant=" + encodeQueryParam(tenant)
                    + "&success=" + encodeQueryParam("Recipe updated successfully."));

        } catch (Exception e) {
            e.printStackTrace();

            resp.sendRedirect(req.getContextPath()
                    + "/recipes/manage?tenant=" + encodeQueryParam(tenant)
                    + "&error=" + encodeQueryParam("Failed to update recipe."));
        }
    }
}