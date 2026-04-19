<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="_header.jspf" %>

<section class="card">
    <details class="alert-settings-details">
        <summary class="alert-settings-summary">Alert Settings</summary>

        <c:if test="${not empty successMessage}">
            <p class="success-message"><c:out value="${successMessage}"/></p>
        </c:if>
        <c:if test="${not empty errorMessage}">
            <p class="error-message"><c:out value="${errorMessage}"/></p>
        </c:if>

        <form method="post" action="${pageContext.request.contextPath}/notifications" class="config-form">
            <div class="form-row">
                <label for="nearExpiryDays">Near-expiry window (days)</label>
                <input type="number" id="nearExpiryDays" name="nearExpiryDays"
                       value="${nearExpiryDays}" min="0" max="${maxDays}" required>
            </div>

            <div class="form-row">
                <label for="retentionDays">Notification retention (days)</label>
                <input type="number" id="retentionDays" name="retentionDays"
                       value="${retentionDays}" min="1" max="${maxDays}" required>
            </div>

            <button type="submit">Save</button>
        </form>
    </details>
</section>

<section class="card">
    <h2>Notifications</h2>

    <c:choose>
        <c:when test="${empty notifications}">
            <p>No notifications yet.</p>
        </c:when>
        <c:otherwise>
            <table>
                <thead>
                <tr>
                    <th>When</th>
                    <th>Subject</th>
                    <th>Message</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="n" items="${notifications}">
                    <tr class="row-${n.status}">
                        <td>${n.when}</td>
                        <td><c:out value="${n.subject}"/></td>
                        <td><c:out value="${n.body}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</section>

<%@ include file="_footer.jspf" %>
