<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty param.impact}">
    <p class="success">${param.impact}</p>
</c:if>

<section class="card">
    <h2>Suggested Dishes for Near-Expiry Ingredients</h2>
    <c:choose>
        <c:when test="${empty suggestions}">
            <p>No suggestions currently. You may not have near-expiry ingredients right now.</p>
        </c:when>
        <c:otherwise>
            <table>
                <thead>
                <tr>
                    <th>Dish</th>
                    <th>Ingredients</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="dish" items="${suggestions}">
                    <tr>
                        <td>${dish.name}</td>
                        <td>${dish.ingredientNames}</td>
                        <td>
                            <form method="post" action="${pageContext.request.contextPath}/recommendations">
                                <input type="hidden" name="tenant" value="${tenant}">
                                <input type="hidden" name="dishName" value="${dish.name}">
                                <button type="submit">Log as Cooked</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</section>

<%@ include file="_footer.jspf" %>
