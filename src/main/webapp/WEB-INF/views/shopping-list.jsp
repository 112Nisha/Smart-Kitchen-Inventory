<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ include file="_header.jspf" %>

<section class="card">
    <h2>Auto-Generated Shopping List</h2>
    <p>Items listed are at or below threshold.</p>
    <p style="margin-bottom: 0.9rem;">
        <a href="${pageContext.request.contextPath}/shopping-list?format=csv"
           style="font-size: 0.88rem; font-weight: 600; color: var(--gold); text-decoration: none;">
            &#8595; Export CSV
        </a>
    </p>

    <c:if test="${not empty param.error}">
        <p class="error"><c:out value="${param.error}"/></p>
    </c:if>

    <c:choose>
        <c:when test="${empty items}">
            <p><em>No items need reordering at this time.</em></p>
        </c:when>
        <c:otherwise>
            <%-- Bulk action bar. Sits outside the table so checkboxes inside the
                 table can reference it via the HTML5 form= attribute without
                 creating illegal nested <form> elements. --%>
            <form id="bulk-form" method="POST"
                  action="${pageContext.request.contextPath}/shopping-list"
                  style="display: block; margin-bottom: 0.9rem;">
                <input type="hidden" name="action" value="mark-purchased-bulk">
                <button type="submit">Mark Selected as Purchased</button>
            </form>

            <table>
                <thead>
                <tr>
                    <th style="width: 2.5rem;">
                        <%-- Select-all toggle; no name= so it never submits a value --%>
                        <input type="checkbox" id="select-all" title="Select all items">
                    </th>
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
                        <td>
                            <%-- form= links this checkbox to bulk-form above the table --%>
                            <input type="checkbox" name="ingredientId"
                                   value="${item.ingredientId}" form="bulk-form">
                        </td>
                        <td>${item.name}</td>
                        <td><fmt:formatNumber value="${item.currentQuantity}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td><fmt:formatNumber value="${item.threshold}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td><fmt:formatNumber value="${item.suggestedReorderQty}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td>${item.unit}</td>
                        <td>
                            <form method="POST" action="${pageContext.request.contextPath}/shopping-list" style="display: inline;">
                                <input type="hidden" name="action" value="mark-ignored">
                                <input type="hidden" name="ingredientId" value="${item.ingredientId}">
                                <button type="submit">Ignore</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>

            <script>
            (function () {
                var master = document.getElementById('select-all');
                if (!master) return;
                master.addEventListener('change', function () {
                    document.querySelectorAll('input[form="bulk-form"][name="ingredientId"]')
                            .forEach(function (cb) { cb.checked = master.checked; });
                });
            }());
            </script>
        </c:otherwise>
    </c:choose>

    <c:if test="${not empty ignoredItems}">
        <details class="alert-settings-details" style="margin-top: 1.4rem;">
            <summary class="alert-settings-summary">
                Dismissed Items (${ignoredItems.size()})
            </summary>
            <p style="margin: 0.6rem 0 0.8rem; font-size: 0.88rem; color: var(--ink-3);">
                These items are below threshold but were dismissed. Restore any item to move it back to the active list.
            </p>
            <table style="opacity: 0.72;">
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
                <c:forEach var="item" items="${ignoredItems}">
                    <tr>
                        <td>${item.name}</td>
                        <td><fmt:formatNumber value="${item.currentQuantity}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td><fmt:formatNumber value="${item.threshold}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td><fmt:formatNumber value="${item.suggestedReorderQty}" minFractionDigits="2" maxFractionDigits="2"/></td>
                        <td>${item.unit}</td>
                        <td>
                            <form method="POST" action="${pageContext.request.contextPath}/shopping-list" style="display: inline;">
                                <input type="hidden" name="action" value="reset">
                                <input type="hidden" name="ingredientId" value="${item.ingredientId}">
                                <button type="submit">Restore</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </details>
    </c:if>
</section>

<%@ include file="_footer.jspf" %>
