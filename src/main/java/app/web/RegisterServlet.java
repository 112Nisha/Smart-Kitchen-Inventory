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
        String restaurantName = req.getParameter("restaurantName") == null ? "" : req.getParameter("restaurantName").trim();
        String username = req.getParameter("username") == null ? "" : req.getParameter("username").trim();
        String password = req.getParameter("password") == null ? "" : req.getParameter("password").trim();

        if (restaurantName.isBlank() || username.isBlank() || password.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/auth?registerError=Please fill in all registration fields.");
            return;
        }

        if (userRepository.restaurantExists(restaurantName)) {
            resp.sendRedirect(req.getContextPath() + "/auth?registerError=Restaurant already exists. Please log in.");
            return;
        }

        if (userRepository.usernameExists(username)) {
            resp.sendRedirect(req.getContextPath() + "/auth?registerError=Username already exists.");
            return;
        }

        userRepository.register(restaurantName, username, password);

        HttpSession session = req.getSession(true);
        session.setAttribute("tenant", restaurantName);
        session.setAttribute("username", username);

        resp.sendRedirect(req.getContextPath() + "/recommendations");
    }
}