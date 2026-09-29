package com.hengtongan.computerstore.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the promise that this application never stores a card number.
 * <p>
 * The demo validator already refuses any number outside a fixed list of published
 * test cards, so a real card cannot reach the database through the application.
 * This test covers the other direction: the <i>schema</i> must not offer a place
 * to put one either. A future change that adds a {@code card_number} column would
 * otherwise look harmless in review and quietly become the thing that leaks.
 *
 * <p>Source-level rather than a database test, because it has to run without
 * credentials and must hold on a fresh checkout where no migration has run yet.
 */
class CardSchemaSafetyTest {

    private static final String MIGRATION = "src/main/resources/db/migrations/migration_add_card_payments.sql";
    private static final String PAYMENT_ENTITY =
            "src/main/java/com/hengtongan/computerstore/core/domain/entity/Payment.java";

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    @Test
    void thePaymentsTableHasNoColumnForACardNumber() throws IOException {
        String sql = read(MIGRATION).toLowerCase(Locale.ROOT);
        // Strip comments first: this file deliberately discusses "card number"
        // in prose, and a naive search would match its own explanation.
        String code = sql.replaceAll("(?m)--.*$", "");

        List<String> offenders = new ArrayList<>();
        for (String column : List.of("card_number", "cardnumber", "pan", "cvv", "cvc",
                "card_expiry", "card_expiry_date", "expiry")) {
            if (Pattern.compile("\\badd\\s+column\\s+`?" + column + "`?\\b").matcher(code).find()) {
                offenders.add(column);
            }
        }
        if (!offenders.isEmpty()) {
            fail("payments must not gain a column that can hold card data: " + offenders
                    + ". A real integration stores a gateway token, not a card.");
        }
    }

    @Test
    void theLast4ColumnIsCappedByADatabaseConstraint() throws IOException {
        // The column width is what rejects a full PAN today; the CHECK is the
        // backstop that survives someone widening the column. This test guards
        // the backstop, so the guarantee does not quietly depend on a single
        // mechanism -- and so the comment in the migration stays true.
        String sql = read(MIGRATION).toLowerCase(Locale.ROOT);
        Matcher constraint = Pattern.compile(
                "check\\s*\\(\\s*card_last4\\s+is\\s+null\\s+or\\s+char_length\\s*\\(\\s*card_last4\\s*\\)\\s*<=\\s*4\\s*\\)")
                .matcher(sql);
        assertTrue(constraint.find(),
                "expected a CHECK capping card_last4 at 4 characters; it is the backstop that "
                        + "survives the column being widened to hold a 'fuller' mask.");
    }

    @Test
    void theLast4ColumnIsNarrowEnoughToRejectAPanOnItsOwn() throws IOException {
        // The primary limit. VARCHAR(4) is what makes a straight INSERT of a card
        // number fail today, so its width is a load-bearing part of the schema
        // rather than a detail.
        String sql = read(MIGRATION);
        Matcher width = Pattern.compile("card_last4\\s+varchar\\s*\\(\\s*(\\d+)\\s*\\)",
                Pattern.CASE_INSENSITIVE).matcher(sql);
        assertTrue(width.find(), "expected an explicit width for card_last4");
        assertEquals(4, Integer.parseInt(width.group(1)),
                "card_last4 must stay VARCHAR(4) or a full card number becomes storable");
    }

    @Test
    void theEntityHasNoFieldThatCouldHoldACardNumber() throws IOException {
        String source = read(PAYMENT_ENTITY);
        List<String> offenders = new ArrayList<>();
        // Field declarations, not methods: a getCardNumber() is a smell but a
        // private String cardNumber is the actual hazard.
        Matcher field = Pattern.compile("(?m)^\\s*private\\s+\\w+\\s+(\\w+)\\s*;").matcher(source);
        while (field.find()) {
            String name = field.group(1).toLowerCase(Locale.ROOT);
            if (name.contains("cardnumber") || name.equals("pan") || name.equals("cvv") || name.equals("cvc")) {
                offenders.add(field.group(1));
            }
        }
        if (!offenders.isEmpty()) {
            fail("Payment must not gain a field that can hold card data: " + offenders);
        }
    }

    @Test
    void theEntityKeepsOnlyAMask() throws IOException {
        // Positive counterpart: the mask is actually there, so the checks above
        // are not passing because the feature was never wired up.
        String source = read(PAYMENT_ENTITY);
        assertTrue(source.contains("private String cardLast4;"), "expected the masked last4 field");
        assertTrue(source.contains("private String cardBrand;"), "expected the brand field");
    }
}
