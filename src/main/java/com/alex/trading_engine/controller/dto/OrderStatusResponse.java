package com.alex.trading_engine.controller.dto;

import com.alex.trading_engine.model.OrderStatus;

/** Response body for GET /order/{id}. */
public record OrderStatusResponse(String orderId, OrderStatus status) {}
