<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty successMessage}">
    <p class="success"><c:out value="${successMessage}"/></p>
</c:if>

<section class="card">
    <h2>Manage Recipes</h2>

    <p>
        <a href="${pageContext.request.contextPath}/recommendations?tenantId=${tenantId}">
            <button type="button">Back to Recommendations</button>
        </a>
    </p>

    <c:choose>
        <c:when test="${empty allRecipes}">
            <p>No recipes available.</p>
        </c:when>

        <c:otherwise>
            <table>
                <thead>
                <tr>
                    <th>Dish</th>
                    <th>Ingredients</th>
                    <th>Instructions</th>
                    <th>Delete</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="dish" items="${allRecipes}">
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
                            <form method="post" action="${pageContext.request.contextPath}/recipes/manage"
                                  onsubmit="return confirm('Delete this recipe?');">
                                <input type="hidden" name="tenant" value="${tenant}">
                                <input type="hidden" name="recipeId" value="${dish.id}">
                                <button type="submit">Delete</button>
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