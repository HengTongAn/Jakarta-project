package com.example.computer_store.config;

import com.example.computer_store.dao.AuditLogDAO;
import com.example.computer_store.util.AuditLogger;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppContextListener.class.getName());

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
	AppContext.init();
	sce.getServletContext().setAttribute(ATTR_NAME, AppContext.get());
	startRetentionTask();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
	if (retentionScheduler != null) {
	    retentionScheduler.shutdownNow();
	}
	sce.getServletContext().removeAttribute(ATTR_NAME);
    }

    /**
     * Enforcement retention by policy: aged audit records are moved to the
     * archive automatically on a schedule, like a real SIEM would.
     */
    private void startRetentionTask() {
	LOGGER.info("Starting audit retention task (archives records older than "
		+ RETENTION_DAYS + " days)");
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
	    int archived = new AuditLogDAO().archiveBefore(cutoff);
	    if (archived > 0) {
		LOGGER.info("Audit retention archived " + archived + " record(s) older than "
			+ RETENTION_DAYS + " days");
		AuditLogger.logSystemEvent("AUTO_ARCHIVE",
			"Retention archived " + archived + " record(s) older than "
				+ RETENTION_DAYS + " days");
	    }
	} catch (Exception e) {
	    LOGGER.log(Level.WARNING, "Audit retention run failed", e);
	}
    }
}
