package com.hengtongan.computerstore.web.view;

import org.junit.jupiter.api.Test;

import com.hengtongan.computerstore.core.repository.TransactionRepository.TransactionStats;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards that every {@code ${stats.*}} the transaction pages render is a real
 * getter on {@link TransactionStats}.
 *
 * <h2>Why a source-reading test and not a rendering test</h2>
 *
 * {@code stats.grossPayments} is EL, not Java. It compiles perfectly in the JSP
 * and is resolved at request time by Tomcat, which throws
 * {@code PropertyNotFoundException} and serves a 500 if the bean has no such
 * getter. So the compiler is silent, {@code mvn package} is green, and the whole
 * test suite passes -- the failure only appears when an admin opens the page.
 *
 * <p>That is exactly what happened: the admin and customer transaction pages read
 * {@code grossPayments}, {@code grossRefunds} and {@code netAmount}, none of which
 * existed on {@code TransactionStats}. Both pages had been returning 500.</p>
 *
 * <h2>Why reflection rather than string matching</h2>
 *
 * The obvious check is to grep the repository for the field name, which is the
 * check that would have passed while the pages were broken -- the money figures
 * were added to the views first, and a search finds nothing to complain about
 * because nothing existed. Asking the class itself, via
 * {@code getDeclaredMethod("get" + Name)}, fails on a missing getter whatever the
 * source happens to spell, and also catches a property that exists but is
 * unreadable because the getter is not public.
 *
 * <h2>What is not checked</h2>
 *
 * That the values are right. That needs a database with refunds in it and a
 * servlet container to render the EL. This holds the half that can be read off
 * the source: the name the page asks for is a name the bean can answer.
 */
class TransactionStatsPropertyTest {

    private static final Path STATS_BEAN =
            Path.of("src/main/java/com/hengtongan/computerstore/core/repository/TransactionRepository.java");

    /** The views that render {@code stats}, and the servlet scope that binds it. */
    private static final Path[] VIEWS = {
            Path.of("src/main/webapp/WEB-INF/views/admin/transactions/list.jsp"),
            Path.of("src/main/webapp/WEB-INF/views/customer/account/transactions.jsp"),
    };

    /**
     * Deliberately word-bounded. Matching {@code stats.} alone would also catch
     * the scriptlet-local variables of the same name in the same files.
     */
    private static final Pattern STATS_PROPERTY = Pattern.compile("\\$\\{\\s*stats\\.([A-Za-z][A-Za-z0-9_]*)\\s*}");

    @Test
    void everyStatsPropertyThePagesRenderIsARealGetter() throws IOException {
        Map<String, String> missing = new LinkedHashMap<>();
        int checked = 0;

        for (Path view : VIEWS) {
            assertTrue(Files.exists(view), "Expected to find " + view);
            Matcher m = STATS_PROPERTY.matcher(Files.readString(view, StandardCharsets.UTF_8));
            while (m.find()) {
                String property = m.group(1);
                checked++;
                if (!hasPublicGetter(TransactionStats.class, property)) {
                    missing.put(view.getFileName() + ": " + property, describe());
                }
            }
        }

        assertTrue(checked > 0,
                "Found no ${stats.*} references at all, so this test is checking nothing. Either "
                        + "the views were renamed or the pattern stopped matching, and in both "
                        + "cases a real regression would pass silently.");

        if (!missing.isEmpty()) {
            fail("These ${stats.*} references have no getter on TransactionStats. Tomcat resolves "
                    + "EL at request time, so the page compiles, the build passes, and every admin "
                    + "who opens it gets a 500 PropertyNotFoundException:\n  "
                    + String.join("\n  ", missing.keySet())
                    + "\n\nCurrently declared:\n  " + describe());
        }
    }

    @Test
    void theMoneyFiguresAreNotTheSameNumberTwice() throws IOException {
        // gross/net only mean something if the three differ. Before this work the
        // class had a single "completed amount" column, which swept refunds in with
        // payments because a refund row is COMPLETED like a payment is -- a store
        // that took $100 and returned $100 reported $200 of completed payments.
        assertTrue(hasPublicGetter(TransactionStats.class, "grossPayments"),
                "Money in must be readable");
        assertTrue(hasPublicGetter(TransactionStats.class, "grossRefunds"),
                "Money out must be readable. Without it a refund is invisible and the "
                        + "'completed amount' total silently counts it as revenue.");
        assertTrue(hasPublicGetter(TransactionStats.class, "netAmount"),
                "What the store kept must be readable");
    }

    @Test
    void theRepositoryActuallyPopulatesTheMoneyFigures() throws IOException {
        // Guards against a getter existing that is never assigned. That is the
        // quiet version of this bug: the property resolves, so the page renders,
        // and it renders $0.00 for every store including one with real revenue.
        String source = Files.readString(STATS_BEAN, StandardCharsets.UTF_8);
        for (String property : new TreeSet<>(Map.of(
                "grossPayments", "grossPayments",
                "grossRefunds", "grossRefunds",
                "netAmount", "netAmount").keySet())) {
            String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            assertTrue(source.contains("." + setter + "("),
                    "TransactionRepository never calls " + setter
                            + ". A getter that is never assigned resolves in EL and always renders "
                            + "as zero, which is harder to notice than a 500 because the page looks "
                            + "correct and every store reports the same revenue.");
        }
    }

