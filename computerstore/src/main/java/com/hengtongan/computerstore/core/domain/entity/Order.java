package com.hengtongan.computerstore.core.domain.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Order {

    public enum Status {
        PENDING, PROCESSING, SHIPPED, COMPLETED, CANCELLED, REFUNDED
    }

    private int orderId;
    private int userId;
    private Timestamp orderDate;
    private BigDecimal totalAmount;
    private Status status;

    // Payment. Method and status are VARCHAR columns rather than an enum so an
    // unmapped provider shows up as data to fix instead of a constraint failure;
    // the allowed values are enforced in PaymentService.
    private String paymentMethod;
    private String paymentProvider;
    private String paymentStatus;
    private String paymentTransaction;
    private Timestamp paidAt;

    // Denormalised fields for display
    private String customerName;
    private String customerUsername;
    private int itemCount;
    private List<OrderItem> items = new ArrayList<>();
    private List<OrderStatusEvent> statusEvents = new ArrayList<>();

    public Order() {
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentTransaction() {
        return paymentTransaction;
    }

    public void setPaymentTransaction(String paymentTransaction) {
        this.paymentTransaction = paymentTransaction;
    }

    public Timestamp getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Timestamp paidAt) {
        this.paidAt = paidAt;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Timestamp getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Timestamp orderDate) {
        this.orderDate = orderDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public void setCustomerUsername(String customerUsername) {
        this.customerUsername = customerUsername;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public List<OrderStatusEvent> getStatusEvents() {
        return statusEvents;
    }

    public void setStatusEvents(List<OrderStatusEvent> statusEvents) {
        this.statusEvents = statusEvents;
    }
}