package com.example.computer_store;

import com.example.computer_store.util.time.TimeBoundaries;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeBoundariesTest {

    @Test
    void startOfDayMillisIsLocalMidnightInZone() {
        LocalDate day = LocalDate.of(2026, 9, 23);
        // UTC midnight of the same date
        assertEquals(Instant.parse("2026-09-23T00:00:00Z").toEpochMilli(),
                TimeBoundaries.startOfDayMillis(day, ZoneId.of("UTC")));
        // Phnom_Penh (+07) midnight = 17:00 UTC on the previous day
        assertEquals(Instant.parse("2026-09-22T17:00:00Z").toEpochMilli(),
                TimeBoundaries.startOfDayMillis(day, ZoneId.of("Asia/Phnom_Penh")));
    }

    @Test
    void startOfDayIsLocalMidnightNotUtcMidnight() {
        // The regression this guards against: on a UTC JDBC session, a plain
        // java.sql.Date parameter is written as 'YYYY-MM-DD 00:00:00' UTC,
        // which on an +07 box is 07:00 local - dropping the early-morning rows.
        // The boundary must follow the JVM's local zone instead.
        LocalDate day = LocalDate.of(2026, 9, 23);
        Timestamp ts = TimeBoundaries.startOfDay(day);
        long expect = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(expect, ts.getTime());
        long offsetMillis = ZoneId.systemDefault().getRules()
                .getOffset(Instant.ofEpochMilli(expect)).getTotalSeconds() * 1000L;
        if (offsetMillis != 0) {
            long utcMidnight = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            assertNotEquals(utcMidnight, ts.getTime(),
                    "startOfDay must follow the local zone, not UTC midnight");
        }
    }

    @Test
    void todayStartIsNotInTheFutureAndIsADayBoundary() {
        long now = System.currentTimeMillis();
        long todayStart = TimeBoundaries.todayStart().getTime();
        assertTrue(todayStart <= now, "today start must not be in the future");
        assertTrue(now - todayStart <= 24L * 3600 * 1000, "today start too far back");
        long offsetMillis = ZoneId.systemDefault().getRules()
                .getOffset(Instant.ofEpochMilli(todayStart)).getTotalSeconds() * 1000L;
        assertEquals(0L, (todayStart + offsetMillis) % (24L * 3600 * 1000),
                "local wall-clock time of the boundary must be 00:00");
    }

    @Test
    void secondsAgoIsRollingWindowNotDayBoundary() {
        long before = System.currentTimeMillis();
        Timestamp past = TimeBoundaries.secondsAgo(60);
        long after = System.currentTimeMillis();
        assertTrue(past.getTime() >= before - 60_000L - 1_000L);
        assertTrue(past.getTime() <= after - 59_000L + 1_000L);
    }

    @Test
    void hoursAgoAndDaysAgoAreConsistent() {
        long h = TimeBoundaries.hoursAgo(24).getTime();
        long d = TimeBoundaries.daysAgo(1).getTime();
        assertTrue(Math.abs(h - d) < 5_000L, "24h must roughly equal 1 day");
    }

    @Test
    void zeroWindowIsClampedToNow() {
        long before = System.currentTimeMillis();
        Timestamp t = TimeBoundaries.secondsAgo(0);
        long after = System.currentTimeMillis();
        assertTrue(t.getTime() >= before - 1_000L && t.getTime() <= after);
    }
}