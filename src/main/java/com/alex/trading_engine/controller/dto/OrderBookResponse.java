package com.alex.trading_engine.controller.dto;

import java.util.List;

/** Response body for GET /orderbook: top bid and ask levels with price and total quantity. */
public record OrderBookResponse(List<PriceLevel> bids, List<PriceLevel> asks) {

    public record PriceLevel(double price, double totalQuantity) {}
}
