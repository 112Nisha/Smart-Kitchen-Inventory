package app.web;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public class ExpiryServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        List<ExpiryAlertContext> alerts = services().expiryAlertService().evaluateAndNotify(tenantId);
        req.setAttribute("tenant", tenantId);
        req.setAttribute("alerts", alerts);
        req.getRequestDispatcher("/WEB-INF/views/expiry-alerts.jsp").forward(req, resp);
    }
}
