package com.example.computer_store.core.domain.entity;

import java.sql.Timestamp;

/**
 * A single step in an order's lifecycle timeline. Every status change
 * (including the initial "Order placed" event) is recorded so both
 * customers and admins can see when and by whom the order moved.
 */
public class OrderStatusEvent {

    private int eventId;
    private int orderId;
    private Order.Status fromStatus;
    private Order.Status toStatus;
    private String changedBy;
    private String note;
    private Timestamp createdAt;

    public OrderStatusEvent() {
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public Order.Status getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(Order.Status fromStatus) {
        this.fromStatus = fromStatus;
    }

    public Order.Status getToStatus() {
        return toStatus;
    }

    public void setToStatus(Order.Status toStatus) {
        this.toStatus = toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}