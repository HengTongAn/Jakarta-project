package com.example.computer_store.service.impl;

import com.example.computer_store.service.OrderService;

import com.example.computer_store.dao.CartDAO;
import com.example.computer_store.dao.InventoryDAO;
import com.example.computer_store.dao.OrderDAO;
import com.example.computer_store.dao.ProductDAO;
import com.example.computer_store.exception.InsufficientStockException;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.CartItem;
import com.example.computer_store.model.InventoryLog;
import com.example.computer_store.model.Order;
import com.example.computer_store.model.OrderItem;
import com.example.computer_store.model.Product;
import com.example.computer_store.model.User;
import com.example.computer_store.util.AuditLogger;
import com.example.computer_store.util.ConnectionProvider;
import com.example.computer_store.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class OrderServiceImpl implements OrderService {

    private final CartDAO cartDAO;
    private final ProductDAO productDAO;
    private final OrderDAO orderDAO;
    private final InventoryDAO inventoryDAO;
    private final ConnectionProvider connectionProvider;

    public OrderServiceImpl() {
        this(new CartDAO(), new ProductDAO(), new OrderDAO(), new InventoryDAO());
    }

    public OrderServiceImpl(CartDAO cartDAO, ProductDAO productDAO,
                            OrderDAO orderDAO, InventoryDAO inventoryDAO) {
        this(cartDAO, productDAO, orderDAO, inventoryDAO, DBConnection::getConnection);
    }

    public OrderServiceImpl(CartDAO cartDAO, ProductDAO productDAO,
                            OrderDAO orderDAO, InventoryDAO inventoryDAO,
                            ConnectionProvider connectionProvider) {
        this.cartDAO = cartDAO;
        this.productDAO = productDAO;
        this.orderDAO = orderDAO;
        this.inventoryDAO = inventoryDAO;
        this.connectionProvider = connectionProvider;
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
                total = total.add(product.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }

            Order order = new Order();
            order.setUserId(userId);
            order.setTotalAmount(total);
            order.setStatus(Order.Status.PENDING);
            int orderId = orderDAO.createOrder(conn, order);

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
                log.setNewQuantity(product.getStockQuantity() - item.getQuantity());
                log.setAction("ORDER_CREATED");
                log.setUserId(null);
                inventoryDAO.addLog(conn, log);
            }

            // Keep cart removal in this transaction. Clearing it after the
            // commit on a second connection could leave a completed order
            // behind while reporting checkout as failed.
            cartDAO.clear(conn, userId);
            conn.commit();
            order.setOrderId(orderId);
            AuditLogger.logDataModification("ORDER_CREATED", user.getUsername(), "ORDER",
                    String.valueOf(orderId), "Checkout total " + total);
            return order;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Order processing failed. Please try again.", e);
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(conn);
        }
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
    public Order getOrder(int orderId) {
        Order order = orderDAO.findById(orderId);
        if (order == null) {
            throw new NotFoundException("Order does not exist.");
        }
        return order;
    }

    /**
     * Updates the order status. Cancelling an order returns the purchased
     * quantity back to stock and writes inventory log entries.
     */
    @Override
    public void updateStatus(int orderId, Order.Status newStatus) {
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

        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            conn.setAutoCommit(false);

            if (newStatus == Order.Status.CANCELLED) {
                for (OrderItem item : order.getItems()) {
                    Product product = productDAO.findById(item.getProductId());
                    if (product != null) {
                        int restored = product.getStockQuantity() + item.getQuantity();
                        productDAO.setStock(conn, product.getProductId(), restored,
                                Product.computeStatus(restored));

                        InventoryLog log = new InventoryLog();
                        log.setProductId(item.getProductId());
                        log.setOldQuantity(product.getStockQuantity());
                        log.setNewQuantity(restored);
                        log.setAction("ORDER_CANCELLED");
                        log.setUserId(null);
                        inventoryDAO.addLog(conn, log);
                    }
                }
            }

            orderDAO.updateStatus(conn, orderId, newStatus);
            conn.commit();
            if (newStatus == Order.Status.CANCELLED) {
                AuditLogger.logDataModification("ORDER_CANCELLED", "SYSTEM", "ORDER",
                        String.valueOf(orderId), "Order cancelled, stock restored");
            }
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Could not update order status.", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // nothing sensible to do
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
            case PROCESSING -> next == Order.Status.COMPLETED || next == Order.Status.CANCELLED;
            case COMPLETED, CANCELLED -> false;
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
