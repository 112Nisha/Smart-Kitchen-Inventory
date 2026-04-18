<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <%@ include file="_header.jspf" %>

        <c:if test="${not empty sessionScope.success}">
            <p class="success">
                <c:out value="${sessionScope.success}" />
            </p>
            <c:remove var="success" scope="session" />
        </c:if>

        <section class="auth-grid">

            <section class="card auth-card">
                <h2>Login to Existing Restaurant</h2>
                <p class="auth-subtext">Access your restaurant dashboard, inventory, and dish recommendations</p>

                <c:if test="${not empty sessionScope.loginError}">
                    <p class="error">
                        <c:out value="${sessionScope.loginError}" />
                    </p>
                    <c:remove var="loginError" scope="session" />
                </c:if>

                <form method="post" action="${pageContext.request.contextPath}/login">

                    <label for="login-restaurant">Restaurant Name</label>
                    <input id="login-restaurant" type="text" name="restaurantName" required>

                    <label for="login-username">Username</label>
                    <input id="login-username" type="text" name="username" required>

                    <label for="login-password">Password</label>
                    <input id="login-password" type="password" name="password" required>

                    <button type="submit">Login</button>
                </form>
            </section>

            <section class="card auth-card">
                <h2>Register</h2>
                <p class="auth-subtext">Create a new restaurant account or register as a new member of an existing
                    restaurant to start managing ingredients and recipes</p>

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
            </section>

        </section>

        <%@ include file="_footer.jspf" %>