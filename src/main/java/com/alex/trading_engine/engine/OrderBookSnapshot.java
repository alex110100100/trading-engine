package com.alex.trading_engine.engine;

import java.util.List;

/** Top-of-book snapshot: price levels with total quantity. */
public record OrderBookSnapshot(List<PriceLevel> bids, List<PriceLevel> asks) {
    public record PriceLevel(double price, double totalQuantity) {}
}
