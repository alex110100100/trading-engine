package com.alex.trading_engine.model;

/**
 * Status of an order after submission.
 * - ACCEPTED: fully resting in the book (no fill).
 * - PARTIALLY_FILLED: some quantity matched, remainder resting.
 * - FILLED: fully matched (nothing resting).
 */
public enum OrderStatus {
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
}