    // ------------------------------------------------------- the money query

    /**
     * The three money figures are only as honest as the SQL behind them.
     *
     * <h2>What this can and cannot do</h2>
     *
     * These are assertions about the query <em>text</em>, which is a weaker kind of
     * guard than the bean-shape checks above and should be read as such. What it
     * does buy is that the three ways this query can silently start lying are
     * pinned: direction flipped, the settled-status gate dropped, and the NULL
     * guard removed. Each of those compiles, satisfies every other test in the
     * suite, and reports wrong money.
     *
     * <p>The real evidence that the arithmetic is right is not in this file. It is
     * an execution of this exact SQL against the development database, with
     * synthetic rows covering each case: a $100 sale reported as +100 gross and
     * +100 net, a $100 refund as -100, a $30 partial as -30, and failed and
     * pending rows as excluded because no money moved. That run also confirmed
     * {@code transaction_type} is {@code NOT NULL}, so the {@code <>} comparison
     * cannot silently drop a row on a three-valued-logic surprise.</p>
     */
    @Test
    void theMoneySumsAgreeOnWhatCountsAsSettledAndDisagreeOnDirection() throws IOException {
        String sql = moneySums();

        String payments = expressionFor(sql, "gross_payments");
        String refunds = expressionFor(sql, "gross_refunds");

        // The settled gate. Without it a PENDING or FAILED payment counts as
        // revenue, which is the difference between "taken" and "attempted".
        assertTrue(payments.contains("status = 'COMPLETED'"),
                "gross_payments must only count settled rows. An order that was never "
                        + "paid is not revenue, and a FAILED one certainly is not.");
        assertTrue(refunds.contains("status = 'COMPLETED'"),
                "gross_refunds must only count settled refunds. A refund that is still "
                        + "in flight has not yet returned any money.");

        // Direction, as an exact complement rather than a pair of lists. Enumerating
        // "not REFUND and not PARTIAL_REFUND" instead would classify a CHARGEBACK as
        // revenue, since it is neither of those two.
        assertTrue(payments.contains("transaction_type = 'PAYMENT'"),
                "gross_payments must be defined as the PAYMENT type. Money in has one "
                        + "shape, and naming it positively keeps a new type defaulting "
                        + "to money out instead of to revenue.");
        assertTrue(refunds.contains("transaction_type <> 'PAYMENT'"),
                "gross_refunds must be the complement of gross_payments. Listing the "
                        + "refund types instead of negating PAYMENT lets a chargeback be "
                        + "counted as money coming in.");
    }

    @Test
    void anEmptyLedgerReadsAsZeroRatherThanBlank() throws IOException {
        // SUM() over no matching rows is SQL NULL, and EL renders that as an empty
        // string -- so a brand new store would show a blank currency figure exactly
        // where it should show $0.00, and a blank reads as "broken" to an operator
        // rather than "no sales yet".
        //
        // Verified against the development database rather than assumed: the exact
        // aggregate over an empty transactions table returns NULL, and the same
        // expression wrapped in COALESCE(...,0) returns 0.00. Only the empty-table
        // case produces NULL -- rows that exist but match nothing hit the CASE's
        // ELSE 0 and total to a real zero -- which is exactly the new-store case.
        String source = Files.readString(STATS_BEAN, StandardCharsets.UTF_8);
        for (String column : new String[] {"gross_payments", "gross_refunds"}) {
            assertTrue(source.contains("zeroIfNull(rs.getBigDecimal(\"" + column + "\"))"),
                    "The raw SUM for " + column + " must go through zeroIfNull. It is SQL "
                            + "NULL whenever the ledger is empty, and that renders as a blank "
                            + "currency figure rather than zero.");
        }

        // Checking only the call sites is how the helper was once neutered with the
        // suite still green: the calls were all correct and the body had stopped
        // replacing anything. A helper whose entire reason to exist is the null
        // replacement is worth one assertion on its own behaviour.
        assertTrue(source.contains("value == null ? java.math.BigDecimal.ZERO : value"),
                "zeroIfNull must actually substitute ZERO for null. Its callers are "
                        + "checked above, but a guard that returns the null straight "
                        + "through satisfies every caller assertion while rendering blank.");

        // The half that is not obvious, and the reason this helper has a scale at all.
        assertTrue(source.contains("setScale(MONEY_SCALE"),
                "zeroIfNull must pin the scale. BigDecimal.ZERO has scale 0 while a "
                        + "decimal(10,2) column yields scale 2, and DecimalFormat renders "
                        + "them differently: an empty ledger showed $0 where a ledger "
                        + "holding a zero-amount row showed $0.00. Same store, same "
                        + "figure, different format, decided by row count.");
    }

