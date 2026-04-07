package com.alex.trading_engine.controller.dto;

import com.alex.trading_engine.model.Trade;

import java.time.Instant;

/** Response body for GET /trades. */
public record TradeResponse(
        String buyerOrderId,
        String sellerOrderId,
        String symbol,
        double price,
        double quantity,
        Instant timestamp
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getBuyerOrderId(),
                trade.getSellerOrderId(),
                trade.getSymbol(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getTimestamp()
        );
    }
}
