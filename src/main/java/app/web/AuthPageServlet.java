package app.web;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/auth")
public class AuthPageServlet extends BaseServlet {

    /** handles GET requests for the authentication page */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String redirectTarget = req.getContextPath() + "/login";
        String query = req.getQueryString();
        if (query != null && !query.isBlank()) {
            redirectTarget = redirectTarget + "?" + query;
        }
        resp.sendRedirect(redirectTarget);
    }
}