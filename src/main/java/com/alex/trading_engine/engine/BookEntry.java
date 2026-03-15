package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import lombok.Getter;

/**
 * Mutable wrapper for an order in the order book, tracking remaining quantity
 * so we can support partial fills without mutating the immutable Order.
 */
@Getter
public class BookEntry {
    private final Order order;
    private double remainingQuantity;

    public BookEntry(Order order, double remainingQuantity) {
        this.order = order;
        this.remainingQuantity = remainingQuantity;
    }

    public void reduceBy(double quantity) {
        if (quantity > remainingQuantity) {
            throw new IllegalArgumentException("Cannot reduce by more than remaining quantity");
        }
        this.remainingQuantity -= quantity;
    }

    public boolean isFilled() {
        return remainingQuantity <= 0;
    }
}
