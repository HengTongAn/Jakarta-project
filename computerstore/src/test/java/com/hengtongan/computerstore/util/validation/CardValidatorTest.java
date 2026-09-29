package com.hengtongan.computerstore.util.validation;

import com.hengtongan.computerstore.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the demo card validator.
 * <p>
 * These exist to protect one claim: <b>a real card number cannot get past this
 * class, and once validation returns, the number is gone.</b> Everything else the
 * form does is presentation.
 */
class CardValidatorTest {

    private static final String APPROVED = "4242 4242 4242 4242";
    private static final String DECLINED = "4000 0000 0000 0002";

    private static String futureExpiry() {
        return YearMonth.now().plusYears(2).format(java.time.format.DateTimeFormatter.ofPattern("MM/yy"));
    }

    private static CardValidator.CardDetails ok(String number) {
        return CardValidator.validate(number, futureExpiry(), "123", "Sok Sovann");
    }

    // ------------------------------------------------------------- acceptance

    @Test
    void acceptsTheApprovedTestCard() {
        CardValidator.CardDetails card = ok(APPROVED);
        assertEquals("Visa", card.brand());
        assertEquals("4242", card.last4());
        assertTrue(card.approved());
        assertEquals("Visa ending 4242", card.masked());
    }

    @Test
    void encodesTheOutcomeInTheTestNumberSoADeclineCanBeDemonstrated() {
        assertFalse(ok(DECLINED).approved());
        assertFalse(ok("4000 0000 0000 9995").approved());
    }

    @Test
    void acceptsDigitsWithSpacesOrDashes() {
        assertEquals("4242", ok("4242-4242-4242-4242").last4());
        assertEquals("4242", ok("4242424242424242").last4());
        assertEquals("4242", ok("  4242 4242 4242 4242  ").last4());
    }

    // ------------------------------------------------ the important rejections

    @Test
    void refusesAWellFormedNumberThatIsNotATestCard() {
        // A real Visa number is Luhn-valid and 16 digits, so format checks alone
        // would wave it through. The whitelist is what stops it.
        ValidationException e = assertThrows(ValidationException.class,
                () -> CardValidator.validate("4111111111111111", futureExpiry(), "123", "Sok Sovann"));
        assertTrue(e.getMessage().toLowerCase().contains("test card"),
                "should explain that only test numbers work, got: " + e.getMessage());
    }

    @Test
    void theRejectionMessageNeverEchoesTheNumberTyped() {
        // Otherwise a mistyped real card ends up in a server log via the error.
        String realLooking = "4111111111111111";
        ValidationException e = assertThrows(ValidationException.class,
                () -> CardValidator.validate(realLooking, futureExpiry(), "123", "Sok Sovann"));
        assertFalse(e.getMessage().contains(realLooking),
                "message leaked the card number: " + e.getMessage());
        assertFalse(e.getMessage().contains("4111"), "message leaked a card prefix: " + e.getMessage());
    }

    @Test
    void theDemoMessageItselfIsSafeToRender() {
        // It is shown on a page and returned in a flash message, so it must list
        // only the test numbers -- never anything from a real submission.
        String message = CardValidator.demoOnlyMessage();
        for (String testCard : CardValidator.demoCards().keySet()) {
            assertTrue(message.contains(testCard), "should list " + testCard);
        }
        assertTrue(message.toLowerCase().contains("never enter a real card"));
    }

    @Test
    void rejectsMalformedNumbers() {
        assertThrows(ValidationException.class,
                () -> CardValidator.validate("", futureExpiry(), "123", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(null, futureExpiry(), "123", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate("424242424242", futureExpiry(), "123", "Sok Sovann"));
        // Luhn failure: same message whether it is a typo or not a card, so the
        // response cannot be used to probe which numbers are issued.
        ValidationException e = assertThrows(ValidationException.class,
                () -> CardValidator.validate("4242424242424243", futureExpiry(), "123", "Sok Sovann"));
        assertTrue(e.getMessage().toLowerCase().contains("not valid"));
    }

    @Test
    void rejectsExpiredAndMalformedDates() {
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, "01/20", "123", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, "13/30", "123", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, "not a date", "123", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, null, "123", "Sok Sovann"));
    }

