package com.hengtongan.computerstore.core.repository;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin History keyword search used to return <b>500</b> on every search.
 *
 * <h2>What actually happened</h2>
 *
 * The keyword clause was written as {@code ESCAPE '\'}. That is not a valid SQL
 * string literal when backslash escaping is on (the default): the {@code \}
 * escapes the closing quote, so the literal never terminates and MySQL keeps
 * consuming what follows — including the {@code ?} parameter markers. The
 * driver therefore counted only 3 parameters in a statement the code was about
 * to bind 6 values to, and threw:
 *
 * <pre>
 * java.sql.SQLException: Parameter index out of range (4 &gt; number of
 *   parameters, which is 3)
 *   at ...AuditLogRepository.count(AuditLogRepository.java:57)
 *   at ...AdminHistoryServlet.doGet(AdminHistoryServlet.java:49)
 * </pre>
 *
 * <p>Verified directly against MySQL 8.4.10: {@code ESCAPE '\'} is
 * <b>ERROR 1064 syntax error</b>, while {@code ESCAPE '\\'} parses and runs.
 * The correct form was already in {@code ProductRepository.applyFilters} — the
 * same clause written two different ways in one codebase.
 *
 * <h2>Why a source-level test and not a database test</h2>
 *
 * A JDBC-backed test would need a live schema, which this project has no fixture
 * for. But the defect is entirely in the SQL <i>text</i>, so the text is what
 * gets asserted. The two properties below are what MySQL cares about, and both
 * were violated at once.
 */
class AuditLogRepositoryFilterSqlTest {

    /** Builds the WHERE fragment and its bound values, exactly as the DAO does. */
    private static String[] build(String type, String actor, String keyword,
                                  LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        new AuditLogRepository().appendFilters(sql, params, type, actor, keyword, from, to);
        return new String[] { sql.toString(), String.valueOf(params.size()) };
    }

    private static int placeholders(String sql) {
        int n = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') n++;
        }
        return n;
    }

    @Test
    void placeholderCountAlwaysMatchesTheNumberOfBoundParams() {
        // The invariant whose violation surfaced as the 500. Checked across every
        // filter combination, because the original bug only appeared when the
        // keyword was present -- a search box that always works when empty and
        // always fails when used is easy to miss by hand.
        String[] types = { null, "DATA" };
        String[] actors = { null, "admin" };
        String[] keywords = { null, "order", "100%_off\\sale" };
        LocalDate[] dates = { null, LocalDate.of(2026, 9, 1) };

        for (String type : types) {
            for (String actor : actors) {
                for (String keyword : keywords) {
                    for (LocalDate from : dates) {
                        for (LocalDate to : dates) {
                            String[] built = build(type, actor, keyword, from, to);
                            assertEquals(placeholders(built[0]), Integer.parseInt(built[1]),
                                    () -> "placeholder/param mismatch for type=" + type
                                            + " actor=" + actor + " keyword=" + keyword
                                            + " from=" + from + " to=" + to
                                            + "\n  sql: " + built[0]);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyEscapeClauseCarriesExactlyTwoBackslashes() {
        // The literal that reaches MySQL must be '\\' (one backslash once
        // unescaped). A single backslash there is the defect itself.
        String sql = build(null, null, "order", null, null)[0];

        assertTrue(sql.contains("ESCAPE '\\\\'"),
                () -> "expected the MySQL form ESCAPE '\\\\' but got: " + sql);
        assertFalse(sql.contains("ESCAPE '\\' "),
                () -> "found a lone-backslash ESCAPE literal, which MySQL cannot parse: " + sql);

        // No ESCAPE clause may be followed by anything other than a quote, a
        // space or a LIKE column -- i.e. no half-escaped literal slipped in.
        for (int i = 0; i + 9 <= sql.length(); i++) {
            if (sql.startsWith("ESCAPE '", i)) {
                String clause = sql.substring(i, Math.min(sql.length(), i + 12));
                assertTrue(clause.startsWith("ESCAPE '\\\\'"),
                        () -> "malformed ESCAPE clause near: " + clause);
            }
        }
    }

    @Test
    void aKeywordSearchBindsSixColumns() {
        // Six searchable columns, six placeholders, six values. Pins the shape
        // of the clause so adding a column to the OR list without adding its
        // placeholder is caught here rather than in production.
        String[] built = build(null, null, "order", null, null);

        assertEquals(6, placeholders(built[0]), () -> "expected 6 placeholders: " + built[0]);
        assertEquals("6", built[1], "expected 6 bound values");
        for (String column : new String[] { "action_name", "actor", "resource_type",
                                            "resource_id", "details", "ip_address" }) {
            assertTrue(built[0].contains(column + " LIKE ?"),
                    () -> column + " should be searched with a bound parameter");
        }
    }

    @Test
    void noFiltersMeansNoPlaceholdersAndNoParams() {
        String[] built = build(null, null, null, null, null);
        assertEquals(0, placeholders(built[0]));
        assertEquals("0", built[1]);
    }

    @Test
    void aLoneKeywordSearchIsTheCaseThatUsedToFail() {
        // This is precisely the call AdminHistoryServlet makes on a plain search:
        // count(null, null, keyword, null, null).
        String[] built = build(null, null, "order", null, null);
        assertEquals(6, placeholders(built[0]));
        assertEquals("6", built[1]);
        assertTrue(built[0].startsWith("SELECT COUNT(*) FROM audit_logs WHERE 1 = 1 AND ("));
    }
}
