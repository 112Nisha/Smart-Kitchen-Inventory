<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty impactMessage}">
    <p class="success"><c:out value="${impactMessage}"/></p>
</c:if>

<c:if test="${not empty successMessage}">
    <p class="success"><c:out value="${successMessage}"/></p>
</c:if>

<section class="card">
    <h2>Suggested Dishes for Near-Expiry Ingredients</h2>

    <p style="margin-bottom: 16px;">
        <a href="${pageContext.request.contextPath}/recipes/add?tenantId=${tenantId}">
            <button type="button">Add New Recipe</button>
        </a>

        <a href="${pageContext.request.contextPath}/recipes/manage?tenantId=${tenantId}" style="margin-left: 10px;">
            <button type="button">Manage / Delete Recipes</button>
        </a>
    </p>

    <c:choose>
        <c:when test="${empty recommendations}">
            <p>No suggestions currently. You may not have enough usable ingredients right now.</p>
        </c:when>

        <c:otherwise>
            <table>
                <thead>
                <tr>
                    <th>Dish</th>
                    <th>Ingredients</th>
                    <th>Instructions</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="dish" items="${recommendations}">
                    <tr>
                        <td>${dish.name}</td>

                        <td>
                            <ul style="margin: 0; padding-left: 18px;">
                                <c:forEach var="ingredient" items="${dish.ingredients}">
                                    <li>${ingredient.name} - ${ingredient.quantity} ${ingredient.unit}</li>
                                </c:forEach>
                            </ul>
                        </td>

                        <td style="max-width: 300px;">
                            <pre style="
                                white-space: pre-wrap;
                                margin: 0;
                                font-family: Arial, sans-serif;
                                font-size: 14px;
                                line-height: 1.6;
                                color: #333;
                            "><c:out value="${dish.instructions}" /></pre>
                        </td>

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