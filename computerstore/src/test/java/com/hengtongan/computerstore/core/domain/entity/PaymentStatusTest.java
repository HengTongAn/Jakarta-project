package com.hengtongan.computerstore.core.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The gateway reports status as a small integer, and the same numbers are
 * described differently in different places in ABA's documentation. Getting one
 * of these mappings wrong marks a real order unpaid, or a real payment unverified.
 */
class PaymentStatusTest {

    @Test
    void mapsTheDocumentedWireCodes() {
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire("0"));
        assertEquals(Payment.Status.PAID, Payment.Status.fromWire("1"));
        assertEquals(Payment.Status.CANCELLED, Payment.Status.fromWire("2"));
        assertEquals(Payment.Status.FAILED, Payment.Status.fromWire("3"));
        assertEquals(Payment.Status.EXPIRED, Payment.Status.fromWire("4"));
    }

    @Test
    void acceptsTheSpelledOutNames() {
        assertEquals(Payment.Status.PAID, Payment.Status.fromWire("SUCCESS"));
        assertEquals(Payment.Status.PAID, Payment.Status.fromWire("approved"));
        assertEquals(Payment.Status.CANCELLED, Payment.Status.fromWire("USER_CANCELLED"));
        assertEquals(Payment.Status.FAILED, Payment.Status.fromWire("failed"));
        assertEquals(Payment.Status.EXPIRED, Payment.Status.fromWire("EXPIRED"));
    }

    @Test
    void isCaseAndWhitespaceInsensitive() {
        assertEquals(Payment.Status.PAID, Payment.Status.fromWire("  success  "));
        assertEquals(Payment.Status.PAID, Payment.Status.fromWire("Paid"));
    }

    /**
     * The safe default. An unrecognised code must never fall through to PAID --
     * that would let a garbled gateway response mark an order as paid when the
     * customer never paid. It also must not throw, because this runs while
     * rendering a customer's payment page.
     */
    @Test
    void unknownCodesStayPendingRatherThanPaid() {
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire("99"));
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire("SUCCESSFUL"));
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire("banana"));
    }

    @Test
    void absentStatusStaysPending() {
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire(null));
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire(""));
        assertEquals(Payment.Status.PENDING, Payment.Status.fromWire("   "));
    }
}
