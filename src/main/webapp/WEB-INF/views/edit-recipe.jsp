<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ include file="_header.jspf" %>

<c:if test="${not empty errorMessage}">
    <p class="error"><c:out value="${errorMessage}" /></p>
</c:if>

<section class="card">
    <h2>Edit Recipe</h2>

    <p>
        <a href="${pageContext.request.contextPath}/recipes/manage?tenant=${tenant}">
            <button type="button">Back to Manage Recipes</button>
        </a>
    </p>

    <form method="post" action="${pageContext.request.contextPath}/recipes/edit">
        <input type="hidden" name="tenant" value="${tenant}">
        <input type="hidden" name="recipeId" value="${recipe.id}">

        <p>
            <label>Recipe Name:</label><br>
            <input type="text" name="recipeName" required style="width: 100%;"
                   value="${recipe.name}">
        </p>

        <p>
            <label>Instructions:</label><br>
            <textarea name="instructions" rows="8" required style="width: 100%;"><c:out value="${recipe.instructions}" /></textarea>
        </p>

        <h3>Ingredients</h3>

        <div id="ingredients-container">
            <c:forEach var="ingredient" items="${recipe.ingredients}" varStatus="status">
                <div class="ingredient-row"
                     style="display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;">
                    <input type="text" name="ingredientName" placeholder="Ingredient name"
                           required style="flex: 2;" value="${ingredient.name}">

                    <input type="number" step="any" min="0" name="ingredientQuantity" placeholder="Qty"
                           required style="width: 100px;" value="${ingredient.quantity}">

                    <div class="unit-choice" role="radiogroup" aria-label="Ingredient Unit">
                        <input type="radio" id="unit-kg-${status.index}" name="unitDisplay${status.index}" value="kg"
                               ${ingredient.unit == 'kg' ? 'checked' : ''}
                               onchange="updateUnit(this, 'kg')">
                        <label for="unit-kg-${status.index}" class="unit-choice-button">kg</label>

                        <input type="radio" id="unit-liter-${status.index}" name="unitDisplay${status.index}" value="liters"
                               ${ingredient.unit == 'liters' ? 'checked' : ''}
                               onchange="updateUnit(this, 'liters')">
                        <label for="unit-liter-${status.index}" class="unit-choice-button">liter</label>
                    </div>

                    <input type="hidden" name="ingredientUnit" value="${ingredient.unit}">

                    <button type="button" onclick="removeIngredient(this)" style="margin-left: 8px;">Remove</button>
                </div>
            </c:forEach>
        </div>

        <p style="margin-top: 14px; margin-bottom: 16px;">
            <button type="button" onclick="addIngredient()">+ Add Ingredient</button>
        </p>

        <p>
            <button type="submit">Update Recipe</button>
        </p>
    </form>
</section>
<input type="hidden" id="ingredient-count" value="${empty recipe.ingredients ? 0 : fn:length(recipe.ingredients)}">
<script>
    let ingredientIndex = parseInt(document.getElementById("ingredient-count").value, 10);

    function updateUnit(radio, value) {
        const row = radio.closest(".ingredient-row");
        const hiddenUnit = row.querySelector("input[type='hidden'][name='ingredientUnit']");
        hiddenUnit.value = value;
    }

    function addIngredient() {
        const container = document.getElementById("ingredients-container");
        const row = document.createElement("div");
        row.className = "ingredient-row";
        row.style.display = "flex";
        row.style.gap = "10px";
        row.style.marginBottom = "16px";
        row.style.alignItems = "center";
        row.style.flexWrap = "wrap";

        const currentIndex = ingredientIndex++;

        row.innerHTML = `
            <input type="text" name="ingredientName" placeholder="Ingredient name" required style="flex: 2;">
            <input type="number" step="any" min="0" name="ingredientQuantity" placeholder="Qty" required style="width: 100px;">

            <div class="unit-choice" role="radiogroup" aria-label="Ingredient Unit">
                <input type="radio" id="unit-kg-\${currentIndex}" name="unitDisplay\${currentIndex}" value="kg" checked
                       onchange="updateUnit(this, 'kg')">
                <label for="unit-kg-\${currentIndex}" class="unit-choice-button">kg</label>

                <input type="radio" id="unit-liter-\${currentIndex}" name="unitDisplay\${currentIndex}" value="liters"
                       onchange="updateUnit(this, 'liters')">
                <label for="unit-liter-\${currentIndex}" class="unit-choice-button">liter</label>
            </div>

            <input type="hidden" name="ingredientUnit" value="kg">

            <button type="button" onclick="removeIngredient(this)" style="margin-left: 8px;">Remove</button>
        `;

        container.appendChild(row);
        updateRemoveButtons();
    }

    function removeIngredient(button) {
        button.parentElement.remove();
        updateRemoveButtons();
    }

    function updateRemoveButtons() {
        const rows = document.getElementsByClassName("ingredient-row");

        for (let row of rows) {
            let removeBtn = row.querySelector("button");

            if (rows.length > 1) {
                if (!removeBtn) {
                    removeBtn = document.createElement("button");
                    removeBtn.type = "button";
                    removeBtn.innerText = "Remove";
                    removeBtn.style.marginLeft = "8px";
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