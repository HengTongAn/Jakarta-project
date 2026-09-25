package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.OrderStatusEvent;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.messaging.EmailUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sends best-effort, real-time email notifications for important store
 * events (order placed, order status changed, ...). Every event type is a
 * small method built on top of the generic {@link #send send} helper, so
 * adding a new notification (welcome email, password changed, low stock,
 * ...) is just one more method plus a template.
 *
 * Never throws: like the audit log, a failing notification must never be
 * able to break the business action that triggered it.
 */
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private static final String STORE_NAME = "TechStore";
    private static final String ENABLED_PROPERTY = "computerstore.mail.notifications.enabled";

    private final UserService userService;

    public NotificationService() {
        this(new UserService());
    }

    public NotificationService(UserService userService) {
        this.userService = userService;
    }

    /** Order confirmation, sent right after a successful checkout. */
    public void sendOrderPlaced(Order order) {
        if (order == null) {
            return;
        }
        sendToCustomer(order,
                STORE_NAME + ": Order #" + order.getOrderId() + " received",
                "Hi,\n\n"
                + "Your order #" + order.getOrderId() + " has been placed.\n\n"
                + "Order number: " + order.getOrderId() + "\n"
                + "Order date: " + order.getOrderDate() + "\n"
                + "Total amount: $" + money(order.getTotalAmount()) + "\n\n"
                + "We are getting your items ready. You can follow every step "
                + "on your \"My Orders\" page.\n\n"
                + "- " + STORE_NAME);
    }

    /** Current-status update, sent in real time for every status change. */
    public void sendOrderStatusChanged(Order order, OrderStatusEvent event) {
        if (order == null || event == null || event.getToStatus() == null) {
            return;
        }
        StringBuilder body = new StringBuilder()
                .append("Hi,\n\n")
                .append("Your order #").append(order.getOrderId())
                .append(" moved to a new status.\n\n")
                .append("New status: ").append(label(event.getToStatus()));
        if (event.getFromStatus() != null) {
            body.append(" (previously ").append(label(event.getFromStatus())).append(")");
        }
        body.append("\n")
                .append("Updated by: ")
                .append(event.getChangedBy() == null ? "the system" : event.getChangedBy())
                .append("\n");
        if (event.getNote() != null && !event.getNote().isBlank()) {
            body.append("Note: ").append(event.getNote()).append("\n");
        }
        body.append("\n")
                .append("Track your order on your \"My Orders\" page.\n\n")
                .append("- ").append(STORE_NAME);

        sendToCustomer(order,
                STORE_NAME + ": Order #" + order.getOrderId() + " is now " + label(event.getToStatus()),
                body.toString());
    }

    /**
     * Generic, reusable sender for any future notification type. Silently
     * does nothing when notifications are not configured or disabled, but
     * always logs what it decided so the flow is easy to observe.
     */
    public void send(String to, String subject, String body) {
        if (!enabled()) {
            LOGGER.debug("Email notification skipped for \"{}\" to {} {}", subject, to,
                    EmailUtil.isConfigured()
                            ? "(notifications disabled by computerstore.mail.notifications.enabled)"
                            : "(SMTP not configured; fill config/mail.properties)");
            return;
        }
        if (to == null || to.isBlank()) {
            return;
        }
        boolean queued = EmailUtil.send(to, subject, body);
        LOGGER.info("Email {} to {} subject=\"{}\"", queued ? "queued" : "NOT queued", to, subject);
    }

    private void sendToCustomer(Order order, String subject, String body) {
        try {
            User customer = userService.get(order.getUserId());
            send(customer.getEmail(), subject, body);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not notify customer of order #{}: {}", order.getOrderId(), e.getMessage());
        }
    }

    private boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))
                && EmailUtil.isConfigured();
    }

    private String money(BigDecimal value) {
        return value == null
                ? "0.00"
                : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String label(Order.Status status) {
        return switch (status) {
            case PENDING -> "Pending";
            case PROCESSING -> "Processing";
            case SHIPPED -> "Shipped";
            case COMPLETED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case REFUNDED -> "Refunded";
        };
    }
}