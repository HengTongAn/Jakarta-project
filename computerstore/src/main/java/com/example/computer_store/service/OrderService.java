package com.example.computer_store.service;

import com.example.computer_store.model.Order;
import com.example.computer_store.model.User;

import java.util.List;

public interface OrderService {

    Order checkout(User user);

    List<Order> getOrdersForUser(int userId);

    Order getOrderForUser(int orderId, int userId);

    List<Order> getAllOrders();

    Order getOrder(int orderId);

    void updateStatus(int orderId, Order.Status newStatus);
}
