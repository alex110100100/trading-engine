package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.OrderStatus;

/** Result of processing an order: order id and resulting status. */
public record ProcessOrderResult(String orderId, OrderStatus status) {}
