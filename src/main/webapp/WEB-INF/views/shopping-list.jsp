<%@ taglib uri="jakarta.tags.core" prefix="c" %>
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
                <td>${item.quantity} ${item.unit}</td>
                <td>${item.lowStockThreshold}</td>
                <td>${(item.lowStockThreshold * 2) - item.quantity} ${item.unit}</td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</section>

<%@ include file="_footer.jspf" %>
