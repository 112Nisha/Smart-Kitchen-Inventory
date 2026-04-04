package app.web;

import app.model.DishRecipe;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/recipes/manage")
public class ManageRecipesServlet extends BaseServlet {

    /** handles GET requests to display the manage recipes page */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String tenant = tenantOrDefault(req.getParameter("tenant"));
        List<DishRecipe> allRecipes = services().dishRecommendationService().getAllRecipes();

        req.setAttribute("tenant", tenant);
        req.setAttribute("allRecipes", allRecipes);
        req.setAttribute("successMessage", req.getParameter("success"));

        req.getRequestDispatcher("/WEB-INF/views/manage-recipes.jsp")
                .forward(req, resp);
    }

    /** handles POST requests to delete a recipe */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenant = tenantOrDefault(req.getParameter("tenant"));
        String recipeIdStr = req.getParameter("recipeId");

        if (recipeIdStr != null && !recipeIdStr.isBlank()) {
            try {
                Long recipeId = Long.parseLong(recipeIdStr);
                services().dishRecommendationService().deleteRecipe(recipeId);
            } catch (NumberFormatException ignored) {
            }
        }

        resp.sendRedirect(req.getContextPath()
                + "/recipes/manage?tenant=" + encodeQueryParam(tenant)
                + "&success=" + encodeQueryParam("Recipe deleted successfully."));
    }
}