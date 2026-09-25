// Admin product form: add / remove specification rows in the spec editor.
(function () {
    "use strict";

    function el(id) {
        return document.getElementById(id);
    }

    // Row removal works for both server-rendered rows and JS-created ones.
    document.addEventListener("click", function (event) {
        var removeBtn = event.target.closest("[data-remove-spec]");
        if (!removeBtn) {
            return;
        }
        var row = removeBtn.closest("tr");
        if (row) {
            row.remove();
        }
    });

    function addSpecRow(tbody, key, value) {
        var tr = document.createElement("tr");

        var tdKey = document.createElement("td");
        var keyInput = document.createElement("input");
        keyInput.type = "text";
        keyInput.name = "specKey";
        keyInput.className = "form-control";
        keyInput.maxLength = 100;
        keyInput.value = key || "";
        tdKey.appendChild(keyInput);

        var tdValue = document.createElement("td");
        var valueInput = document.createElement("input");
        valueInput.type = "text";
        valueInput.name = "specValue";
        valueInput.className = "form-control";
        valueInput.maxLength = 500;
        valueInput.value = value || "";
        tdValue.appendChild(valueInput);

        var tdRemove = document.createElement("td");
        var removeBtn = document.createElement("button");
        removeBtn.type = "button";
        removeBtn.className = "btn btn-sm btn-outline-danger";
        removeBtn.setAttribute("data-remove-spec", "");
        removeBtn.setAttribute("aria-label", "Remove this specification");
        removeBtn.innerHTML = "&#10005;";
        tdRemove.appendChild(removeBtn);

        tr.appendChild(tdKey);
        tr.appendChild(tdValue);
        tr.appendChild(tdRemove);
        tbody.appendChild(tr);
    }

    function init() {
        var addBtn = el("addSpecBtn");
        var specEditor = el("specEditor");
        if (!addBtn || !specEditor) {
            return;
        }
        var tbody = specEditor.querySelector("tbody");
        if (!tbody) {
            return;
        }
        addBtn.addEventListener("click", function () {
            addSpecRow(tbody, "", "");
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();