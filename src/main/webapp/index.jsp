<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("tenant") != null) {
        response.sendRedirect(request.getContextPath() + "/dashboard");
    } else {
        response.sendRedirect(request.getContextPath() + "/auth");
    }
%>
