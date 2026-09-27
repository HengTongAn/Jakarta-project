package com.hengtongan.computerstore.infrastructure.payment;

import com.hengtongan.computerstore.core.config.PaymentConfig;
import com.hengtongan.computerstore.util.json.MiniJson;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the ABA Payway merchant e-commerce API.
 *
 * <h2>UNVERIFIED AGAINST THE LIVE GATEWAY</h2>
 * The endpoint paths, the {@code Authorization} scheme and the response field
 * names below are taken from publicly circulated ABA Payway documentation and
 * have <strong>not</strong> been exercised against a real ABA sandbox, because
 * this build has no merchant credentials. Two specific known ambiguities are
 * handled defensively rather than guessed at:
 * <ul>
 *   <li>Field names appear in the wild as both {@code transId} and
 *       {@code TRANS_ID}; every field is read through
 *       {@link MiniJson#firstString(Map, String...)} with both spellings.</li>
 *   <li>The transaction status endpoint path is the least certain part of this
 *       class.</li>
 * </ul>
 * <b>Verify all of it against ABA's official developer documentation before
 * enabling live mode.</b> {@link PaymentConfig#isReady()} will happily let an
 * operator switch this on, and it will fail at the first real charge if the
 * paths are wrong.
 *
 * <h2>Simulate mode</h2>
 * When {@link PaymentConfig#isSimulate()} is set (the default), no socket is
 * opened at all. A realistic transaction id and QR payload are synthesised
 * locally and the same order state machine runs, so a demo exercises the full
 * flow with no credentials and no risk of touching a live bank.
 */
public class AbaPaywayClient {

    /** Outcome of asking the gateway to create a payment. */
    public record PrecreateResult(boolean ok, String transactionId, String qrImage,
                                  String abaPhone, String message) {

        public static PrecreateResult failed(String message) {
            return new PrecreateResult(false, null, null, null, message);
        }
    }

    /** Outcome of asking the gateway whether the money arrived. */
    public record StatusResult(boolean ok, String status, String transactionId, String message) {
    }

    private static final String PRECREATE_PATH = "/api/v1/checkout/merchant/ecommerce/precreate";
    private static final String STATUS_PATH = "/api/v1/checkout/merchant/ecommerce/transaction-status";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Creates a payment and returns the QR the customer scans.
     *
     * @param orderId    the store's order id, echoed back as ABA's {@code orderId}
     * @param amount     charge amount, in {@link PaymentConfig#currency()}
     * @param description line shown in the customer's ABA app
     * @param baseUrl    absolute origin of this deployment, for the return URLs
     */
    public PrecreateResult precreate(int orderId, BigDecimal amount, String description, String baseUrl) {
        if (PaymentConfig.isSimulate()) {
            return simulatedPrecreate(orderId, amount);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        body.put("currency", PaymentConfig.currency());
        body.put("description", description);
        body.put("orderId", String.valueOf(orderId));
        body.put("shopId", PaymentConfig.merchantId());
        body.put("shopName", PaymentConfig.shopName());
        body.put("successUrl", baseUrl + "/payment/aba/return?order=" + orderId);
        body.put("cancelUrl", baseUrl + "/payment/aba/return?order=" + orderId);
        body.put("returnUrl", baseUrl + "/payment/aba/return?order=" + orderId);
        body.put("expireTime", 15);
        body.put("paymentMethod", "abapay");

        Map<String, Object> response = post(PRECREATE_PATH, body);
        if (response == null) {
            return PrecreateResult.failed("Could not reach the ABA Payway gateway.");
        }

        String transactionId = MiniJson.firstString(response, "transId", "TRANS_ID", "transactionId");
        if (transactionId == null) {
            return PrecreateResult.failed("Gateway did not return a transaction id: "
                    + firstString(response, "message", "MESSAGE", "error"));
        }
        return new PrecreateResult(true, transactionId,
                MiniJson.firstString(response, "qrImage", "QR_IMAGE", "qr"),
                MiniJson.firstString(response, "abaPhone", "ABATEPHONE", "aba_phone"),
                firstString(response, "message", "MESSAGE"));
    }

    /**
     * Asks the gateway for the authoritative status of a transaction.
     * <p>
     * This is the call the whole design hangs on: the browser redirect carries a
     * status parameter, and that parameter is customer-controlled, so the order
     * is only ever marked paid from an answer to <em>this</em> method.
     */
    public StatusResult queryStatus(int orderId, String transactionId) {
        if (PaymentConfig.isSimulate()) {
            return new StatusResult(true, simulatedStatus(orderId), transactionId, "Simulated gateway");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", String.valueOf(orderId));
        body.put("transId", transactionId);

        Map<String, Object> response = post(STATUS_PATH, body);
        if (response == null) {
            return new StatusResult(false, null, transactionId, "Could not reach the ABA Payway gateway.");
        }
        return new StatusResult(true,
                firstString(response, "status", "STATUS"),
                firstString(response, "transId", "TRANS_ID", "transactionId"),
                firstString(response, "message", "MESSAGE"));
    }

    /**
     * Simulated QR payload. Not a scannable QR image -- it is a data URI
     * containing the same text, so the page is honest about being a demo and a
     * phone camera cannot be tricked into opening a real bank deep link.
     */
    private PrecreateResult simulatedPrecreate(int orderId, BigDecimal amount) {
        String transactionId = "SIM-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
        String payload = "APACH_PC_SIMULATED_PAYMENT\norder=" + orderId
                + "\ntransaction=" + transactionId
                + "\namount=" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + " " + PaymentConfig.currency();
        String svg = simulatedQrSvg(payload);
        String dataUri = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new PrecreateResult(true, transactionId, dataUri, "simulated", "Simulated gateway");
    }

    private String simulatedStatus(int orderId) {
        // Deterministic per order so a demo can show both outcomes: press the
        // button on the payment page to decide which one the gateway reports.
        return PaymentConfig.get("payment.aba.simulated_result_" + orderId, "1");
    }

    /**
     * Draws a deterministic checkerboard stand-in for a QR code. Visually
     * obvious at a glance that it is not a real code, which is the point.
     */
    private String simulatedQrSvg(String payload) {
        int cells = 25;
        int cell = 12;
        int size = cells * cell;
        int hash = Math.abs(payload.hashCode());
        StringBuilder rects = new StringBuilder();
        for (int y = 0; y < cells; y++) {
            for (int x = 0; x < cells; x++) {
                hash = hash * 1103515245 + 12345;
                boolean on = ((hash >>> 16) & 1) == 1;
                if (isFinder(x, y, cells) ? on : !on) {
                    continue;
                }
                rects.append("<rect x='").append(x * cell).append("' y='").append(y * cell)
                        .append("' width='").append(cell).append("' height='").append(cell).append("'/>");
            }
        }
        return "<svg xmlns='http://www.w3.org/2000/svg' width='" + size + "' height='" + size
                + "' viewBox='0 0 " + size + " " + size + "'><rect width='" + size + "' height='"
                + size + "' fill='#fff'/><g fill='#0f172a'>" + rects + "</g></svg>";
    }

    /** The three corner squares that make it read as a QR code at a glance. */
    private boolean isFinder(int x, int y, int cells) {
        int edge = 7;
        return (x < edge && y < edge)
                || (x >= cells - edge && y < edge)
                || (x < edge && y >= cells - edge);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        if (!PaymentConfig.isReady()) {
            return null;
        }
        String json = MiniJson.write(body);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PaymentConfig.apiUrl() + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("aba-username", PaymentConfig.username())
                    .header("aba-secret", PaymentConfig.secret())
                    // ABA signs the request by base64-encoding the exact body sent.
                    .header("Authorization", Base64.getEncoder()
                            .encodeToString(json.getBytes(StandardCharsets.UTF_8)))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return MiniJson.parseObject(response.body());
        } catch (java.io.IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            // Includes a malformed gateway body. Returning null keeps the caller
            // on its "could not reach the gateway" path instead of 500-ing checkout.
            return null;
        }
    }

    private String firstString(Map<String, Object> response, String... keys) {
        return MiniJson.firstString(response, keys);
    }
}
