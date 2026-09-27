package com.hengtongan.computerstore.core.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards a failure mode that only shows up at runtime, as a 500.
 * <p>
 * A repository's row mapper reads columns by name ({@code rs.getString("qr_image")})
 * from a hand-written SELECT list. Adding a field to the mapper but forgetting the
 * SELECT list compiles cleanly, passes every unit test, and then throws
 * {@code SQLSyntaxErrorException: Column 'x' not found} the first time a customer
 * opens the page. That is exactly what happened to {@code qr_image} during the
 * payment work, and nothing caught it.
 * <p>
 * <b>Scope: single-mapper repositories only.</b> The check pairs one {@code COLUMNS}
 * constant with the whole file's {@code rs.getX} calls, which is only sound when
 * there is exactly one of each. {@code OrderRepository} and {@code ProductRepository}
 * are deliberately not covered: they have several mappers sharing one select list,
 * so a whole-file comparison would be meaningless rather than merely incomplete.
 * Extend the check per-mapper if those are ever refactored.
 * <p>
 * It reads the source because the unit tests have no database; reflection could see
 * the compiled constant but not which columns the mapper actually asks for.
 */
class RepositoryColumnCoverageTest {

    private static final Pattern COLUMN_CONSTANT =
            Pattern.compile("COLUMNS\\s*=\\s*(.*?);", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern RS_GETTER = Pattern.compile(
            "rs\\.get(?:String|Int|Long|Boolean|BigDecimal|Timestamp|Bytes|Object)\\(\\s*\"([^\"]+)\"");

    @Test
    void paymentMapperReadsOnlySelectedColumns() throws IOException {
        Set<String> selected = selectedColumns("PaymentRepository.java");
        Set<String> missing = notSelected("PaymentRepository.java", selected);

        if (!missing.isEmpty()) {
            fail("PaymentRepository: the row mapper reads column(s) " + missing
                    + " that the COLUMNS select list omits. This compiles and passes unit"
                    + " tests, then throws \"Column not found\" at runtime."
                    + " Selected: " + selected);
        }
        assertFalse(selected.isEmpty(), "COLUMNS constant not found in PaymentRepository");
    }

    /** Sanity check on the checker: it must actually be reading both lists. */
    @Test
    void theCheckIsNotVacuouslyPassing() throws IOException {
        Set<String> selected = selectedColumns("PaymentRepository.java");
        assertTrue(selected.contains("payment_id"), "expected payment_id in the select list");
        assertTrue(selected.contains("qr_image"), "expected qr_image in the select list");
        assertTrue(requestedColumns("PaymentRepository.java").size() > 5,
                "expected the mapper to request several columns");
    }

    private Set<String> notSelected(String file, Set<String> selected) throws IOException {
        Set<String> missing = new LinkedHashSet<>();
        for (String column : requestedColumns(file)) {
            if (!selected.contains(column)) {
                missing.add(column);
            }
        }
        return missing;
    }

    private Set<String> selectedColumns(String fileName) throws IOException {
        String source = read(fileName);
        Matcher constant = COLUMN_CONSTANT.matcher(source);
        if (!constant.find()) {
            return Set.of();
        }
        Set<String> columns = new LinkedHashSet<>();
        Matcher literal = STRING_LITERAL.matcher(constant.group(1));
        while (literal.find()) {
            for (String part : literal.group(1).split(",")) {
                String name = part.trim();
                if (!name.isEmpty()) {
                    columns.add(name);
                }
            }
        }
        return columns;
    }

    private Set<String> requestedColumns(String fileName) throws IOException {
        Set<String> columns = new LinkedHashSet<>();
        Matcher m = RS_GETTER.matcher(read(fileName));
        while (m.find()) {
            columns.add(m.group(1));
        }
        return columns;
    }

    private String read(String fileName) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/hengtongan/computerstore/core/repository", fileName),
                StandardCharsets.UTF_8);
    }
}
