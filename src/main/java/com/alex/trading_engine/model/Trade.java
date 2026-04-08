package com.alex.trading_engine.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable record of a trade: two orders matched at a price and quantity.
 */
@Getter
public class Trade {
    private final String buyerOrderId;
    private final String sellerOrderId;
    private final String symbol;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Instant timestamp;

    public Trade(String buyerOrderId, String sellerOrderId, String symbol, BigDecimal price, BigDecimal quantity, Instant timestamp) {
        this.buyerOrderId = buyerOrderId;
        this.sellerOrderId = sellerOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
}