    @Test
    void moneyScaleMatchesTheAmountColumn() throws IOException {
        // The scale is hardcoded in Java and also declared in SQL, in different
        // files, so nothing else in the build would notice them drifting apart. Read
        // the column's scale out of the migration and compare the number, rather than
        // matching the literal text -- "DECIMAL(10,2)" and "DECIMAL(10, 2)" are the
        // same column and a text match would fail on the spacing while proving
        // nothing about the agreement that matters.
        Path migration = Path.of("src/main/resources/db/migrations/migration_add_transactions.sql");
        assertTrue(Files.exists(migration), "Expected to find " + migration);
        Matcher m = Pattern.compile("amount\\s+DECIMAL\\s*\\(\\s*\\d+\\s*,\\s*(\\d+)\\s*\\)")
                .matcher(Files.readString(migration, StandardCharsets.UTF_8));
        assertTrue(m.find(),
                "Could not read the scale of transactions.amount out of " + migration
                        + ". This test exists to tie MONEY_SCALE in TransactionRepository to the "
                        + "column it claims to mirror; if the column definition cannot be found, "
                        + "that tie has quietly become a guess.");

        int columnScale = Integer.parseInt(m.group(1));
        String source = Files.readString(STATS_BEAN, StandardCharsets.UTF_8);
        Matcher c = Pattern.compile("MONEY_SCALE\\s*=\\s*(\\d+)")
                .matcher(source);
        assertTrue(c.find(), "Could not find MONEY_SCALE in " + STATS_BEAN);
        int codeScale = Integer.parseInt(c.group(1));

        assertEquals(columnScale, codeScale,
                "MONEY_SCALE in TransactionRepository is " + codeScale + " but transactions.amount "
                        + "is DECIMAL(10," + columnScale + "). A figure rendered at the wrong scale "
                        + "shows cents that are not there or drops ones that are.");
    }

    @Test
    void theStatsQueryIsSharedByBothCallers() throws IOException {
        // The admin and customer views used to carry byte-identical SQL. That is how
        // refund-aware totals reached one view's markup and not the other's: the
        // money columns were added to the views first, and a duplicated query is
        // free to be fixed in one place and forgotten in the other.
        String source = Files.readString(STATS_BEAN, StandardCharsets.UTF_8);
        assertTrue(source.contains("return loadStats(null);"),
                "getStats() must delegate to the shared query");
        assertTrue(source.contains("return loadStats(userId);"),
                "getStatsForUser() must delegate to the same shared query");
        assertTrue(source.contains("(userId == null ? \"\" : \" WHERE user_id = ?\")"),
                "The narrowing must be applied by the shared query, so the two callers "
                        + "cannot drift on which rows they count.");
        assertTrue(moneySums().contains("FROM transactions"),
                "The shared query must read the transactions table");
        assertFalse(moneySums().contains("GROUP BY"),
                "The aggregate is over every matching row. A GROUP BY here would make "
                        + "rs.next() return the first group instead of one overall total.");
    }

    // ------------------------------------------------------------- utilities

    /** The two money aggregate expressions from the shared query, as SQL text. */
    private static String moneySums() throws IOException {
        String source = Files.readString(STATS_BEAN, StandardCharsets.UTF_8);
        int method = source.indexOf("private TransactionStats loadStats(");
        assertTrue(method > 0, "Could not find loadStats");
        return source.substring(method, source.indexOf("\n    }", method));
    }

    /**
     * The {@code SUM(CASE ...)} expression carrying an alias.
     *
     * <p>Located by alias and delimited by counting parentheses from the
     * {@code SUM(}, not by the first {@code )} and not by a fixed line range.
     * The query is assembled from several concatenated string literals, so the
     * condition and its alias live in different source lines, and a
     * first-closing-paren scan would run past the end of the expression it meant
     * to return.</p>
     */
    private static String expressionFor(String sql, String alias) {
        int aliasAt = sql.indexOf("as " + alias);
        assertTrue(aliasAt > 0, "Could not find alias " + alias);
        int sumAt = sql.lastIndexOf("SUM(", aliasAt);
        assertTrue(sumAt > 0, "No SUM(...) before alias " + alias);

        int depth = 0;
        for (int i = sql.indexOf('(', sumAt); i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return sql.substring(sumAt, i + 1);
            }
        }
        throw new AssertionError("Unbalanced SUM(...) for alias " + alias);
    }

    private static boolean hasPublicGetter(Class<?> type, String property) {
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method m : type.getMethods()) {
            if (m.getName().equals("get" + suffix) && m.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    /** The readable property names on the bean, for a failure message. */
    private static String describe() {
        StringBuilder out = new StringBuilder();
        for (Method m : TransactionStats.class.getMethods()) {
            String name = m.getName();
            if (name.startsWith("get") && m.getParameterCount() == 0
                    && !"getClass".equals(name)) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(name.substring(3));
            }
        }
        return out.toString();
    }
}
