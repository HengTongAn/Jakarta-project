package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.config.PaymentConfig;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.repository.AppSettingsRepository;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ABA Payway configuration:
 *   GET  /admin/payments -> configuration form
 *   POST /admin/payments -> save
 * <p>
 * This is the whole activation story: an operator with no credentials leaves
 * {@code enabled} off and the storefront is unchanged; an operator with a
 * merchant account pastes three values, switches simulate off and the same
 * checkout charges real money through ABA. No rebuild, no restart.
 * <p>
 * The API secret is write-only. It is never rendered back into the form and
 * never placed in a request attribute; the field stays blank with a placeholder
 * when one is already stored, and a blank submission leaves it untouched.
 */
@WebServlet("/admin/payments")
public class AdminPaymentsServlet extends BaseServlet {

    private static final String VIEW = "admin/payments.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AppSettingsRepository settings = new AppSettingsRepository();
        Map<String, String> values = new LinkedHashMap<>();

        values.put(PaymentConfig.KEY_ENABLED, switchOn(request, "enabled") ? "true" : "false");
        values.put(PaymentConfig.KEY_SIMULATE, switchOn(request, "simulate") ? "true" : "false");
        values.put(PaymentConfig.KEY_CARD_ENABLED, switchOn(request, "cardEnabled") ? "true" : "false");
        values.put(PaymentConfig.KEY_CARD_SIMULATE, switchOn(request, "cardSimulate") ? "true" : "false");
        values.put(PaymentConfig.KEY_MERCHANT_ID, trim(request, "merchantId"));
        values.put(PaymentConfig.KEY_API_URL, trim(request, "apiUrl"));
        values.put(PaymentConfig.KEY_USERNAME, trim(request, "username"));
        values.put(PaymentConfig.KEY_SHOP_NAME, trim(request, "shopName"));
        values.put(PaymentConfig.KEY_CURRENCY, trim(request, "currency").toUpperCase());

        String currency = values.get(PaymentConfig.KEY_CURRENCY);
        if (!currency.isEmpty() && !currency.matches("[A-Z]{3}")) {
            flashError(request, "Currency must be a three-letter code such as USD or KHR.");
            render(request, response);
            return;
        }

        // Blank means "leave the stored secret alone" so it never has to be
        // echoed back through the browser to be preserved.
        String secret = trim(request, "secret");
        if (!secret.isEmpty()) {
            values.put(PaymentConfig.KEY_SECRET, secret);
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            settings.save(entry.getKey(), entry.getValue());
        }
        PaymentConfig.invalidate();

        AuditLogger.logAdminAction("PAYMENT_CONFIG_UPDATE", sessionUsername(request), "payments",
                "aba[enabled=" + values.get(PaymentConfig.KEY_ENABLED)
                        + " simulate=" + values.get(PaymentConfig.KEY_SIMULATE)
                        + " secret=" + (secret.isEmpty() ? "unchanged" : "rotated") + "]"
                        + " card[enabled=" + values.get(PaymentConfig.KEY_CARD_ENABLED)
                        + " simulate=" + values.get(PaymentConfig.KEY_CARD_SIMULATE) + "]");
        Flash.success(request, "Payment settings saved. They take effect on the next request.");
        redirect(request, response, "/admin/payments");
    }

    private void render(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Only the non-secret values are exposed; the secret is never read into
        // a request attribute.
        request.setAttribute("enabled", PaymentConfig.isEnabled());
        request.setAttribute("simulate", PaymentConfig.isSimulate());
        request.setAttribute("merchantId", PaymentConfig.merchantId());
        request.setAttribute("apiUrl", PaymentConfig.apiUrl());
        request.setAttribute("username", PaymentConfig.username());
        request.setAttribute("shopName", PaymentConfig.shopName());
        request.setAttribute("currency", PaymentConfig.currency());
        request.setAttribute("secretSet", !PaymentConfig.secret().isBlank());
        request.setAttribute("ready", PaymentConfig.isReady());
        request.setAttribute("blockingReason", PaymentConfig.blockingReason());
        request.setAttribute("cardEnabled", PaymentConfig.isCardEnabled());
        request.setAttribute("cardSimulate", PaymentConfig.isCardSimulated());
        request.setAttribute("cardReady", PaymentConfig.isCardReady());
        request.setAttribute("cardBlockingReason", PaymentConfig.cardBlockingReason());
        forward(request, response, VIEW);
    }

    /**
     * Reads a switch whose form pairs a checkbox ({@code value="on"}) with a
     * hidden {@code value="off"}, so both states are always submitted.
     * <p>
     * Every submitted value is examined rather than just the first, so the
     * result does not depend on where the two controls sit in the markup. A
     * missing parameter is treated as off: with the hidden field in place it
     * means the form did not come from this page, and the safe reading of a
     * half-configured payment gateway is the one that cannot charge a customer.
     */
    private boolean switchOn(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && "on".equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private String trim(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    private String sessionUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("user");
        if (user instanceof User) {
            return ((User) user).getUsername();
        }
        return "UNKNOWN";
    }
}
