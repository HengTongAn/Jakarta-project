package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.PageExperienceSample;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The JDBC boundary where an unreported timing can silently become a fast one.
 *
 * <h2>Why this class exists</h2>
 *
 * A defect was found here against real data, not predicted. Two rows whose browser
 * timings were all NULL were reported by the live query with a client p50 of
 * {@code 0}, and appeared in {@code clientReported} as though they had measured
 * something. They had not: the browser never sent a timing for those phases, the
 * servlet wrote NULL, and {@code ResultSet.getInt} hands NULL back as {@code 0}.
 *
 * <p>The failure mode is the dangerous direction. A page that never became
 * interactive would be counted as the fastest page on the site, and would drag
 * down the median and the p95 of whatever page type it belonged to -- the exact
 * figures an operator acts on. Nothing threw, nothing logged, and the report
 * looked healthy.
 *
 * <h2>Why it needs a test at all</h2>
 *
 * {@code DBConnection.getConnection} is static and this project runs Mockito's
 * subclass mock maker with {@code mockito.inline} off, so the query cannot be
 * stubbed as a unit. {@code ResultSet} is an interface, though, so the one
 * conversion that matters can be driven directly -- and it is the conversion whose
 * contract is easy to get wrong, because {@code getInt} on a NULL column does not
 * fail and does not return anything that looks absent.
 */
class PageExperienceReportTest {

    /**
     * The regression. A NULL column must come back as the sentinel, not as zero.
     */
    @Test
    void aNullTimingIsUnreportedRatherThanInstant() throws SQLException {
        ResultSet rs = nullColumn();
        int value = PageExperienceRepository.reportedOrUnreported(rs, "interactive_ms");

        assertEquals(PageExperienceSample.UNREPORTED, value,
                "getInt answered 0 for a SQL NULL and the sentinel was not restored. This row "
                        + "now enters the percentile list as a genuine zero, so a page that never "
                        + "finished rendering is reported as the fastest page on the site.");
    }

    /**
     * The other direction, which is just as easy to break.
     *
     * <p>A phase that genuinely completed in under a millisecond is stored as {@code 0},
     * and {@code wasNull()} correctly reports {@code false} for it. If this were
     * collapsed into the NULL case the report would discard real fast measurements --
     * and {@code 0} is a legitimate EPT for a cached page served from memory, so this
     * is not a theoretical value.</p>
     */
    @Test
    void aMeasuredZeroIsNotConfusedWithAnAbsentOne() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("interactive_ms")).thenReturn(0);
        when(rs.wasNull()).thenReturn(false);

        assertEquals(0, PageExperienceRepository.reportedOrUnreported(rs, "interactive_ms"),
                "a stored 0 is a real measurement and must survive the read. Treating it as "
                        + "unreported would discard the fastest pages from the report, which is "
                        + "a real category: a page served from cache is interactive almost "
                        + "immediately.");
    }

    @Test
    void aMeasuredTimingIsReturnedUnchanged() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("interactive_ms")).thenReturn(1450);
        when(rs.wasNull()).thenReturn(false);

        assertEquals(1450, PageExperienceRepository.reportedOrUnreported(rs, "interactive_ms"));
    }

    /**
     * Pins the sentinel to a negative value.
     *
     * <p>{@code reportedOrUnreported} is only correct because downstream code compares
     * against {@code < 0}. Changing {@code UNREPORTED} to {@code 0} would not fail any
     * compilation and would reinstate exactly the bug this class documents: every
     * {@code r[0] >= 0} guard would admit the unreported rows again.</p>
     */
    @Test
    void theSentinelIsNegativeSoTheExistingGuardsStillWork() {
        assertTrue(PageExperienceSample.UNREPORTED < 0,
                () -> "UNREPORTED is " + PageExperienceSample.UNREPORTED
                        + ", which is not negative. The bucket and report code excludes "
                        + "unreported rows with a `< 0` test, so a zero sentinel would let "
                        + "them all back in.");
    }

    private static ResultSet nullColumn() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        // Exactly what the JDBC driver does for a NULL INT column.
        when(rs.getInt("interactive_ms")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        return rs;
    }
}