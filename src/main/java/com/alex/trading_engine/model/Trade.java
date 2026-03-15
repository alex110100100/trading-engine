package com.alex.trading_engine.model;

import lombok.Getter;

import java.time.Instant;

/**
 * Immutable record of a trade: two orders matched at a price and quantity.
 */
@Getter
public class Trade {
    private final String buyerOrderId;
    private final String sellerOrderId;
    private final String symbol;
    private final double price;
    private final double quantity;
    private final Instant timestamp;

    public Trade(String buyerOrderId, String sellerOrderId, String symbol, double price, double quantity, Instant timestamp) {
        this.buyerOrderId = buyerOrderId;
        this.sellerOrderId = sellerOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
}
