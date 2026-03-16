package com.alex.trading_engine.controller.dto;

import com.alex.trading_engine.model.OrderStatus;

/** Response body for POST /order. */
public record SubmitOrderResponse(String orderId, OrderStatus status) {}
