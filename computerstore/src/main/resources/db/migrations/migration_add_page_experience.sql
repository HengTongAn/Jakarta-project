-- Experienced Page Time (EPT) samples: real-user monitoring for the storefront.
--
-- WHY A TABLE
-- Everything /admin/performance showed before this was read out of the JVM: cache
-- hit rates, pool occupancy, query durations. All of it describes the server, and
-- none of it answers the question an operator actually has, which is "why does it
-- feel slow to my customers". EPT is a browser-side number -- how long until the
-- page is in a state a person can click -- so it has to be measured in a browser
-- and reported back. A histogram in memory cannot answer that, because the
-- interesting question is comparative: which page type is slowest, and is that
-- specific to one browser.
--
-- WHAT IS DELIBERATELY ABSENT
-- No IP address, no user id, no session id, no full URL, no raw User-Agent.
-- Each of those is either personal data this feature has no need for, or an
-- unbounded-cardinality key that would turn "which page type is slowest" into a
-- list of single visits. `browser` is a coarse family and `page_type` is a route
-- pattern, both bounded by construction. See PageType and UserAgentClassifier.
--
-- WHY TWO SETS OF TIMINGS
-- Every column here is measured by the browser and reported by rum.js. The split
-- that matters is between `server_ms` and the interactive/load figures: if the
-- server think time is 90ms and the page only became clickable at 3000ms, then
-- the server is not the problem and the connection or the page weight is.
-- Reporting only one of these would make the number unactionable.
--
-- `server_ms` is responseStart - requestStart, so it excludes connection setup
-- and TLS; `ttfb_ms` is responseStart from navigation start, so it includes
-- them. The gap between the two is what a slow connection looks like.
-- This is NOT the server's own in-container timing -- that cannot reach a script
-- at all, because it is not known until the response has been written. It lives
-- in MetricsCollector under ExperienceFilter.METRIC_SERVER_MS instead.
--
-- The client columns are NULL for rows where the browser reported no timing for
-- that particular phase.
--
-- SAMPLE ROWS
-- Only a sampled fraction of page views is stored, so the counts here describe
-- the sample rather than total traffic. The admin view shows the rate and the
-- sample size; latency percentiles stay valid under uniform sampling even though
-- the volume does not.
CREATE TABLE IF NOT EXISTS page_experience_samples (
    sample_id      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    page_type      VARCHAR(64)  NOT NULL,
    browser        VARCHAR(24)  NOT NULL,
    device         VARCHAR(16)  NOT NULL DEFAULT 'unknown',
    -- Server think time as the browser experienced it: responseStart - requestStart.
    -- NOT the server's own measurement. See the note above.
    server_ms      INT          NOT NULL DEFAULT 0,
    -- Time to first byte from navigation start, so including connection setup.
    ttfb_ms        INT          NOT NULL DEFAULT 0,
    -- Browser-observed, milliseconds from navigation start. NULL when unreported.
    interactive_ms INT          NULL,
    dom_ready_ms   INT          NULL,
    load_ms        INT          NULL,
    transfer_bytes INT          NOT NULL DEFAULT 0,
    sampled_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Every report filters by window first, then groups. These orders let the
    -- window predicate be an index range scan rather than a full table scan.
    INDEX idx_page_exp_time (sampled_at),
    INDEX idx_page_exp_type_time (page_type, sampled_at),
    INDEX idx_page_exp_browser_time (browser, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
