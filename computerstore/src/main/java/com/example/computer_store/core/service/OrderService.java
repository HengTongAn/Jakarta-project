package com.example.computer_store.core.service;

import com.example.computer_store.core.domain.entity.Order;
import com.example.computer_store.core.domain.entity.User;

import java.util.List;

public interface OrderService {

    Order checkout(User user);

    List<Order> getOrdersForUser(int userId);

    Order getOrderForUser(int orderId, int userId);

    List<Order> getAllOrders();

    List<Order> getOrdersByStatus(Order.Status status);

    /** Paginated admin list; {@code status} may be null for all statuses. */
    List<Order> getOrdersPage(Order.Status status, int offset, int limit);

    int countOrders(Order.Status status);

    Order getOrder(int orderId);

    void updateStatus(int orderId, Order.Status newStatus);

    /**
     * Changes the status of an order and records who did it (actor) and why
     * (note) on the order timeline.
     */
    void updateStatus(int orderId, Order.Status newStatus, String actor, String note);

    /**
     * Lets the customer cancel their own order while it is still pending.
     * The cancelled items are returned to stock.
     */
    void cancelByCustomer(int orderId, int userId);
}