    @Test
    void acceptsTheCurrentMonthAsStillValid() {
        // A card expiring this month is valid through the end of it; comparing
        // against YearMonth.now().minusMonths(1) would wrongly reject it.
        String thisMonth = YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("MM/yy"));
        assertEquals("4242", CardValidator.validate(APPROVED, thisMonth, "123", "Sok Sovann").last4());
    }

    @Test
    void rejectsBadSecurityCodes() {
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, futureExpiry(), "12", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, futureExpiry(), "1234", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, futureExpiry(), "12a", "Sok Sovann"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, futureExpiry(), null, "Sok Sovann"));
    }

    @Test
    void rejectsHolderNamesThatCouldSmuggleMarkup() {
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, futureExpiry(), "123", "<script>alert(1)</script>"));
        assertThrows(ValidationException.class,
                () -> CardValidator.validate(APPROVED, futureExpiry(), "123", "1234567890"));
    }

    // ------------------------------------------------ the structural guarantee

    @Test
    void theReturnedValueHasNowhereToPutACardNumber() {
        // The strongest form of the claim: not "we remember not to log it" but
        // "there is no field to log". Pinning the component list means adding a
        // component that could hold a number fails here rather than shipping.
        assertEquals(
                java.util.List.of("brand", "last4", "outcome", "message"),
                Arrays.stream(CardValidator.CardDetails.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .collect(Collectors.toList()));
    }

    @Test
    void last4IsAlwaysExactlyFourDigits() {
        // The value stored on the payment row, so its width is worth pinning:
        // card_last4 is VARCHAR(4) with a CHECK, and a 5-digit value would be
        // rejected by the database at insert time.
        assertEquals(4, ok(APPROVED).last4().length());
        for (String testCard : CardValidator.demoCards().keySet()) {
            assertEquals(4, CardValidator.validate(testCard, futureExpiry(), "123", "Sok Sovann")
                    .last4().length());
        }
    }

    // ------------------------------------------------------------ brand + luhn

    @Test
    void detectsBrandFromTheLeadingDigits() {
        assertEquals("Visa", CardValidator.brand("4242424242424242"));
        assertEquals("Mastercard", CardValidator.brand("5555555555554444"));
        assertEquals("Amex", CardValidator.brand("378282246310005"));
        assertEquals("Discover", CardValidator.brand("6011111111111117"));
        // Unrecognised prefixes report generically rather than being guessed at,
        // so a misdetected brand is never shown to a customer.
        assertEquals("Card", CardValidator.brand("9999999999999999"));
    }

    @Test
    void luhnMatchesKnownAnswers() {
        assertTrue(CardValidator.luhnValid("4242424242424242"));
        assertTrue(CardValidator.luhnValid("79927398713"));
        assertFalse(CardValidator.luhnValid("4242424242424243"));
        assertFalse(CardValidator.luhnValid(""));
    }

    @Test
    void groupedIsOnlyForDisplay() {
        // Fixed groups of four. Amex's 4-6-5 convention is deliberately not
        // implemented: every accepted test card is a 16-digit Visa, so a
        // brand-aware grouper would be code for a case that cannot occur.
        assertEquals("4242 4242 4242 4242", CardValidator.grouped("4242424242424242"));
        assertEquals("3782 8224 6310 005", CardValidator.grouped("378282246310005"));
    }

    @Test
    void digitsOnlyStripsEverythingThatIsNotADigit() {
        assertEquals("4242424242424242", CardValidator.digitsOnly("4242 4242-4242_4242"));
        assertEquals("", CardValidator.digitsOnly("abc"));
        assertEquals("", CardValidator.digitsOnly(null));
    }
}
