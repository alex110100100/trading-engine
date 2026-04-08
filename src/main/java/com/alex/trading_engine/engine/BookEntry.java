package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Mutable wrapper for an order in the order book, tracking remaining quantity
 * so we can support partial fills without mutating the immutable Order.
 */
@Getter
public class BookEntry {
    private final Order order;
    private BigDecimal remainingQuantity;

    public BookEntry(Order order, BigDecimal remainingQuantity) {
        this.order = order;
        this.remainingQuantity = remainingQuantity;
    }

    public void reduceBy(BigDecimal quantity) {
        if (quantity.compareTo(remainingQuantity) > 0) {
            throw new IllegalArgumentException("Cannot reduce by more than remaining quantity");
        }
        this.remainingQuantity = this.remainingQuantity.subtract(quantity);
    }

    public boolean isFilled() {
        return remainingQuantity.compareTo(BigDecimal.ZERO) <= 0;
    }
}
