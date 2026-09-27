package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hermetic tests for how a submitted payment method is resolved. No database.
 * <p>
 * The rejection case is the important one: the method arrives from a hidden form
 * field, and if an unrecognised value silently fell back to cash on delivery
 * then tampering with the form could downgrade a real gateway payment.
 */
class PaymentServiceMethodTest {

    private final PaymentService service = new PaymentService();

    @Test
    void acceptsTheFormValuesCheckoutActuallySends() {
        assertEquals(PaymentService.METHOD_ABA, service.normaliseMethod("aba"));
        assertEquals(PaymentService.METHOD_CARD, service.normaliseMethod("visa"));
        assertEquals(PaymentService.METHOD_COD, service.normaliseMethod("cash"));
    }

    @Test
    void acceptsTheAliasesTheGatewayUses() {
        assertEquals(PaymentService.METHOD_ABA, service.normaliseMethod("abapay"));
        assertEquals(PaymentService.METHOD_ABA, service.normaliseMethod("aba_payway"));
        assertEquals(PaymentService.METHOD_ABA, service.normaliseMethod("ABA Payway"));
        assertEquals(PaymentService.METHOD_CARD, service.normaliseMethod("card"));
        assertEquals(PaymentService.METHOD_CARD, service.normaliseMethod("credit_card"));
        assertEquals(PaymentService.METHOD_COD, service.normaliseMethod("cod"));
        assertEquals(PaymentService.METHOD_COD, service.normaliseMethod("cash_on_delivery"));
    }

    @Test
    void isCaseAndWhitespaceInsensitive() {
        assertEquals(PaymentService.METHOD_ABA, service.normaliseMethod("  ABA  "));
        assertEquals(PaymentService.METHOD_CARD, service.normaliseMethod(" Visa "));
        assertEquals(PaymentService.METHOD_COD, service.normaliseMethod(" Cash "));
    }

    @Test
    void rejectsAnythingElseRatherThanDefaulting() {
        // "card" used to be rejected: the old checkout had a card form that
        // collected card numbers and submitted nothing, so accepting the value
        // would have implied a payment that never happened. It is accepted again
        // now that it settles for real (in simulation). Everything unknown must
        // still be refused, or a tampered form could downgrade a payment.
        assertThrows(ValidationException.class, () -> service.normaliseMethod("paypal"));
        assertThrows(ValidationException.class, () -> service.normaliseMethod("bank_transfer"));
        assertThrows(ValidationException.class, () -> service.normaliseMethod(""));
        assertThrows(ValidationException.class, () -> service.normaliseMethod(null));
        assertThrows(ValidationException.class, () -> service.normaliseMethod("<script>"));
    }

    @Test
    void eachMethodMapsToItsOwnStoredValue() {
        // Guards against two methods collapsing onto one constant, which would
        // make the payment_method column unable to tell them apart.
        assertEquals(3, java.util.Set.of(
                PaymentService.METHOD_ABA,
                PaymentService.METHOD_CARD,
                PaymentService.METHOD_COD).size());
    }
}
