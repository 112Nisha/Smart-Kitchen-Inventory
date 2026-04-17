package app.web;

import app.repository.UserRepository;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/register")
public class RegisterServlet extends BaseServlet {

    private final UserRepository userRepository = new UserRepository();

    /** handles POST requests for user registration */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String restaurantName = req.getParameter("restaurantName") == null ? ""
                : req.getParameter("restaurantName").trim();
        String username = req.getParameter("username") == null ? "" : req.getParameter("username").trim();
        String password = req.getParameter("password") == null ? "" : req.getParameter("password").trim();
        String role = req.getParameter("role") == null ? "" : req.getParameter("role").trim().toLowerCase();

        if (restaurantName.isBlank() || username.isBlank() || password.isBlank() || role.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/auth?registerError=Please fill in all registration fields.");
            return;
        }

        if (userRepository.usernameExists(username)) {
            resp.sendRedirect(req.getContextPath() + "/auth?registerError=Username already exists.");
            return;
        }

        try {
            userRepository.register(restaurantName, username, password, role);
        } catch (IllegalArgumentException e) {
            resp.sendRedirect(req.getContextPath() + "/auth?registerError=" + e.getMessage());
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/auth?success=Registration successful. Please log in.");
    }
}