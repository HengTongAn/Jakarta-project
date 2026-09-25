// Shared UX layer: toasts, submit spinners, scroll-to-top, cart badge refresh.
(function () {
    "use strict";
    var CTX = window.CTXPATH || "";

    /* ---------- toasts (flash messages -> auto-dismissing toasts) ---------- */
    function dismissToast(toast) {
        if (!toast || toast.classList.contains("is-leaving")) {
            return;
        }
        toast.classList.add("is-leaving");
        window.setTimeout(function () {
            toast.remove();
        }, 300);
    }

    function scheduleDismiss(toast, ms) {
        window.setTimeout(function () {
            dismissToast(toast);
        }, ms || 4500);
    }

    /* ---------- comfortable motion: reveal, progress, and navigation feedback ---------- */
    var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    function initMotion() {
        var revealTargets = document.querySelectorAll(
            ".store-section, .benefits-strip, .promo-banner, .why-section, .faq-section, " +
            ".support-cta, .product-card, .card-hover, .stats-card, .mail-row, .chat-conv, " +
            ".category-tile, .build-card, .why-card, .review-card"
        );

        revealTargets.forEach(function (element, index) {
            element.setAttribute("data-reveal", "");
            element.style.setProperty("--reveal-delay", (index % 6) * 55 + "ms");
        });

        if (reduceMotion || !("IntersectionObserver" in window)) {
            revealTargets.forEach(function (element) { element.classList.add("is-visible"); });
        } else {
            var observer = new IntersectionObserver(function (entries, currentObserver) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        entry.target.classList.add("is-visible");
                        currentObserver.unobserve(entry.target);
                    }
                });
            }, { threshold: 0.1, rootMargin: "0px 0px -36px 0px" });
            revealTargets.forEach(function (element) { observer.observe(element); });
        }

        var progress = document.getElementById("scrollProgress");
        var navbar = document.querySelector(".navbar");
        var updateScrollUI = function () {
            var documentHeight = document.documentElement.scrollHeight - window.innerHeight;
            var percent = documentHeight > 0 ? Math.round((window.scrollY / documentHeight) * 100) : 0;
            if (progress) {
                progress.style.width = Math.min(100, Math.max(0, percent)) + "%";
                progress.setAttribute("aria-valuenow", String(percent));
            }
            if (navbar) {
                navbar.classList.toggle("is-scrolled", window.scrollY > 18);
            }
        };
        window.addEventListener("scroll", updateScrollUI, { passive: true });
        updateScrollUI();

        document.addEventListener("click", function (event) {
            var target = event.target.closest(".btn, .category-tile, .build-card, .pill");
            if (!target || reduceMotion || target.getAttribute("data-no-ripple") !== null) {
                return;
            }
            var ripple = document.createElement("span");
            ripple.className = "motion-ripple";
            var rect = target.getBoundingClientRect();
            ripple.style.left = (event.clientX - rect.left) + "px";
            ripple.style.top = (event.clientY - rect.top) + "px";
            target.appendChild(ripple);
            window.setTimeout(function () { ripple.remove(); }, 550);
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initMotion);
    } else {
        initMotion();
    }

    // Messages rendered server-side (survive disable-JS; JS then animates them away).
    var stack = document.getElementById("toastStack");
    if (stack) {
        stack.querySelectorAll(".ux-toast").forEach(function (toast) {
            scheduleDismiss(toast);
            var close = toast.querySelector("[data-dismiss-toast]");
            if (close) {
                close.addEventListener("click", function () {
                    dismissToast(toast);
                });
            }
        });
    }

    // Publish a toast on demand (used by in-page actions like quick add).
    function publishToast(message, type) {
        type = type || "success";
        var icons = { success: "bi-check-circle-fill", danger: "bi-x-circle-fill", warning: "bi-exclamation-triangle-fill" };
        var host = document.getElementById("toastStack");
        if (!host) {
            host = document.createElement("div");
            host.id = "toastStack";
            host.className = "toast-stack";
            document.body.appendChild(host);
        }
        var toast = document.createElement("div");
        toast.className = "ux-toast ux-toast-" + type;
        toast.setAttribute("role", "status");
        toast.setAttribute("aria-live", "polite");
        toast.innerHTML = '<i class="bi ' + (icons[type] || icons.success) + '" aria-hidden="true"></i>' +
            '<span class="ux-toast-msg"></span>' +
            '<button type="button" class="btn-close ux-toast-close" data-dismiss-toast aria-label="Close"></button>';
        toast.querySelector(".ux-toast-msg").textContent = message;
        host.appendChild(toast);
        scheduleDismiss(toast);
    }

    /* ---------- submit spinners ---------- */
    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!form || form.getAttribute("data-no-spinner") !== null) {
            return;
        }
        var pendingConfirmation = form.querySelector("[data-confirm]");
        if (pendingConfirmation && pendingConfirmation.getAttribute("data-confirmed") !== "true") {
            return;
        }
        window.setTimeout(function () {
            form.querySelectorAll("button[type=submit]").forEach(function (button) {
                if (button.disabled) {
                    return;
                }
                button.disabled = true;
                button.setAttribute("aria-busy", "true");
                var label = button.getAttribute("data-loading") || button.textContent.replace(/^\s+|\s+$/g, "") || "Please wait…";
                button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' + label;
            });
        }, 0);
    });

    /* ---------- scroll-to-top ---------- */
    var topButton = document.getElementById("scrollTop");
    if (topButton) {
        var onScroll = function () {
            topButton.classList.toggle("show", window.scrollY > 420);
        };
        window.addEventListener("scroll", onScroll, { passive: true });
        onScroll();
        topButton.addEventListener("click", function () {
            window.scrollTo({ top: 0, behavior: "smooth" });
            topButton.focus({ preventScroll: true });
        });
    }

    /* ---------- cart badge refresh ---------- */
    function refreshCartCount(done) {
        fetch(CTX + "/cart/count", { headers: { "Accept": "application/json" }, credentials: "same-origin" })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("cart count unavailable");
                }
                return response.json();
            })
            .then(function (data) {
                var badge = document.getElementById("cartCountBadge");
                if (badge) {
                    var n = Number(data.count) || 0;
                    badge.textContent = n;
                    badge.classList.toggle("d-none", n === 0);
                }
                if (done) { done(null, Number(data.count)); }
            })
            .catch(function (err) {
                if (done) { done(err); }
            });
    }

    /* ---------- quick add to cart (AJAX, graceful no-JS fallback) ---------- */
    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!(form instanceof HTMLFormElement) || form.getAttribute("data-quickadd") === null) {
            return;
        }
        event.preventDefault();
        var addButton = form.querySelector("button[type=submit]");
        var originalHtml = addButton ? addButton.innerHTML : null;
        if (addButton) {
            addButton.disabled = true;
            addButton.classList.add("btn-spinner");
            addButton.setAttribute("aria-busy", "true");
            var loadingLabel = addButton.getAttribute("data-loading") || "Adding…";
            addButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' + loadingLabel;
        }
        var body = new URLSearchParams(new FormData(form));
        body.set("ajax", "1");
        fetch(form.action, {
            method: "POST",
            body: body,
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        })
            .then(function (response) {
                var contentType = response.headers.get("content-type") || "";
                if (contentType.indexOf("application/json") === -1) {
                    // Guest redirected to login HTML, or auth filter returned a page.
                    window.location.href = CTX + "/login";
                    return null;
                }
                if (!response.ok) {
                    return response.json().then(function (data) {
                        throw new Error(data.message || "Could not add item");
                    });
                }
                return response.json();
            })
            .then(function (data) {
                if (!data) {
                    return;
                }
                window.UX.toast("Added to cart", "success");
                window.UX.refreshCartCount();
                if (addButton) {
                    addButton.disabled = false;
                    addButton.classList.remove("btn-spinner");
                    addButton.removeAttribute("aria-busy");
                    if (originalHtml !== null) {
                        addButton.innerHTML = originalHtml;
                    }
                }
            })
            .catch(function (err) {
                if (addButton) {
                    addButton.disabled = false;
                    addButton.classList.remove("btn-spinner");
                    addButton.removeAttribute("aria-busy");
                    if (originalHtml !== null) {
                        addButton.innerHTML = originalHtml;
                    }
                }
                window.UX.toast(err.message || "Could not add item", "danger");
            });
    });

    /* ---------- review form submission ---------- */
    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!(form instanceof HTMLFormElement) || form.id !== "writeReviewForm") {
            return;
        }
        var submitButton = form.querySelector("button[type=submit]");
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.setAttribute("aria-busy", "true");
            var originalText = submitButton.textContent;
            submitButton.textContent = submitButton.getAttribute("data-loading") || "Submitting…";
        }
    });

    /* ---------- star rating interaction ---------- */
    document.addEventListener("change", function (event) {
        var input = event.target;
        if (input.type === "radio" && input.name === "rating") {
            var starInput = input.closest(".star-input");
            if (starInput) {
                var labels = starInput.querySelectorAll(".star-label");
                var rating = parseInt(input.value, 10);
                labels.forEach(function (label, index) {
                    if (index < rating) {
                        label.style.color = "#f59e0b";
                    } else {
                        label.style.color = "#cbd5e1";
                    }
                });
            }
        }
    });

    document.addEventListener("mouseover", function (event) {
        var label = event.target.closest(".star-label");
        if (label) {
            var starOption = label.closest(".star-option");
            if (starOption) {
                var starInput = starOption.closest(".star-input");
                if (starInput) {
                    var labels = starInput.querySelectorAll(".star-label");
                    var index = Array.from(labels).indexOf(label);
                    labels.forEach(function (l, i) {
                        if (i <= index) {
                            l.style.color = "#f59e0b";
                        } else {
                            l.style.color = "#cbd5e1";
                        }
                    });
                }
            }
        }
    });

    document.addEventListener("mouseout", function (event) {
        var starInput = event.target.closest(".star-input");
        if (starInput) {
            var checkedInput = starInput.querySelector("input:checked");
            if (checkedInput) {
                var rating = parseInt(checkedInput.value, 10);
                var labels = starInput.querySelectorAll(".star-label");
                labels.forEach(function (label, index) {
                    if (index < rating) {
                        label.style.color = "#f59e0b";
                    } else {
                        label.style.color = "#cbd5e1";
                    }
                });
            } else {
                var labels = starInput.querySelectorAll(".star-label");
                labels.forEach(function (label) {
                    label.style.color = "#cbd5e1";
                });
            }
        }
    });

    /* ---------- generic quantity steppers ---------- */
    document.addEventListener("click", function (event) {
        var button = event.target.closest("[data-step]");
        if (!button) {
            return;
        }
        var input = document.getElementById(button.getAttribute("data-target"));
        if (!input) {
            return;
        }
        event.preventDefault();
        var min = parseInt(input.min, 10) || 1;
        var max = parseInt(input.max, 10) || 999999;
        var value = parseInt(input.value, 10);
        if (isNaN(value)) {
            value = min;
        }
        var step = parseInt(button.getAttribute("data-step"), 10) || 1;
        input.value = Math.min(max, Math.max(min, value + step));
        var form = input.closest("form");
        if (form && button.getAttribute("data-submit") === "true") {
            if (form.requestSubmit) {
                form.requestSubmit();
            } else {
                form.submit();
            }
        }
    });

    /* ---------- inline confirmation for dangerous actions ---------- */
    document.addEventListener("click", function (event) {
        var link = event.target.closest("a[data-confirm]");
        if (!link) {
            return;
        }
        event.preventDefault();
        if (link.getAttribute("data-confirmed") === "true") {
            var href = link.getAttribute("href");
            if (!href) {
                return;
            }
            if (link.getAttribute("data-noop") === null) {
                window.location.href = href;
            }
            return;
        }
        link.setAttribute("data-confirmed", "true");
        var original = link.textContent.trim();
        link.textContent = "Confirm?";
        link.classList.remove("btn-outline-danger");
        link.classList.add("btn-danger", "confirm-pulse");
        window.setTimeout(function () {
            link.textContent = original;
            link.classList.add("btn-outline-danger");
            link.classList.remove("btn-danger", "confirm-pulse");
            link.removeAttribute("data-confirmed");
        }, 2500);
    });

    /* ---------- inline confirmation for buttons (dangerous submits) ---------- */
    document.addEventListener("submit", function (event) {
        var form = event.target;
        var danger = form.querySelector("[data-confirm]");
        if (!danger) {
            return;
        }
        if (danger.getAttribute("data-confirmed") === "true") {
            return;
        }
        event.preventDefault();
        danger.setAttribute("data-confirmed", "true");
        var original = danger.textContent.trim();
        danger.textContent = "Confirm?";
        danger.classList.add("btn-danger", "confirm-pulse");
        danger.setAttribute("aria-live", "assertive");
        var cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "btn btn-outline-secondary btn-sm ms-1";
        cancel.textContent = "Cancel";
        cancel.addEventListener("click", function () {
            danger.textContent = original;
            danger.classList.remove("btn-danger", "confirm-pulse");
            danger.removeAttribute("data-confirmed");
            cancel.remove();
        });
        danger.parentNode.appendChild(cancel);
    });

    /* ---------- admin: sortable + filterable tables ---------- */
    function cellValue(cell) {
        // Guards against rows that use a single <td colspan="N"> message
        // (e.g. an empty-table placeholder): those cells only exist at index 0.
        if (!cell) {
            return { type: "str", key: "" };
        }
        var numeric = cell.getAttribute("data-sort");
        if (numeric !== null) {
            var n = parseFloat(numeric.replace(/[^0-9.\-]/g, ""));
            return { type: "num", key: isNaN(n) ? -1 : n };
        }
        var text = (cell.textContent || "").trim();
        return { type: "str", key: text.toLowerCase() };
    }

    function initSortableTables() {
        document.querySelectorAll("table[data-sortable]").forEach(function (table) {
            var head = table.querySelector("thead");
            if (!head) {
                return;
            }
            var rows = table.querySelectorAll("tbody tr");
            head.querySelectorAll("th").forEach(function (th, index) {
                if (th.getAttribute("data-nosort") !== null) {
                    return;
                }
                th.classList.add("sortable");
                th.setAttribute("tabindex", "0");
                th.setAttribute("role", "button");
                th.setAttribute("aria-sort", "none");
                th.title = "Sort by this column";

                function toggleSort() {
                    var state = th.getAttribute("aria-sort");
                    var order = state === "ascending" ? "descending" : "ascending";
                    head.querySelectorAll("th").forEach(function (header) {
                        header.setAttribute("aria-sort", "none");
                        var arrow = header.querySelector(".sort-arrow");
                        if (arrow) {
                            arrow.remove();
                        }
                    });
                    var list = Array.prototype.slice.call(document.querySelectorAll("table[data-sortable] tbody tr"));
                    var idx = Math.max(0, index);
                    list.sort(function (a, b) {
                        var va = cellValue(a.cells[idx]);
                        var vb = cellValue(b.cells[idx]);
                        var cmp;
                        if (va.type === "num" && vb.type === "num") {
                            cmp = va.key - vb.key;
                        } else {
                            cmp = (va.key < vb.key) ? -1 : (va.key > vb.key ? 1 : 0);
                        }
                        return order === "ascending" ? cmp : -cmp;
                    });
                    var tbody = table.querySelector("tbody");
                    list.forEach(function (row) {
                        tbody.appendChild(row);
                    });
                    th.setAttribute("aria-sort", order === "ascending" ? "ascending" : "descending");
                    var arrow = document.createElement("i");
                    arrow.className = "bi sort-arrow " + (order === "ascending" ? "bi-arrow-up" : "bi-arrow-down");
                    arrow.setAttribute("aria-hidden", "true");
                    th.appendChild(arrow);
                }

                th.addEventListener("click", toggleSort);
                th.addEventListener("keydown", function (event) {
                    if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        toggleSort();
                    }
                });
            });

            if (table.hasAttribute("data-filter-target")) {
                var input = document.getElementById(table.getAttribute("data-filter-target"));
                var countEl = table.hasAttribute("data-count")
                    ? document.getElementById(table.getAttribute("data-count"))
                    : null;
                if (input) {
                    input.addEventListener("input", function () {
                        var query = input.value.trim().toLowerCase();
                        var visible = 0;
                        table.querySelectorAll("tbody tr").forEach(function (row) {
                            var match = query === "" ||
                                Array.prototype.some.call(row.cells, function (cell) {
                                    return (cell.textContent || "").toLowerCase().indexOf(query) !== -1;
                                });
                            row.style.display = match ? "" : "none";
                            if (match) {
                                visible++;
                            }
                        });
                        if (countEl) {
                            countEl.textContent = visible;
                        }
                    });
                }
            }
        });
    }

    function initCheckoutPayment() {
        var methods = document.querySelectorAll(".payment-method");
        var cardPanel = document.getElementById("cardPaymentPanel");
        var cashPanel = document.querySelector(".cash-payment-panel");
        if (!methods.length || !cardPanel || !cashPanel) {
            return;
        }
        methods.forEach(function (method) {
            var input = method.querySelector("input[type='radio']");
            if (!input) {
                return;
            }
            input.addEventListener("change", function () {
                methods.forEach(function (item) {
                    item.classList.toggle("is-selected", item.querySelector("input") === input);
                });
                var cardSelected = input.value === "card";
                cardPanel.classList.toggle("d-none", !cardSelected);
                cashPanel.classList.toggle("d-none", cardSelected);
            });
        });
    }

    function initCheckoutFormatting() {
        var cardNumber = document.getElementById("cardNumber");
        var expiry = document.getElementById("cardExpiry");
        if (cardNumber) {
            cardNumber.addEventListener("input", function () {
                var value = cardNumber.value.replace(/\D/g, "").slice(0, 16);
                cardNumber.value = value.replace(/(.{4})/g, "$1 ").trim();
            });
        }
        if (expiry) {
            expiry.addEventListener("input", function () {
                var value = expiry.value.replace(/\D/g, "").slice(0, 4);
                expiry.value = value.length > 2 ? value.slice(0, 2) + "/" + value.slice(2) : value;
            });
        }
    }

    function initImageUploads() {
        var allowedTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];
        var maxBytes = 5 * 1024 * 1024;

        document.querySelectorAll("[data-image-input]").forEach(function (input) {
            var target = document.querySelector(input.getAttribute("data-preview-target"));
            var status = document.querySelector(input.getAttribute("data-image-status"));
            if (!target) {
                return;
            }
            var defaultStatus = status ? status.textContent : "";

            input.addEventListener("change", function () {
                var file = input.files && input.files[0];
                if (input._previewUrl) {
                    URL.revokeObjectURL(input._previewUrl);
                    input._previewUrl = null;
                }
                if (!file) {
                    if (status) { status.textContent = defaultStatus; }
                    return;
                }
                if (allowedTypes.indexOf(file.type) === -1) {
                    input.value = "";
                    if (status) { status.textContent = "Please choose a JPG, PNG, GIF, or WEBP image."; }
                    window.UX.toast("Please choose a supported image file", "warning");
                    return;
                }
                if (file.size > maxBytes) {
                    input.value = "";
                    if (status) { status.textContent = "This image is larger than 5MB. Please choose a smaller file."; }
                    window.UX.toast("Image must be 5MB or smaller", "warning");
                    return;
                }

                var url = URL.createObjectURL(file);
                input._previewUrl = url;
                target.classList.add("is-previewing");
                if (target.tagName === "IMG") {
                    target.src = url;
                } else if (target.classList.contains("admin-avatar-preview")) {
                    target.classList.remove("admin-avatar-placeholder");
                    target.innerHTML = '<img src="' + url + '" alt="Selected profile image">';
                } else if (target.classList.contains("product-image-preview")) {
                    target.innerHTML = '<img src="' + url + '" alt="Selected product image"><span class="image-preview-label">Ready to upload</span>';
                } else {
                    target.innerHTML = '<img src="' + url + '" alt="Selected profile image">';
                }
                window.setTimeout(function () { target.classList.remove("is-previewing"); }, 180);
                if (status) {
                    status.textContent = file.name + " selected — " + Math.max(1, Math.round(file.size / 1024)) + " KB. Ready to upload.";
                }
            });
        });
    }

    function initPasswordConfirmation() {
        document.querySelectorAll("form[data-password-confirm]").forEach(function (form) {
            var password = form.querySelector("[name='newPassword']");
            var confirmation = form.querySelector("[name='confirmPassword']");
            if (!password || !confirmation) {
                return;
            }
            var validate = function () {
                var mismatch = confirmation.value !== "" && password.value !== confirmation.value;
                confirmation.setCustomValidity(mismatch ? "Passwords do not match." : "");
                confirmation.classList.toggle("is-invalid", mismatch);
            };
            password.addEventListener("input", validate);
            confirmation.addEventListener("input", validate);
        });
    }

    function initPageEnhancements() {
        initSortableTables();
        initCheckoutPayment();
        initCheckoutFormatting();
        initImageUploads();
        initPasswordConfirmation();
        // Cart count filter is disabled (pool pressure); refresh badge on load.
        if (document.body && document.body.getAttribute("data-userid") && document.getElementById("cartCountBadge")) {
            refreshCartCount();
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initPageEnhancements);
    } else {
        initPageEnhancements();
    }

    window.UX = window.UX || {};
    window.UX.toast = publishToast;
    window.UX.refreshCartCount = refreshCartCount;
})();
