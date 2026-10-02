package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.domain.entity.PageExperienceSample;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository.ExperienceReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Real-user performance data: stores what browsers reported and builds the report.
 *
 * <h2>Why this service swallows its own failures</h2>
 *
 * Every method here is called either from a beacon that a shopper's browser is
 * waiting on, or from an admin dashboard. Neither is a place where an analytics
 * table being unavailable should become the user's problem. A failed sample is
 * dropped and a failed report renders as an empty page rather than a 500 on
 * {@code /admin/performance}.
 *
 * <p>The alternative -- letting the SQLException propagate -- would mean either a
 * 204 that never arrives, or an admin page that is down because a table is missing.
 * Both make performance data look like an application failure, which trains people
 * to ignore this page. So failures are logged at WARN, visible to an operator and
 * invisible to a customer.</p>
 *
 * <p>{@link #reportOrNull()} is the one an admin page should call, and the reason
 * it exists is that an empty report and a failed report must not look the same to
 * the page: a dashboard that renders "no data yet" when the table is broken is
 * quieter but more misleading than one that renders a warning.</p>
 */
public class PageExperienceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageExperienceService.class);

    private final PageExperienceRepository repository = new PageExperienceRepository();

    /** Stores one sample. Returns false when it could not be stored. */
    public boolean record(PageExperienceSample sample) {
        try {
            repository.insert(sample);
            return true;
        } catch (SQLException e) {
            LOGGER.warn("Could not store page experience sample for {}", sample.getPageType(), e);
            return false;
        }
    }

    /**
     * The report over the retention window, or {@code null} if it could not be built.
     *
     * <p>Null rather than an empty report so the caller can tell "nothing sampled
     * yet" from "the query failed" -- they look identical otherwise, and only one of
     * them means the instrumentation is broken.</p>
     */
    public ExperienceReport reportOrNull() {
        try {
            return repository.report();
        } catch (SQLException e) {
            LOGGER.warn("Could not build the page experience report", e);
            return null;
        }
    }

    /** Deletes samples past the retention window. Returns rows removed, or -1 on failure. */
    public int purge(int days) {
        try {
            return repository.purgeOlderThan(days);
        } catch (SQLException e) {
            LOGGER.warn("Could not purge page experience samples", e);
            return -1;
        }
    }

    public int retentionDays() {
        return PageExperienceRepository.DEFAULT_RETENTION_DAYS;
    }
}
