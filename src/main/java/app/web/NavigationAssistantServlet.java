package app.web;

import app.service.NavigationAssistantService.AssistantReply;

import com.google.gson.Gson;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class NavigationAssistantServlet extends BaseServlet {
    private static final Gson GSON = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tenantId = tenantOrDefault(req.getParameter("tenant"));
        String message = req.getParameter("message");
        AssistantReply reply = services().navigationAssistantService().answer(tenantId, message);

        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.getWriter().write(GSON.toJson(reply));
    }
}