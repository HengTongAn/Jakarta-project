// Admin product form: "auto-fill details from the web".
// Searches via /admin/products/webinfo?action=search, loads an official page
// (?action=fetch), and fills the detail fields + spec table as EDITABLE
// suggestions. Every value from the web is written via .value / .textContent
// (never innerHTML), so external content cannot execute in the admin page.
(function () {
    "use strict";
    var CTX = window.CTXPATH || "";

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
        var host = document.querySelector("[data-product-autofill]");
        if (!host) {
            return;
        }

        var queryInput = el("webInfoQuery");
        var searchBtn = el("webInfoSearchBtn");
        var addBtn = el("addSpecBtn");
        var status = el("webInfoStatus");
        var results = el("webInfoResults");
        var specEditor = el("specEditor");
        if (!queryInput || !searchBtn || !status || !results) {
            return;
        }
        var tbody = specEditor ? specEditor.querySelector("tbody") : null;

        function setStatus(message, isError) {
            status.textContent = message;
            status.className = "mt-2" + (isError ? " text-danger fw-semibold" : " text-muted");
        }

        function renderResults(items) {
            results.innerHTML = "";
            if (!items || !items.length) {
                results.innerHTML = "";
                setStatus("No results found. Try a more specific query (e.g. add the brand and model).", true);
                return;
            }
            setStatus(items.length + (items.length === 1 ? " result." : " results.") +
                " Pick the official manufacturer page.", false);
            items.forEach(function (item) {
                var row = document.createElement("div");
                row.className = "border rounded p-2 mb-2 d-flex justify-content-between align-items-start gap-2 flex-wrap";

                var copy = document.createElement("div");
                copy.className = "flex-grow-1 min-w-0";
                var title = document.createElement("div");
                title.className = "fw-semibold small";
                title.textContent = item.title || "(untitled)";
                var meta = document.createElement("div");
                meta.className = "text-muted small text-break";
                meta.textContent = item.url || "";
                var snippet = document.createElement("div");
                snippet.className = "small";
                snippet.textContent = item.snippet || "";
                copy.appendChild(title);
                copy.appendChild(meta);
                copy.appendChild(snippet);

                var fillBtn = document.createElement("button");
                fillBtn.type = "button";
                fillBtn.className = "btn btn-sm btn-outline-brand flex-shrink-0";
                fillBtn.textContent = "Fetch & fill";
                fillBtn.addEventListener("click", function () {
                    fetchDetails(item.url);
                });

                row.appendChild(copy);
                row.appendChild(fillBtn);
                results.appendChild(row);
            });
        }

        function fetchDetails(url) {
            if (!url) {
                setStatus("That result has no usable URL.", true);
                return;
            }
            results.innerHTML = "";
            setStatus("Fetching the official page (usually a few seconds)…", false);
            fetch(CTX + "/admin/products/webinfo?action=fetch&url=" + encodeURIComponent(url), {
                headers: { "Accept": "application/json" },
                credentials: "same-origin"
            })
                .then(function (response) {
                    if (!response.ok) {
                        return response.json().then(function (data) {
                            throw new Error(data.error || "Could not load that page.");
                        });
                    }
                    return response.json();
                })
                .then(apply)
                .catch(function (err) {
                    setStatus(err.message || "Could not load that page.", true);
                });
        }

        function apply(data) {
            data = data || {};
            var highlightsField = el("highlightsField");
            var boxField = el("boxContentsField");
            var warrantyField = el("warrantyInfoField");
            var sourceField = el("sourceUrlField");

            // Basic fields are only suggested when still empty, so they never
            // clobber a name/description the admin already typed.
            var nameField = el("name");
            if (nameField && !nameField.value.trim() && data.pageTitle) {
                nameField.value = productNameFromTitle(data.pageTitle);
            }
            var descriptionField = el("description");
            if (descriptionField && !descriptionField.value.trim() && data.description) {
                descriptionField.value = data.description;
            }
            if (highlightsField && data.highlights && data.highlights.length) {
                highlightsField.value = data.highlights.slice(0, 8).join("\n");
            }
            if (boxField && data.boxContents) {
                boxField.value = data.boxContents;
            }
            if (warrantyField && data.warrantyInfo) {
                warrantyField.value = data.warrantyInfo;
            }
            if (sourceField && data.sourceUrl) {
                sourceField.value = data.sourceUrl;
            }
            if (tbody) {
                tbody.innerHTML = "";
                var specs = data.specs || [];
                // Manufacturer part number gets a dedicated, editable first row
                // unless the page's specs already carried it under its real key.
                if (data.manufacturerPart && !specs.some(function (spec) {
                        return isPartNumberKey(spec.key);
                    })) {
                    addSpecRow(tbody, "Manufacturer part number", data.manufacturerPart);
                }
                (specs.length ? specs : [{}]).forEach(function (spec) {
                    addSpecRow(tbody, spec.key, spec.value);
                });
            }
            var message = "Details filled as editable suggestions — review and adjust them before saving.";
            if (data.manufacturerPart) {
                message += " Part number: " + data.manufacturerPart;
            }
            setStatus(message, false);
        }

        /**
         * "ASUS ROG Strix G16 (2024) | ASUS" -> "ASUS ROG Strix G16 (2024)".
         * Splits on the common "|", "–" and "-" title separators and keeps the
         * longest fragment (a site name is usually the short one). Capped to the
         * name column's 200-char limit; a suggestion the admin can edit.
         */
        function productNameFromTitle(title) {
            title = (title || "").replace(/\s+/g, " ").trim();
            if (!title) {
                return "";
            }
            var parts = title.split(/\s*[|\u2013-]\s*/).filter(function (p) {
                return p.length > 0;
            });
            var best = parts[0] || title;
            if (parts.length > 1) {
                parts.forEach(function (p) {
                    if (p.length > best.length) {
                        best = p;
                    }
                });
            }
            return best.length > 200 ? best.substring(0, 200) : best;
        }

        /** Mirrors the backend's part-number aliases (WebProductInfoService). */
        function isPartNumberKey(key) {
            var lower = (key || "").toLowerCase();
            var aliases = ["part number", "part no.", "part #", "model number",
                "model no.", "mpn", "sku", "stock code", "product code",
                "item number", "item no.", "product number", "p/n"];
            return aliases.some(function (alias) {
                return lower.indexOf(alias) !== -1;
            });
        }

        function searchWeb(query) {
            query = (query || "").trim();
            if (!query) {
                setStatus("Type a search query first (e.g. brand + model + specifications).", true);
                return;
            }
            results.innerHTML = "";
            setStatus("Searching the web…", false);
            fetch(CTX + "/admin/products/webinfo?action=search&q=" + encodeURIComponent(query), {
                headers: { "Accept": "application/json" },
                credentials: "same-origin"
            })
                .then(function (response) {
                    if (!response.ok) {
                        return response.json().then(function (data) {
                            throw new Error(data.error || "Search failed.");
                        });
                    }
                    return response.json();
                })
                .then(function (data) {
                    renderResults(data.results || []);
                })
                .catch(function (err) {
                    setStatus(err.message || "Search failed.", true);
                });
        }

        searchBtn.addEventListener("click", function () {
            searchWeb(queryInput.value);
        });
        queryInput.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                searchWeb(queryInput.value);
            }
        });
        if (addBtn && tbody) {
            addBtn.addEventListener("click", function () {
                addSpecRow(tbody, "", "");
            });
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();