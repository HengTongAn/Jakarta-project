package com.example.computer_store.util.time;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Day/time boundaries for date-bounded queries, computed in the JVM's local
 * zone so they match what the UI displays.
 *
 * <p>Timestamps are stored as UTC instants and rendered in the server's local
 * time (e.g. {@code fmt:formatDate}), but MySQL's {@code CURDATE()}/{@code NOW()}
 * run in the JDBC session time zone, which is pinned to UTC via
 * {@code serverTimezone=UTC}. Comparing against DB-side "today" then mis-filters
 * rows created between local midnight and UTC midnight. Binding boundaries
 * computed here instead keeps every day-bounded query aligned with the
 * displayed local clock.</p>
 */
public final class TimeBoundaries {

    private TimeBoundaries() {
    }

    /** Local midnight (start of {@code date}) as a {@code Timestamp} instant. */
    public static Timestamp startOfDay(LocalDate date) {
        return new Timestamp(startOfDayMillis(date, ZoneId.systemDefault()));
    }

    /** Local midnight of today. */
    public static Timestamp todayStart() {
        return startOfDay(LocalDate.now());
    }

    public static Timestamp minutesAgo(int minutes) {
        return secondsAgo(minutes * 60L);
    }

    public static Timestamp hoursAgo(int hours) {
        return secondsAgo(hours * 3600L);
    }

    public static Timestamp daysAgo(int days) {
        return secondsAgo(days * 86400L);
    }

    /** {@code seconds} whole seconds before now (a rolling window, not a day boundary). */
    public static Timestamp secondsAgo(long seconds) {
        return new Timestamp(System.currentTimeMillis() - Math.max(0, seconds) * 1000L);
    }

    /** Epoch millis of local midnight of {@code date} in the given zone (testable). */
    public static long startOfDayMillis(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }
}