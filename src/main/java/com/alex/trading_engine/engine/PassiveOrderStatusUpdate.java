package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.OrderStatus;

/**
 * Status of an order already in the book after it was hit during this match (the "passive" side of the trade).
 * The incoming submit gets {@link ProcessOrderResult}; this type carries the same kind of update for resting ids.
 */
public record PassiveOrderStatusUpdate(String orderId, String symbol, OrderStatus status) {
}
