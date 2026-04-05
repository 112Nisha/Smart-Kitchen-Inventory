<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty param.success}">
    <p class="success"><c:out value="${param.success}" /></p>
</c:if>

<section class="auth-grid">

    <section class="card auth-card">
        <h2>Login to Existing Restaurant</h2>
        <p class="auth-subtext">Access your restaurant dashboard, inventory, and dish recommendations</p>

        <c:if test="${not empty param.loginError}">
            <p class="error"><c:out value="${param.loginError}" /></p>
        </c:if>

        <form method="post" action="${pageContext.request.contextPath}/login">
            <label for="login-username">Username</label>
            <input id="login-username" type="text" name="username" required>

            <label for="login-password">Password</label>
            <input id="login-password" type="password" name="password" required>

            <button type="submit">Login</button>
        </form>
    </section>

    <section class="card auth-card">
        <h2>Register New Restaurant</h2>
        <p class="auth-subtext">Create a new restaurant account to start managing ingredients and recipes</p>

        <c:if test="${not empty param.registerError}">
            <p class="error"><c:out value="${param.registerError}" /></p>
        </c:if>

        <form method="post" action="${pageContext.request.contextPath}/register">
            <label for="register-restaurant">Restaurant Name</label>
            <input id="register-restaurant" type="text" name="restaurantName" required>

            <label for="register-username">Username</label>
            <input id="register-username" type="text" name="username" required>

            <label for="register-password">Password</label>
            <input id="register-password" type="password" name="password" required>

            <button type="submit">Register</button>
        </form>
    </section>

</section>

<%@ include file="_footer.jspf" %>