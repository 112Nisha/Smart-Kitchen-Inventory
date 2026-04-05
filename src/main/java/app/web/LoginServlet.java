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
        String username = req.getParameter("username") == null ? "" : req.getParameter("username").trim();
        String password = req.getParameter("password") == null ? "" : req.getParameter("password").trim();

        if (username.isBlank() || password.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/auth?loginError=Invalid username or password.");
            return;
        }

        Optional<UserRepository.LoginResult> loginResult = userRepository.login(username, password);

        if (loginResult.isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/auth?loginError=Invalid username or password.");
            return;
        }

        HttpSession session = req.getSession(true);
        session.setAttribute("tenant", loginResult.get().restaurantName());
        session.setAttribute("username", loginResult.get().user().getUsername());

        resp.sendRedirect(req.getContextPath() + "/recommendations");
    }
}