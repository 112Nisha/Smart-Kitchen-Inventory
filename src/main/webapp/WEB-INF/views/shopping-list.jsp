<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ include file="_header.jspf" %>

<section class="card">
    <h2>Auto-Generated Shopping List</h2>
    <p>Items listed are at or below threshold.</p>

    <c:if test="${not empty param.error}">
        <p class="error"><c:out value="${param.error}"/></p>
    </c:if>

    <c:choose>
        <c:when test="${empty items}">
            <p><em>No items need reordering at this time.</em></p>
        </c:when>
        <c:otherwise>
            <table>
                <thead>
                <tr>
                    <th>Ingredient</th>
                    <th>Current Qty</th>
                    <th>Threshold</th>
                    <th>Suggested Reorder Qty</th>
                    <th>Unit</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${items}">
                    <tr>
                        <td>${item.name}</td>
                        <td><fmt:formatNumber value="${item.currentQuantity}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td><fmt:formatNumber value="${item.threshold}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td><fmt:formatNumber value="${item.suggestedReorderQty}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td>${item.unit}</td>
                        <td>
                            <form method="POST" action="${pageContext.request.contextPath}/shopping-list" style="display: inline;">
                                <input type="hidden" name="action" value="mark-purchased">
                                <input type="hidden" name="ingredientId" value="${item.ingredientId}">
                                <button type="submit" class="btn-small">Purchased</button>
                            </form>
                            <form method="POST" action="${pageContext.request.contextPath}/shopping-list" style="display: inline;">
                                <input type="hidden" name="action" value="mark-ignored">
                                <input type="hidden" name="ingredientId" value="${item.ingredientId}">
                                <button type="submit" class="btn-small">Ignore</button>
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
