<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ include file="_header.jspf" %>

<section class="card">
    <h2>Auto-Generated Shopping List</h2>
    <p>Items listed are at or below threshold.</p>
    <table>
        <thead>
        <tr>
            <th>Ingredient</th>
            <th>Current Qty</th>
            <th>Threshold</th>
            <th>Suggested Reorder Qty</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="item" items="${items}">
            <tr>
                <td>${item.name}</td>
                <td><fmt:formatNumber value="${item.quantity}" minFractionDigits="2" maxFractionDigits="2"/> ${item.unit}</td>
                <td><fmt:formatNumber value="${item.lowStockThreshold}" minFractionDigits="2" maxFractionDigits="2"/></td>
                <td><fmt:formatNumber value="${(item.lowStockThreshold * 2) - item.quantity}" minFractionDigits="2" maxFractionDigits="2"/> ${item.unit}</td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</section>

<%@ include file="_footer.jspf" %>
