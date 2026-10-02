package com.hengtongan.computerstore.core.config;

import com.hengtongan.computerstore.core.repository.AuditLogRepository;
import com.hengtongan.computerstore.infrastructure.realtime.EventHub;
import com.hengtongan.computerstore.infrastructure.security.TwoFactorAuthService;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.security.DefaultCredentialsChecker;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppContextListener.class);

    public static final String ATTR_NAME = "appContext";

    private static final long RETENTION_DAYS =
            Long.getLong("computerstore.audit.retention.days", 730L);
    private static final long RETENTION_INITIAL_DELAY_MS =
            Long.getLong("computerstore.audit.retention.initialDelayMs", 60_000L);
    private static final long RETENTION_PERIOD_MS =
            Long.getLong("computerstore.audit.retention.periodMs", 86_400_000L);

    private ScheduledExecutorService retentionScheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("Initializing ComputerStore application...");

        // Validate 2FA configuration if enabled
        validateTwoFactorConfig();

        // JSTL's fmt: tags render raw toString() output unless a locale is set
        // in the request context. Not a startup action, but it belongs next to
        // the other "something silently degrades if unconfigured" notes.
        LOGGER.info("JSTL fmt: locale is pinned by <fmt:setLocale> in header.jspf "
                + "(a system property is not read by this JSTL build)");

        AppContext.init();
        sce.getServletContext().setAttribute(ATTR_NAME, AppContext.get());

        // Flag any seeded default account (admin/admin123, customer/customer123)
        // still present. Never blocks startup; checkAndLog() degrades to a no-op
        // if the DB is not reachable yet.
        DefaultCredentialsChecker.checkAndLog();

        // Pre-warm connection pool for better first-request performance
        preWarmConnectionPool();

        startRetentionTask();

        LOGGER.info("ComputerStore application initialized successfully");
    }

    private void validateTwoFactorConfig() {
        // Check if 2FA is enabled (has any enabled secrets in DB)
        // We can't check DB here because connection pool may not be initialized yet
        // Instead, we validate the encryption key is present if 2FA will be used
        String key = AppConfig.get("COMPUTERSTORE_2FA_ENCRYPTION_KEY",
                "computerstore.2fa.encryption.key", null);
        if (key != null && !key.isBlank()) {
            try {
                TwoFactorAuthService.validateConfiguration();
            } catch (IllegalStateException e) {
                LOGGER.error("2FA configuration validation failed: {}", e.getMessage());
                // Don't fail startup - 2FA is optional, but log the error
            }
        } else {
            LOGGER.warn("2FA encryption key not configured (COMPUTERSTORE_2FA_ENCRYPTION_KEY). 2FA will not be available.");
        }
    }

    /**
     * Documents why {@code header.jspf} must call {@code <fmt:setLocale>}.
     *
     * <p>Kept here as the prose home for a defect whose symptom is 40 files away
     * from its cause. See {@code LocaleFormatTest} for the executable guard.</p>
     *
     * <h2>What was wrong</h2>
     *
     * <p>Every {@code fmt:formatNumber} in this app rendered raw
     * {@code Object.toString()} output, so a page weight read
     * {@code 87.0939453125 KB} rather than {@code 87.1 KB} and every
     * {@code pattern}/{@code type}/{@code maxFractionDigits} on those tags did
     * nothing. All 107 such calls across the views were affected. It survived
     * because most values are integers, and because a {@code null} formatting
     * locale is not an error -- pages returned 200 throughout.</p>
     *
     * <h2>Why the app shipped broken</h2>
     *
     * <p>The JSTL implementation this app ships --
     * {@code org.glassfish.web:jakarta.servlet.jsp.jstl}, which is Apache
     * Taglibs Standard repackaged under the {@code jakarta.tags.*} URIs -- returns
     * {@code null} from {@code getFormattingLocale} when nothing has configured a
     * locale. {@code FormatNumberSupport.doEndTag} branches on exactly that:</p>
     *
     * <pre>
     *   150: invokestatic  getFormattingLocale:(LPageContext;LTag;ZZ)LLocale;
     *   154: aload_3
     *   155: ifnull 288        &lt;- 288 is Object.toString()
     * </pre>
     *
     * <p>A system property cannot fix it: {@code SetLocaleSupport} in this build
     * never calls {@code System.getProperty}, so
     * {@code -Djakarta.servlet.jsp.jstl.fmt.locale} is read by nothing. Setting
     * it and logging success is worse than doing nothing, because it looks fixed.
     * Only {@code <fmt:setLocale>}, which stores a {@code Locale} in the request
     * context, is honoured.</p>
     *
     * <h2>Why this cannot be fixed from here</h2>
     *
     * <p>The obvious fix -- a system property, which would belong in a startup
     * hook like this one -- does not work. It was tried first and the numbers
     * stayed unformatted while the method logged success, which is worse than
     * not trying. See {@link #documentFormattingLocaleRequirement()}.</p>
     *
     * <p>The redundant {@code fmt} taglib declaration in {@code header.jspf} is
     * deliberate. 25 views already declare that prefix, and static includes merge
     * into one translation unit, but redeclaring a prefix to the same URI is
     * harmless -- verified by loading all 14 admin and 8 customer pages.</p>
     */
    private void documentFormattingLocaleRequirement() {
        // No-op body. The javadoc above is the point: the symptom appears in
        // every view and the cause is a null check inside a third-party jar, so
        // nothing else in the codebase hints at the coupling. See
        // LocaleFormatTest for the guard that fails the build if the line goes.
    }

    /**
     * Pre-warms the connection pool to ensure connections are ready for first requests.
     * This significantly improves first-request latency after application startup.
     */
    private void preWarmConnectionPool() {
        if (!"true".equalsIgnoreCase(System.getProperty("computerstore.pool.preWarm", "true"))) {
            return;
        }

        int warmConnections = Integer.getInteger("computerstore.pool.preWarm.count", 10);
        LOGGER.info("Pre-warming connection pool with {} connections...", warmConnections);

        try {
            for (int i = 0; i < warmConnections; i++) {
                try (java.sql.Connection conn = DBConnection.getConnection()) {
                    // Execute a simple query to ensure connection is fully initialized
                    try (java.sql.Statement stmt = conn.createStatement();
                         java.sql.ResultSet rs = stmt.executeQuery("SELECT 1")) {
                        rs.next();
                    }
                }
            }
            LOGGER.info("Connection pool pre-warming completed successfully");
        } catch (Exception e) {
            LOGGER.warn("Connection pool pre-warming failed (non-critical): {}", e.getMessage());
            // Pre-warming failure should not prevent application startup
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("Shutting down ComputerStore application...");

        if (retentionScheduler != null) {
            // Graceful stop: let an in-flight archive finish (it is idempotent),
            // then force-stop only if it ignores the shutdown signal.
            retentionScheduler.shutdown();
            try {
                if (!retentionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Audit retention task did not stop within 5s; forcing shutdown");
                    retentionScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                retentionScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        EventHub.shutdown();
        DBConnection.shutdown();
        sce.getServletContext().removeAttribute(ATTR_NAME);

        LOGGER.info("ComputerStore application shutdown complete");

        // Release logback's async-appender worker threads on (re)deploy.
        // Without this Tomcat warns about leaked AsyncAppender-Worker threads
        // and every redeploy can accumulate a second set of appenders.
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof ch.qos.logback.classic.LoggerContext logbackContext) {
            logbackContext.stop();
        }
    }

    /**
     * Enforcement retention by policy: aged audit records are moved to the
     * archive automatically on a schedule, like a real SIEM would.
     */
    private void startRetentionTask() {
        LOGGER.info("Starting audit retention task (archives records older than {} days)", RETENTION_DAYS);
        retentionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "audit-retention");
            t.setDaemon(true);
            return t;
        });
        retentionScheduler.scheduleWithFixedDelay(this::runRetention,
                RETENTION_INITIAL_DELAY_MS, RETENTION_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private void runRetention() {
        try {
            LocalDate cutoff = LocalDate.now().minusDays(RETENTION_DAYS);
            int archived = new AuditLogRepository().archiveBefore(cutoff);
            if (archived > 0) {
                LOGGER.info("Audit retention archived {} record(s) older than {} days", archived, RETENTION_DAYS);
                AuditLogger.logSystemEvent("AUTO_ARCHIVE",
                        "Retention archived " + archived + " record(s) older than "
                                + RETENTION_DAYS + " days");
            }
        } catch (Exception e) {
            LOGGER.warn("Audit retention run failed", e);
        }
    }
}