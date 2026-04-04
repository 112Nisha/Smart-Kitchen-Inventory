<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty successMessage}">
    <p class="success"><c:out value="${successMessage}"/></p>
</c:if>

<section class="card">
    <h2>Add New Recipe</h2>

    <p>
        <a href="${pageContext.request.contextPath}/recommendations?tenantId=${tenantId}">
            <button type="button">Back to Recommendations</button>
        </a>
    </p>

    <form method="post" action="${pageContext.request.contextPath}/recipes/add">
        <input type="hidden" name="tenant" value="${tenant}">

        <p>
            <label>Recipe Name:</label><br>
            <input type="text" name="recipeName" required style="width: 100%;">
        </p>

        <p>
            <label>Instructions:</label><br>
            <textarea name="instructions" rows="8" required style="width: 100%;"></textarea>
        </p>

        <h3>Ingredients</h3>

        <div id="ingredients-container">
    <div class="ingredient-row" style="display: flex; gap: 10px; margin-bottom: 10px; align-items: center; flex-wrap: wrap;">
        <input type="text" name="ingredientName" placeholder="Ingredient name" required style="flex: 2;">
        <input type="number" step="0.01" name="ingredientQuantity" placeholder="Qty" required style="width: 100px;">
        <input type="text" name="ingredientUnit" placeholder="Unit" required style="width: 120px;">
        <button type="button" onclick="removeIngredient(this)">Remove</button>
    </div>
</div>

        <p>
            <button type="button" onclick="addIngredient()">+ Add Ingredient</button>
        </p>

        <p>
            <button type="submit">Save Recipe</button>
        </p>
    </form>
</section>

<script>
    // adds a new ingredient input row to the form
    function addIngredient() {
        const container = document.getElementById("ingredients-container");

        const row = document.createElement("div");
        row.className = "ingredient-row";
        row.style.display = "flex";
        row.style.gap = "10px";
        row.style.marginBottom = "10px";
        row.style.alignItems = "center";
        row.style.flexWrap = "wrap";

        row.innerHTML = `
            <input type="text" name="ingredientName" placeholder="Ingredient name" required style="flex: 2;">
            <input type="number" step="0.01" name="ingredientQuantity" placeholder="Qty" required style="width: 100px;">
            <input type="text" name="ingredientUnit" placeholder="Unit" required style="width: 120px;">
            <button type="button" onclick="removeIngredient(this)">Remove</button>
        `;

        container.appendChild(row);
        updateRemoveButtons();
    }

    function removeIngredient(button) {
        const container = document.getElementById("ingredients-container");
        button.parentElement.remove();
        updateRemoveButtons();
    }

    // Adds remove buttons only if there is more than one ingredient
    function updateRemoveButtons() {
        const container = document.getElementById("ingredients-container");
        const rows = container.getElementsByClassName("ingredient-row");

        for (let row of rows) {
            let removeBtn = row.querySelector("button");

            if (rows.length > 1) {
                if (!removeBtn) {
                    removeBtn = document.createElement("button");
                    removeBtn.type = "button";
                    removeBtn.innerText = "Remove";
                    removeBtn.onclick = function () {
                        removeIngredient(removeBtn);
                    };
                    row.appendChild(removeBtn);
                }
            } else {
                if (removeBtn) {
                    removeBtn.remove();
                }
            }
        }
    }
    window.onload = updateRemoveButtons;
</script>

<%@ include file="_footer.jspf" %>