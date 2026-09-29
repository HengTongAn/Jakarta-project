// show / hide password buttons (eye icon)
// every .password-toggle button uses data-target = id of the password input
document.querySelectorAll(".password-toggle").forEach(function (button) {
    button.addEventListener("click", function () {
        var input = document.getElementById(button.getAttribute("data-target"));
        if (!input) {
            return;
        }
        var nowShowing = input.type === "text";
        input.type = nowShowing ? "password" : "text";

        var icon = button.querySelector("i");
        if (icon) {
            icon.className = nowShowing ? "bi bi-eye" : "bi bi-eye-slash";
        }
        button.setAttribute("aria-label", nowShowing ? "Show password" : "Hide password");
    });
});