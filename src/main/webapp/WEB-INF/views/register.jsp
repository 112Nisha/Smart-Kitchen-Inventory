<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="_header.jspf" %>

<section class="auth-grid">
    <section class="card auth-card">
        <h2>Register</h2>
        <p class="auth-subtext">Create a new restaurant account or register as a new member of an existing restaurant to start managing ingredients and recipes</p>

        <c:if test="${not empty sessionScope.registerError}">
            <p class="error">
                <c:out value="${sessionScope.registerError}" />
            </p>
            <c:remove var="registerError" scope="session" />
        </c:if>

        <form method="post" action="${pageContext.request.contextPath}/register">
            <label for="register-restaurant">Restaurant Name</label>
            <input id="register-restaurant" type="text" name="restaurantName" required>

            <label for="register-username">Username</label>
            <input id="register-username" type="text" name="username" required>

            <label for="register-password">Password</label>
            <input id="register-password" type="password" name="password" required>

            <label for="register-role">Role</label>
            <select id="register-role" name="role" required>
                <option value="">Select role</option>
                <option value="manager">Manager</option>
                <option value="chef">Chef</option>
            </select>

            <button type="submit">Register</button>
        </form>

        <p class="auth-subtext">Already registered? <a href="${pageContext.request.contextPath}/login">Go to login</a>.</p>
    </section>
</section>

<%@ include file="_footer.jspf" %>
