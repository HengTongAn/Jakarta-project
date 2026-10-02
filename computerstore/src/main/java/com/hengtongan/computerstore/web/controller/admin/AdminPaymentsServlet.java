package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.config.PaymentConfig;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.repository.AppSettingsRepository;
import com.hengtongan.computerstore.infrastructure.payment.AbaPaywayClient;
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
 *
 * <h2>Why there is a connection test</h2>
 * {@link AbaPaywayClient} has never been run against a real ABA sandbox, so its
 * endpoint paths and the shape of its credentials are the least trustworthy part
 * of the payment stack. An operator who is about to switch simulation off has no
 * way to find that out before a customer does. The probe
 * ({@code action=test}) calls the status endpoint with a transaction id that
 * cannot exist, so it creates nothing, and reports what came back. Simulation
 * stays the default; the probe verifies a configuration, it does not enable one.
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
        // The probe is a separate action rather than a field on the settings form,
        // so pressing it cannot be mistaken for saving. It also takes no config
        // input: it must report on what is already stored, because "what I typed"
        // and "what the store will actually use" are the two things an operator
        // needs to tell apart, and a probe that saved first could never disagree.
        if ("test".equals(request.getParameter("action"))) {
            // Renders rather than redirecting. The probe reads stored settings and
            // changes nothing, so there is no state to be consistent with, and the
            // result is structured -- status code, latency, the gateway's own
            // message -- which a one-shot flash string would flatten. Pressing
            // refresh re-probes, which is what an operator debugging a connection
            // wants anyway.
            runConnectionTest(request, response);
            return;
        }

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

    /**
 * Probes the gateway and renders the page with the result attached.
 *
 * <p>Admin-only (the path is behind admin authorization), POST-only and
 * CSRF-checked, because it makes the server open an outbound socket using stored
 * credentials. Anything that can reach this endpoint can make the host issue an
 * authenticated request to a third party, which is worth a token.
 *
 * <p>The result is flashed as well as rendered, so it survives navigating away
 * and back. An operator who ran a probe, read half the answer and followed a link
 * should not have to re-run it to see what it said.
 */
private void runConnectionTest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AbaPaywayClient.Diagnosis diagnosis = new AbaPaywayClient().diagnose();

        request.setAttribute("probe", diagnosis);
        request.setAttribute("probeReachable", diagnosis.reachable());
        request.setAttribute("probeStatus", diagnosis.httpStatus());
        request.setAttribute("probeAccepted", diagnosis.accepted());
        request.setAttribute("probeMessage", diagnosis.message());
        request.setAttribute("probeLatency", diagnosis.latencyMs());

        // What came back, not whether we hoped it would. The status code and the
        // gateway's own words are the whole value of the probe.
        String summary = diagnosis.usable()
                ? "Gateway reachable and accepted the request (HTTP " + diagnosis.httpStatus()
                        + ", " + diagnosis.latencyMs() + " ms). " + diagnosis.message()
                : diagnosis.reachable()
                        ? "Gateway answered HTTP " + diagnosis.httpStatus() + " and refused the "
                        + "request. " + diagnosis.message()
                        : "No response from the gateway. " + diagnosis.message();
        if (diagnosis.usable()) {
            Flash.success(request, summary);
        } else if (diagnosis.reachable()) {
            Flash.warning(request, summary);
        } else {
            Flash.error(request, summary);
        }

        // Audited because it is the only outbound authenticated call an admin can
        // trigger on demand. The audit line records the outcome and never the
        // credentials or the raw body.
        AuditLogger.logAdminAction("PAYMENT_CONNECTION_TEST", sessionUsername(request), "payments",
                "reachable=" + diagnosis.reachable()
                        + " http=" + diagnosis.httpStatus()
                        + " accepted=" + diagnosis.accepted()
                        + " latencyMs=" + diagnosis.latencyMs());

        render(request, response);
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
