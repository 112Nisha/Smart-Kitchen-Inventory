<%@ include file="_header.jspf" %>
<section class="stats-grid">
    <article class="card">
        <h2>Total Ingredients</h2>
        <p>${ingredientCount}</p>
    </article>
    <article class="card">
        <h2>Near Expiry</h2>
        <p>${nearExpiryCount}</p>
    </article>
    <article class="card">
        <h2>Expired</h2>
        <p>${expiredCount}</p>
    </article>
    <article class="card">
        <h2>Shopping Needed</h2>
        <p>${shoppingCount}</p>
    </article>
    <article class="card">
        <h2>Notifications Sent</h2>
        <p>${notificationCount}</p>
    </article>
</section>

<section class="card">
    <h2>Switch Restaurant (Tenant)</h2>
    <form method="get" action="${pageContext.request.contextPath}/dashboard">
        <label for="tenant">Tenant ID</label>
        <input id="tenant" name="tenant" value="${tenant}" required>
        <button type="submit">Switch</button>
    </form>
</section>
<%@ include file="_footer.jspf" %>
