package app.web;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


import jakarta.servlet.http.HttpServlet;

public abstract class BaseServlet extends HttpServlet {
    protected AppServices services() {
        return (AppServices) getServletContext().getAttribute(AppContextListener.APP_SERVICES_KEY);
    }

    protected String tenantOrDefault(String tenantIdParam) {
        return (tenantIdParam == null || tenantIdParam.isBlank()) ? "restaurant-a" : tenantIdParam.trim();
    }
}
