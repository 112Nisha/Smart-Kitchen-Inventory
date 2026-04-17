package app.web;

import app.repository.UserRepository;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Optional;

@WebServlet("/login")
public class LoginServlet extends BaseServlet {

    private final UserRepository userRepository = new UserRepository();

    /** handles POST requests for user login */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String restaurantName = req.getParameter("restaurantName") == null ? ""
            : req.getParameter("restaurantName").trim();    
        String username = req.getParameter("username") == null ? "" : req.getParameter("username").trim();
        String password = req.getParameter("password") == null ? "" : req.getParameter("password").trim();
        HttpSession session = req.getSession();

        if (restaurantName.isBlank() || username.isBlank() || password.isBlank()) {
        session.setAttribute("loginError", "Please fill in all fields.");
        resp.sendRedirect(req.getContextPath() + "/auth");
        return;
    }

        Optional<UserRepository.LoginResult> loginResult = userRepository.login(username, password, restaurantName);

        if (loginResult.isEmpty()) {
            session.setAttribute("loginError", "Invalid username, password, or restaurant name.");
            resp.sendRedirect(req.getContextPath() + "/auth");
            return;
        }

        session.setAttribute("tenant", loginResult.get().restaurantName());
        session.setAttribute("username", loginResult.get().user().getUsername());
        session.setAttribute("role", loginResult.get().user().getRole());

        String role = loginResult.get().user().getRole();

        if ("chef".equalsIgnoreCase(role)) {
            resp.sendRedirect(req.getContextPath() + "/recommendations");
        } else {
            resp.sendRedirect(req.getContextPath() + "/dashboard");
        }
    }
}