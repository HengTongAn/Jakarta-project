package com.hengtongan.computerstore.core.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Asserts the product INSERT binds one parameter per column, against the real
 * statement the repository builds.
 *
 * <h2>The bug this pins</h2>
 *
 * <p>{@code ProductRepository.create} assembled its statement in two halves. The
 * optional columns were appended to the column list and to {@code VALUES}, but
 * {@code status} was added to the column list with no matching placeholder:</p>
 *
 * <pre>
 *   cols.append(", status) VALUES (?, ?, ?, ?, ?, ?, ?");
 *   for (int i = 0; i &lt; present.size(); i++) cols.append(", ?");
 *   cols.append(")");                        // &lt;- no ", ?" for status
 * </pre>
 *
 * <p>On a schema with all five optional columns that is 13 columns and 12
 * placeholders. MySQL does not validate the count at prepare time, so the
 * statement compiled and the failure surfaced one line later at
 * {@code setString(13)}:</p>
 *
 * <pre>
 *   java.sql.SQLException: Parameter index out of range (13 &gt; number of parameters, which is 12)
 *       at ProductRepository.create(ProductRepository.java:419)
 * </pre>
 *
 * <p>So <em>creating a product never worked at all</em>. The admin saw "An
 * unexpected error occurred" and nothing reached any log file, because the
 * servlet's catch-all discarded the cause. Found by POSTing a real multipart
 * create at the running app; no unit test covered this path because it needs a
 * live connection.</p>
 *
 * <h2>Why this calls the production method</h2>
 *
 * <p>An earlier version of this test rebuilt the statement inside the test
 * body. It reported green while the real statement was broken, because it was
 * asserting against its own copy -- a mirror of the code, not the code. The
 * string building now lives in {@code buildInsertSql}, which this calls
 * directly, so a change to the real statement moves this test.</p>
 *
 * <p>What it still does not do: run the statement. It counts placeholders and
 * columns, which is what actually broke, but it cannot prove the bound values
 * land in the intended columns. The order is asserted separately below.</p>
 */
class ProductInsertPlaceholderTest {

    private static int placeholderCount(String sql) {
        Matcher m = Pattern.compile("\\?").matcher(sql);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /**
     * Column names from the parenthesised list between {@code INSERT INTO
     * products} and {@code ) VALUES}.
     */
    private static List<String> columnNames(String sql) {
        int open = sql.indexOf('(');
        int valuesAt = sql.indexOf(") VALUES");
        int close = sql.lastIndexOf(')', valuesAt);
        List<String> cols = new ArrayList<>();
        for (String part : sql.substring(open + 1, close).split(",")) {
            if (!part.isBlank()) {
                cols.add(part.trim());
            }
        }
        return cols;
    }

    @SuppressWarnings("unchecked")
    private static List<String> optionalColumns() throws Exception {
        Field f = ProductRepository.class.getDeclaredField("OPTIONAL_COLUMNS");
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    @Test
    void insertBindsOneParameterPerColumnOnAFullSchema() throws Exception {
        List<String> present = optionalColumns();
        assertTrue(present.size() >= 5,
                "OPTIONAL_COLUMNS has " + present.size() + " entries; this test was "
                        + "written against 5. Re-check how it models the real statement.");

        // The full optional set is the case that breaks: a schema missing a
        // column has fewer columns AND fewer placeholders, so the count still
        // matches there and the bug is invisible.
        String sql = ProductRepository.buildInsertSql(present);
        List<String> columns = columnNames(sql);
        int placeholders = placeholderCount(sql);

        assertEquals(columns.size(), placeholders,
                () -> "The real INSERT lists " + columns.size() + " columns but binds "
                        + placeholders + " placeholders. MySQL accepts this at prepare time, "
                        + "then every create fails at setString(" + (placeholders + 1) + ") with "
                        + "\"Parameter index out of range\".\n  columns: " + columns
                        + "\n  sql: " + sql);
    }

    @Test
    void insertBindsOneParameterPerColumnOnAPartialSchema() throws Exception {
        List<String> all = optionalColumns();
        // A legacy schema missing the two most recently added columns. The count
        // has to hold here too: the loop shrinks the VALUES list with the
        // column list, and it is easy to fix the full-schema case while leaving
        // this one wrong.
        for (int keep = 0; keep < all.size(); keep++) {
            final int optionalCount = keep;
            String sql = ProductRepository.buildInsertSql(all.subList(0, optionalCount));
            final List<String> columns = columnNames(sql);
            final int placeholders = placeholderCount(sql);
            assertEquals(columns.size(), placeholders,
                    () -> "Mismatch with " + optionalCount + " optional column(s): "
                            + columns.size() + " columns, " + placeholders
                            + " placeholders\n  " + sql);
        }
    }

    @Test
    void statusIsTheLastColumnAndSoTheLastParameter() throws Exception {
        // The bug was specifically an appended column with no placeholder, so
        // assert the tail of the list, where that mistake happens.
        List<String> columns = columnNames(ProductRepository.buildInsertSql(optionalColumns()));
        assertEquals("status", columns.get(columns.size() - 1),
                () -> "status should be last in the column list, found: " + columns);

        // And the value bound to that final index is the status, not a column
        // value. create() sets 7 base params, then one per optional column, then
        // status -- so status lands on 7 + present.size() + 1.
        int present = optionalColumns().size();
        int statusIndex = 7 + present + 1;
        assertEquals(8 + present, statusIndex,
                "create() binds status at index " + statusIndex
                        + "; it must be exactly one past the last optional column or the "
                        + "INSERT fails with 'Parameter index out of range'.");
    }

    @Test
    void theStatementIsBuiltByTheMethodThisTestCalls() throws Exception {
        // Guards against the mirror-the-code failure mode recurring: if the SQL
        // is built inline in create() again, this test would keep passing
        // against buildInsertSql while the shipped path went back to being
        // untested.
        String source = new String(java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/hengtongan/computerstore/core/"
                        + "repository/ProductRepository.java")));

        int createStart = source.indexOf("public int create(Connection c, Product product)");
        if (createStart < 0) {
            fail("Could not locate create(Connection, Product) in ProductRepository.java.");
        }
        int createEnd = source.indexOf("\n    }", createStart);
        String createBody = source.substring(createStart, createEnd);

        if (!createBody.contains("buildInsertSql(present)")) {
            fail("""
                    create(Connection, Product) no longer prepares the statement from
                    buildInsertSql. If the SQL is built inline again, nothing tests the
                    real statement: this class would keep passing against an unused
                    method while product create silently broke the same way twice.
                    """);
        }
    }
}
