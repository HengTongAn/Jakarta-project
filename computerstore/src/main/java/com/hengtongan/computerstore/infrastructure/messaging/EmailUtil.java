package com.hengtongan.computerstore.infrastructure.messaging;

import com.hengtongan.computerstore.core.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort SMTP email sender for mail-message notifications.
 * Settings are resolved via {@link AppConfig} from {@code config/mail.properties}
 * (Gmail SMTP by default), overridable by {@code -Dmail.*} system properties.
 * Never throws: when no credentials are configured, sending is skipped.
 *
 * <p>{@link #send} queues work on a background thread so checkout / status
 * updates never block on a slow SMTP round-trip.
 */
public final class EmailUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailUtil.class);

    private static final String HOST;
    private static final String PORT;
    private static final boolean AUTH;
    private static final boolean STARTTLS;
    private static final String FROM;
    private static final String USERNAME;
    private static final String PASSWORD;

    private static final ExecutorService MAIL_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "email-sender");
        t.setDaemon(true);
        return t;
    });

    static {
        HOST = AppConfig.get(null, "mail.smtp.host", "smtp.gmail.com");
        PORT = AppConfig.get(null, "mail.smtp.port", "587");
        AUTH = Boolean.parseBoolean(AppConfig.get(null, "mail.smtp.auth", "true"));
        STARTTLS = Boolean.parseBoolean(AppConfig.get(null, "mail.smtp.starttls.enable", "true"));
        FROM = AppConfig.get(null, "mail.from", "");
        USERNAME = AppConfig.get(null, "mail.username", "");
        PASSWORD = AppConfig.get(null, "mail.password", "");
    }

    private EmailUtil() {
    }

    public static boolean isConfigured() {
        return !FROM.isBlank() && !USERNAME.isBlank();
    }

    /**
     * Queues an email for background delivery. Returns {@code true} when the
     * message was accepted for sending (SMTP configured); {@code false} when
     * skipped because mail is not configured or the address is blank.
     */
    public static boolean send(String to, String subject, String text) {
        if (!isConfigured() || to == null || to.isBlank()) {
            if (!isConfigured()) {
                LOGGER.debug("Email notification skipped for \"{}\" to {} (SMTP not configured)", subject, to);
            }
            return false;
        }
        MAIL_EXECUTOR.execute(() -> sendSync(to, subject, text));
        return true;
    }

    /** Actual SMTP send; runs only on the mail executor. */
    private static boolean sendSync(String to, String subject, String text) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", HOST);
            props.put("mail.smtp.port", PORT);
            props.put("mail.smtp.auth", String.valueOf(AUTH));
            props.put("mail.smtp.starttls.enable", String.valueOf(STARTTLS));
            // Always verify the mail server's certificate/hostname identity so
            // a hijacked connection cannot impersonate the SMTP relay.
            props.put("mail.smtp.ssl.checkserveridentity", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(USERNAME, PASSWORD);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(text);
            Transport.send(message);
            LOGGER.info("Email sent to {} subject=\"{}\"", to, subject);
            return true;
        } catch (MessagingException e) {
            LOGGER.warn("Failed to send email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    /** Stops the mail worker threads on webapp shutdown. */
    public static void shutdown() {
        MAIL_EXECUTOR.shutdown();
        try {
            if (!MAIL_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                MAIL_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            MAIL_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}