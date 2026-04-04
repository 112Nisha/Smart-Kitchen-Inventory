<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty param.error}">
    <p class="error"><c:out value="${param.error}"/></p>
</c:if>

<section class="card">
    <h2>Add Ingredient</h2>
    <form method="post" action="${pageContext.request.contextPath}/inventory">
        <input type="hidden" name="action" value="add">
        <input type="hidden" name="tenant" value="${tenant}">

        <label>Name</label>
        <input name="name" required>

        <label>Quantity</label>
        <input name="quantity" type="number" step="0.01" min="0" required>

        <label>Unit</label>
        <input name="unit" placeholder="kg / units / liters" required>

        <label>Expiry Date</label>
        <input name="expiryDate" type="date" required>

        <label>Low Stock Threshold</label>
        <input name="lowStockThreshold" type="number" step="0.01" min="0" required>

        <button type="submit">Add Ingredient</button>
    </form>
</section>

<section class="card">
    <h2>Current Inventory</h2>
    <table>
        <thead>
        <tr>
            <th>Name</th>
            <th>Quantity</th>
            <th>Expiry</th>
            <th>State</th>
            <th>Threshold</th>
            <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="item" items="${ingredients}">
            <tr>
                <td>${item.name}</td>
                <td>${item.quantity} ${item.unit}</td>
                <td>${item.expiryDate}</td>
                <td>${item.lifecycle}</td>
                <td>${item.lowStockThreshold}</td>
                <td>
                    <form class="inline" method="post" action="${pageContext.request.contextPath}/inventory">
                        <input type="hidden" name="action" value="use">
                        <input type="hidden" name="tenant" value="${tenant}">
                        <input type="hidden" name="id" value="${item.id}">
                        <input name="usedQuantity" type="number" step="0.01" min="0.01" placeholder="Qty used" required>
                        <button type="submit">Use</button>
                    </form>
                    <form class="inline" method="post" action="${pageContext.request.contextPath}/inventory">
                        <input type="hidden" name="action" value="discard">
                        <input type="hidden" name="tenant" value="${tenant}">
                        <input type="hidden" name="id" value="${item.id}">
                        <button type="submit" class="danger">Discard</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</section>

<%@ include file="_footer.jspf" %>
