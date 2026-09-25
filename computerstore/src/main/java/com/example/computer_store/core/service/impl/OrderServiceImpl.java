package com.example.computer_store.core.service.impl;

import com.example.computer_store.core.service.OrderService;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.core.repository.CartRepository;
import com.example.computer_store.core.repository.InventoryRepository;
import com.example.computer_store.core.repository.OrderRepository;
import com.example.computer_store.core.repository.ProductRepository;
import com.example.computer_store.core.exception.InsufficientStockException;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.CartItem;
import com.example.computer_store.core.domain.entity.InventoryLog;
import com.example.computer_store.core.domain.entity.Order;
import com.example.computer_store.core.domain.entity.OrderItem;
import com.example.computer_store.core.domain.entity.OrderStatusEvent;
import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.infrastructure.realtime.EventHub;
import com.example.computer_store.core.service.NotificationService;
import com.example.computer_store.util.web.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.computer_store.infrastructure.persistence.ConnectionProvider;
import com.example.computer_store.infrastructure.persistence.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final CartRepository cartDAO;
    private final ProductRepository productDAO;
    private final OrderRepository orderDAO;
    private final InventoryRepository inventoryDAO;
    private final ConnectionProvider connectionProvider;
    private final NotificationService notificationService;

    public OrderServiceImpl() {
        this(new CartRepository(), new ProductRepository(), new OrderRepository(), new InventoryRepository());
    }

    public OrderServiceImpl(CartRepository cartDAO, ProductRepository productDAO,
                            OrderRepository orderDAO, InventoryRepository inventoryDAO) {
        this(cartDAO, productDAO, orderDAO, inventoryDAO, DBConnection::getConnection);
    }

    public OrderServiceImpl(CartRepository cartDAO, ProductRepository productDAO,
                            OrderRepository orderDAO, InventoryRepository inventoryDAO,
                            ConnectionProvider connectionProvider) {
        this(cartDAO, productDAO, orderDAO, inventoryDAO, connectionProvider,
                new NotificationService());
    }

    public OrderServiceImpl(CartRepository cartDAO, ProductRepository productDAO,
                            OrderRepository orderDAO, InventoryRepository inventoryDAO,
                            ConnectionProvider connectionProvider,
                            NotificationService notificationService) {
        this.cartDAO = cartDAO;
        this.productDAO = productDAO;
        this.orderDAO = orderDAO;
        this.inventoryDAO = inventoryDAO;
        this.connectionProvider = connectionProvider;
        this.notificationService = notificationService;
    }

    /**
     * Completes a checkout inside one database transaction:
     * validate cart -> check & reduce stock -> create order -> create order
     * items -> commit. Any failure rolls the whole transaction back so stock
     * never becomes inconsistent.
     */
    @Override
    public Order checkout(User user) {
        int userId = user.getUserId();
        List<CartItem> items = cartDAO.findItemsByUser(userId);
        if (items.isEmpty()) {
            throw new ValidationException("Your cart is empty.");
        }

        Connection conn = null;
        Order order = new Order();
        // True per-product stock after this transaction reserved the units,
        // read on the same connection so the log entries and the real-time
        // broadcast never contradict the database.
        Map<Integer, Integer> soldRemaining = new HashMap<>();
        try {
            conn = connectionProvider.getConnection();
            conn.setAutoCommit(false);

            BigDecimal total = BigDecimal.ZERO;
            for (CartItem item : items) {
                Product product = item.getProduct();
                if (product == null || product.getStatus() == Product.Status.DISCONTINUED) {
                    throw new ValidationException("A product in your cart is no longer available.");
                }
                if (item.getQuantity() <= 0) {
                    throw new ValidationException("A product in your cart has an invalid quantity.");
                }
                if (!productDAO.reduceStock(conn, item.getProductId(), item.getQuantity())) {
                    throw new InsufficientStockException(
                            "Insufficient stock for \"" + product.getName() + "\". "
                                    + "Only " + product.getStockQuantity() + " available.");
                }
                int remaining = productDAO.stock(conn, item.getProductId()).stockQuantity;
                soldRemaining.put(item.getProductId(), remaining);
                total = total.add(product.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }

            order.setUserId(userId);
            order.setTotalAmount(total);
            order.setStatus(Order.Status.PENDING);
            int orderId = orderDAO.createOrder(conn, order);

            OrderStatusEvent createdEvent = new OrderStatusEvent();
            createdEvent.setOrderId(orderId);
            createdEvent.setFromStatus(null);
            createdEvent.setToStatus(Order.Status.PENDING);
            createdEvent.setChangedBy(user.getUsername());
            createdEvent.setNote("Order placed");
            orderDAO.insertStatusEvent(conn, createdEvent);

            for (CartItem item : items) {
                Product product = item.getProduct();

                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(orderId);
                orderItem.setProductId(item.getProductId());
                orderItem.setQuantity(item.getQuantity());
                orderItem.setUnitPrice(product.getPrice());
                orderItem.setSubtotal(product.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
                orderDAO.createOrderItem(conn, orderItem);

                InventoryLog log = new InventoryLog();
                log.setProductId(item.getProductId());
                log.setOldQuantity(product.getStockQuantity());
                log.setNewQuantity(soldRemaining.get(item.getProductId()));
                log.setAction("ORDER_CREATED");
                log.setUserId(null);
                inventoryDAO.addLog(conn, log);
            }

            // Keep cart removal in this transaction. Clearing it after the
            // commit on a second connection could leave a completed order
            // behind while reporting checkout as failed.
            cartDAO.clear(conn, userId);
            conn.commit();
            // The transaction is durable now: evict cached product rows so the
            // storefront reflects the lowered stock immediately. Doing this
            // after commit avoids a concurrent reader re-caching pre-commit stock.
            for (CartItem item : items) {
                ProductRepository.invalidateProductCache(item.getProductId());
            }
            // Order counts / revenue on the admin dashboard changed, and the
            // storefront "trending" rows now include the new sales.
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateAllDashboard();
                CacheManager.invalidateAllCatalog();
            }
            order.setOrderId(orderId);
            AuditLogger.logDataModification("ORDER_CREATED", user.getUsername(), "ORDER",
                    String.valueOf(orderId), "Checkout total " + total);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Order processing failed. Please try again.", e);
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(conn);
        }

        // Notification happens strictly after the commit succeeds, so a
        // rolled-back order can never produce an email.
        notificationService.sendOrderPlaced(order);

        // Push the lowered stock to every connected browser, then announce
        // the new order so admin views and the customer's order pages update.
        for (CartItem item : items) {
            int remaining = soldRemaining.get(item.getProductId());
            EventHub.publishStock(item.getProductId(), remaining,
                    Product.computeStatus(remaining).name());
        }
        EventHub.publishOrder(order.getOrderId(), userId, order.getStatus().name());
        return order;
    }

    @Override
    public List<Order> getOrdersForUser(int userId) {
        return orderDAO.findByUserId(userId);
    }

    /**
     * Returns the order only when it belongs to the given customer.
     */
    @Override
    public Order getOrderForUser(int orderId, int userId) {
        Order order = orderDAO.findByIdForUser(orderId, userId);
        if (order == null) {
            throw new NotFoundException("Order does not exist.");
        }
        return order;
    }

    @Override
    public List<Order> getAllOrders() {
        return orderDAO.findAll();
    }

    @Override
    public List<Order> getOrdersByStatus(Order.Status status) {
        return orderDAO.findByStatus(status);
    }

    @Override
    public List<Order> getOrdersPage(Order.Status status, int offset, int limit) {
        return orderDAO.findPage(status, offset, limit);
    }

    @Override
    public int countOrders(Order.Status status) {
        return orderDAO.count(status);
    }

    @Override
    public Order getOrder(int orderId) {
        Order order = orderDAO.findById(orderId);
        if (order == null) {
            throw new NotFoundException("Order does not exist.");
        }
        return order;
    }

    /**
     * Convenience overload used by tests and system-triggered changes. The
     * transition is attributed to the SYSTEM actor.
     */
    @Override
    public void updateStatus(int orderId, Order.Status newStatus) {
        updateStatus(orderId, newStatus, "SYSTEM", null);
    }

    /**
     * Updates the order status inside one transaction, records the change on
     * the order timeline, and returns cancelled or refunded items back to
     * stock (with inventory log entries) so the totals always balance.
     */
    @Override
    public void updateStatus(int orderId, Order.Status newStatus, String actor, String note) {
        Order order = getOrder(orderId);
        if (newStatus == null) {
            throw new ValidationException("An order status is required.");
        }
        if (order.getStatus() == newStatus) {
            return;
        }
        if (!isAllowedTransition(order.getStatus(), newStatus)) {
            throw new ValidationException("Order status cannot change from "
                    + order.getStatus() + " to " + newStatus + ".");
        }
        String actorName = (actor == null || actor.isBlank()) ? "SYSTEM" : actor;

        boolean restock = newStatus == Order.Status.CANCELLED || newStatus == Order.Status.REFUNDED;
        String logAction = newStatus == Order.Status.REFUNDED ? "ORDER_REFUNDED" : "ORDER_CANCELLED";
        Map<Integer, Integer> restoredQuantities = new HashMap<>();
        Map<Integer, String> restoredStatuses = new HashMap<>();

        OrderStatusEvent event = new OrderStatusEvent();
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            conn.setAutoCommit(false);

            // Claim the transition first so concurrent cancel/refund cannot
            // both restock the same order.
            Order.Status fromStatus = order.getStatus();
            if (!orderDAO.updateStatusIfCurrent(conn, orderId, fromStatus, newStatus)) {
                throw new ValidationException(
                        "Order status was already changed by another request. Refresh and try again.");
            }

            if (restock) {
                for (OrderItem item : order.getItems()) {
                    // Atomic restore on the transaction connection: concurrent
                    // checkouts can never be overwritten, and a discontinued
                    // product stays discontinued.
                    ProductRepository.StockSnapshot restored = productDAO.restoreStock(conn,
                            item.getProductId(), item.getQuantity());
                    if (restored != null) {
                        restoredQuantities.put(item.getProductId(), restored.stockQuantity);
                        restoredStatuses.put(item.getProductId(), restored.status.name());
                        InventoryLog log = new InventoryLog();
                        log.setProductId(item.getProductId());
                        log.setOldQuantity(restored.stockQuantity - item.getQuantity());
                        log.setNewQuantity(restored.stockQuantity);
                        log.setAction(logAction);
                        log.setUserId(null);
                        inventoryDAO.addLog(conn, log);
                    }
                }
            }

            event.setOrderId(orderId);
            event.setFromStatus(fromStatus);
            event.setToStatus(newStatus);
            event.setChangedBy(actorName);
            event.setNote(note);
            orderDAO.insertStatusEvent(conn, event);

            conn.commit();
            // Any status change alters the dashboard's order lists/counters
            // (and cancelled/refunded changes revenue and storefront trending).
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateAllDashboard();
                CacheManager.invalidateAllCatalog();
            }
            if (restock) {
                // Evict after commit so the returned stock is visible at once.
                for (Integer productId : restoredQuantities.keySet()) {
                    ProductRepository.invalidateProductCache(productId);
                }
                AuditLogger.logDataModification(logAction, actorName, "ORDER",
                        String.valueOf(orderId),
                        newStatus == Order.Status.REFUNDED
                                ? "Order refunded, stock restored"
                                : "Order cancelled, stock restored");
            }
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Could not update order status.", e);
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(conn);
        }

        // Real-time notification after the transition is committed.
        notificationService.sendOrderStatusChanged(order, event);

        // Broadcast the new status and any returned stock to all live users.
        for (Map.Entry<Integer, Integer> restored : restoredQuantities.entrySet()) {
            int quantity = restored.getValue();
            EventHub.publishStock(restored.getKey(), quantity,
                    restoredStatuses.getOrDefault(restored.getKey(),
                            Product.computeStatus(quantity).name()));
        }
        EventHub.publishOrder(order.getOrderId(), order.getUserId(), newStatus.name());
    }

    @Override
    public void cancelByCustomer(int orderId, int userId) {
        Order order = getOrder(orderId);
        if (order.getUserId() != userId) {
            throw new NotFoundException("Order does not exist.");
        }
        if (order.getStatus() != Order.Status.PENDING) {
            throw new ValidationException("Only pending orders can be cancelled.");
        }
        updateStatus(orderId, Order.Status.CANCELLED, order.getCustomerUsername(),
                "Cancelled by customer before processing");
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                // The rollback is best-effort inside an already-failing path,
                // but a failed rollback can leak the pooled connection into a
                // dirty state, so it must at least be visible in the logs.
                LOGGER.warn("Rollback failed (connection may be left in a dirty state)", e);
            }
        }
    }

    /**
     * Terminal orders must never be reopened. In particular, reopening a
     * cancelled order would leave its restored inventory unreserved.
     */
    private boolean isAllowedTransition(Order.Status current, Order.Status next) {
        return switch (current) {
            case PENDING -> next == Order.Status.PROCESSING || next == Order.Status.CANCELLED;
            case PROCESSING -> next == Order.Status.SHIPPED || next == Order.Status.CANCELLED;
            case SHIPPED -> next == Order.Status.COMPLETED;
            case COMPLETED -> next == Order.Status.REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // nothing sensible to do
            }
        }
    }
}
