package com.example.computer_store.util;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.Properties;

/**
 * Best-effort SMTP email sender for mail-message notifications.
 * Reads settings from mail.properties (Gmail SMTP by default).
 * Never throws: when no credentials are configured, sending is skipped.
 */
public final class EmailUtil {

    private static final String PROPERTIES_FILE = "/mail.properties";

    private static final Properties PROPS = new Properties();
    private static final String HOST;
    private static final String PORT;
    private static final boolean AUTH;
    private static final boolean STARTTLS;
    private static final String FROM;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        try (InputStream in = EmailUtil.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (Exception ignored) {
            // email is best-effort; never crash the app on bad config
        }
        HOST = PROPS.getProperty("mail.smtp.host", "smtp.gmail.com");
        PORT = PROPS.getProperty("mail.smtp.port", "587");
        AUTH = Boolean.parseBoolean(PROPS.getProperty("mail.smtp.auth", "true"));
        STARTTLS = Boolean.parseBoolean(PROPS.getProperty("mail.smtp.starttls.enable", "true"));
        FROM = PROPS.getProperty("mail.from", "");
        USERNAME = PROPS.getProperty("mail.username", "");
        PASSWORD = PROPS.getProperty("mail.password", "");
    }

    private EmailUtil() {
    }

    public static boolean isConfigured() {
        return !FROM.isBlank() && !USERNAME.isBlank();
    }

    public static boolean send(String to, String subject, String text) {
        if (!isConfigured() || to == null || to.isBlank()) {
            return false;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", HOST);
            props.put("mail.smtp.port", PORT);
            props.put("mail.smtp.auth", String.valueOf(AUTH));
            props.put("mail.smtp.starttls.enable", String.valueOf(STARTTLS));

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
            return true;
        } catch (MessagingException e) {
            System.err.println("[EmailUtil] Failed to send email to " + to + ": " + e.getMessage());
            return false;
        }
    }
}