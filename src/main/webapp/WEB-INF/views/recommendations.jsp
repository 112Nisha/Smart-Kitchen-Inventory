<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty impactDish}">
    <section class="impact-summary" role="status" aria-live="polite">
        <h3>Cooking Impact Summary</h3>

        <div class="impact-grid">
            <div class="impact-item">
                <span class="impact-label">Dish Cooked &#127859;</span>
                <span class="impact-value impact-value-main"><c:out value="${impactDish}"/></span>
            </div>

            <div class="impact-item">
                <span class="impact-label">Total Ingredients Used &#129368;</span>
                <span class="impact-value"><c:out value="${impactUsedByUnit}"/></span>
            </div>

            <div class="impact-item">
                <span class="impact-label">Near-Expiry Food Used (kg) &#9878;</span>
                <span class="impact-value impact-value-warn"><c:out value="${impactNearExpiryKg}"/> kg</span>
            </div>

            <div class="impact-item">
                <span class="impact-label">Near-Expiry Food Used (litre) &#129371;</span>
                <span class="impact-value impact-value-warn"><c:out value="${impactNearExpiryLiters}"/> litre</span>
            </div>

            <div class="impact-item">
                <span class="impact-label">Ingredients Saved From Waste &#9851;</span>
                <span class="impact-value impact-value-good"><c:out value="${impactSavedIngredientCount}"/> ingredients</span>
            </div>

            <div class="impact-item">
                <span class="impact-label">Rescued Ingredients &#129365;</span>
                <c:choose>
                    <c:when test="${not empty impactSavedIngredients}">
                        <c:set var="savedIngredients" value="${fn:split(impactSavedIngredients, '|')}"/>
                        <ol class="impact-ingredients-list">
                            <c:forEach var="ingredientName" items="${savedIngredients}">
                                <c:if test="${not empty ingredientName}">
                                    <li><c:out value="${ingredientName}"/></li>
                                </c:if>
                            </c:forEach>
                        </ol>
                    </c:when>
                    <c:otherwise>
                        <span class="impact-value">No near-expiry ingredients were consumed.</span>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

        <p class="impact-note">
            You used <c:out value="${impactNearExpiryKg}"/> kg and <c:out value="${impactNearExpiryLiters}"/> litre
            of near-expiring food, and saved <c:out value="${impactSavedIngredientCount}"/> ingredients from going to waste.
        </p>
    </section>
</c:if>

<c:if test="${not empty errorMessage}">
    <p class="error"><c:out value="${errorMessage}"/></p>
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