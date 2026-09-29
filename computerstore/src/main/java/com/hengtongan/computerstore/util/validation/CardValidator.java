package com.hengtongan.computerstore.util.validation;

import com.hengtongan.computerstore.core.exception.ValidationException;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates a card form submission for the demo card payment method.
 *
 * <h2>What this class will not do</h2>
 * It does not store, log or return the card number. {@link CardDetails} carries
 * only the brand and the last four digits, so once {@link #validate} returns the
 * full number is unreachable from everything downstream -- there is no field to
 * accidentally log, serialise into an audit row, or copy into an exception
 * message. No rejection message ever echoes what was typed.
 *
 * <h2>Why only known test numbers are accepted</h2>
 * A demo form that accepts any well-formed number is a trap: someone will type
 * their real card into it, and then a real card number is sitting in this
 * application's request handling. So the accepted set is a fixed whitelist of
 * published, non-live test numbers, and anything else is refused with a message
 * that names the test numbers instead. A real Visa number therefore cannot get
 * past this method, which is what makes "nothing is stored" a property of the
 * code rather than a promise.
 *
 * <p>Expiry and CVV are checked for shape and for a date that has not passed, so
 * the demo cannot be driven into an inconsistent state.
 */
public final class CardValidator {

    /**
     * Published test numbers and the outcome each one simulates. The keys are
     * digits only; the values are the outcome and a customer-facing reason.
     * <p>
     * These are the widely published sandbox numbers. They are not issued to
     * anyone and cannot be charged, and encoding the outcome in the number means
     * a demo can show a decline without any extra UI.
     */
    private static final Map<String, String[]> TEST_CARDS = new LinkedHashMap<>();

    static {
        // number, outcome, reason shown to the customer
        TEST_CARDS.put("4242424242424242", new String[]{CardDetails.APPROVED, "Approved"});
        TEST_CARDS.put("4000000000000002", new String[]{CardDetails.DECLINED, "Your card was declined by the issuer."});
        TEST_CARDS.put("4000000000009995", new String[]{CardDetails.DECLINED, "Your card has insufficient funds."});
        TEST_CARDS.put("4000000000000069", new String[]{CardDetails.DECLINED, "That card has expired."});
        TEST_CARDS.put("4000000000000127", new String[]{CardDetails.DECLINED, "Incorrect security code."});
        TEST_CARDS.put("4000000000000119", new String[]{CardDetails.DECLINED, "The card could not be processed. Try another card."});
    }

    /** A masked, PAN-free description of an accepted card. */
    public record CardDetails(String brand, String last4, String outcome, String message) {

        public static final String APPROVED = "APPROVED";
        public static final String DECLINED = "DECLINED";

        public boolean approved() {
            return APPROVED.equals(outcome);
        }

        /** e.g. {@code "Visa ending 4242"}. Safe to render: no PAN. */
        public String masked() {
            return brand + " ending " + last4;
        }
    }

    private CardValidator() {
    }

    /**
     * Validates a submission and reduces it to a mask plus the outcome the
     * simulated issuer will report.
     *
     * @throws ValidationException with a message that never contains the number
     */
    public static CardDetails validate(String cardNumber, String expiry, String cvv, String holderName) {
        String digits = digitsOnly(cardNumber);
        if (digits.isEmpty()) {
            throw new ValidationException("Enter your card number.");
        }
        if (digits.length() < 13 || digits.length() > 19) {
            throw new ValidationException("That card number is not the right length.");
        }
        if (!luhnValid(digits)) {
            // Deliberately does not echo the number, and does not distinguish
            // "typo" from "not a card": both are just wrong.
            throw new ValidationException("That card number is not valid. Check it and try again.");
        }
        if (!TEST_CARDS.containsKey(digits)) {
            throw new ValidationException(demoOnlyMessage());
        }
        validateExpiry(expiry);
        validateCvv(cvv, digits);
        validateHolderName(holderName);

        String[] outcome = TEST_CARDS.get(digits);
        return new CardDetails(brand(digits), digits.substring(digits.length() - 4), outcome[0], outcome[1]);
    }

    /**
     * The message shown when a well-formed but unknown number is entered.
     * <p>
     * This is the safety valve of the whole feature, so it says plainly that the
     * store is a demo and names the numbers that will work -- someone who has
     * just typed their real card needs to be told immediately, not left guessing.
     */
    public static String demoOnlyMessage() {
        StringBuilder sb = new StringBuilder("This store runs card payments in demo mode, so only test card numbers are accepted. Use ");
        boolean first = true;
        for (String number : TEST_CARDS.keySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(grouped(number));
            first = false;
        }
        sb.append(". Never enter a real card number here.");
        return sb.toString();
    }

    /** The test numbers, masked to the last four, for the hint shown under the form. */
    public static Map<String, String> demoCards() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : TEST_CARDS.entrySet()) {
            out.put(grouped(entry.getKey()), entry.getKey().substring(entry.getKey().length() - 4));
        }
        return out;
    }

    // ------------------------------------------------------------------ format

    /** Strips spaces and dashes so "4242 4242 4242 4242" validates like the raw digits. */
    public static String digitsOnly(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Standard Luhn checksum. Public so the browser can run the same check. */
    public static boolean luhnValid(String digits) {
        if (digits.isEmpty()) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    /**
     * Brand from the leading digits, Visa first because that is the method this
     * store offers. Anything unrecognised is reported generically rather than
     * guessed at, so a misdetected brand cannot be shown to a customer.
     */
    public static String brand(String digits) {
        if (digits.startsWith("4")) {
            return "Visa";
        }
        if (digits.startsWith("51") || digits.startsWith("52")
                || digits.startsWith("53") || digits.startsWith("54") || digits.startsWith("55")) {
            return "Mastercard";
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return "Amex";
        }
        if (digits.startsWith("6011") || digits.startsWith("65")) {
            return "Discover";
        }
        return "Card";
    }

    /**
     * Accepts {@code MM/YY}, {@code MM/YY} with any separator, or {@code MMYYYY}.
     * The expiry is relative to the current month, so the test cards stay usable
     * instead of quietly expiring and making the demo look broken.
     */
    static void validateExpiry(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Enter the expiry date.");
        }
        String cleaned = raw.trim().replaceAll("[^0-9]", "");
        if (cleaned.length() != 4 && cleaned.length() != 6) {
            throw new ValidationException("Enter the expiry as MM/YY.");
        }
        int month = Integer.parseInt(cleaned.substring(0, 2));
        int year = cleaned.length() == 6
                ? Integer.parseInt(cleaned.substring(2, 6))
                : 2000 + Integer.parseInt(cleaned.substring(2, 4));
        if (month < 1 || month > 12) {
            throw new ValidationException("That expiry month is not valid.");
        }
        YearMonth expiry = YearMonth.of(year, month);
        if (expiry.isBefore(YearMonth.now())) {
            throw new ValidationException("That card has expired.");
        }
    }

    /** Three digits, or four for Amex. */
    static void validateCvv(String raw, String digits) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Enter the security code.");
        }
        String cleaned = raw.trim();
        for (int i = 0; i < cleaned.length(); i++) {
            if (cleaned.charAt(i) < '0' || cleaned.charAt(i) > '9') {
                throw new ValidationException("The security code is digits only.");
            }
        }
        int expected = "Amex".equals(brand(digits)) ? 4 : 3;
        if (cleaned.length() != expected) {
            throw new ValidationException("The security code should be " + expected + " digits.");
        }
    }

    static void validateHolderName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Enter the name on the card.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 60) {
            throw new ValidationException("That name is too long.");
        }
        if (!trimmed.matches("[A-Za-z][A-Za-z .'-]{1,59}")) {
            throw new ValidationException("Enter the name as it appears on the card.");
        }
    }

    /** {@code 4242424242424242} -> {@code 4242 4242 4242 4242}. */
    public static String grouped(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }
}
