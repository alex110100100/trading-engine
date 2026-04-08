package com.alex.trading_engine.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a trading order in the system.
 * Immutable to ensure thread safety.
 */
@JsonDeserialize(builder = Order.Builder.class)
@Getter
public class Order {
    private final String id;
    @NotBlank(message = "symbol is required")
    private final String symbol; // e.g., "BTC/USD"
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive")
    private final double price;
    @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be positive")
    private final double quantity;
    @NotNull(message = "orderSide is required")
    private final OrderSide orderSide;
    private final Instant timestamp;

    private Order(Builder builder) {
        this.id = builder.id;
        this.symbol = builder.symbol;
        this.price = builder.price;
        this.quantity = builder.quantity;
        this.orderSide = builder.orderSide;
        this.timestamp = builder.timestamp;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", orderSide=" + orderSide +
                '}';
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String symbol;
        private double price;
        private double quantity;
        private OrderSide orderSide;
        private Instant timestamp = Instant.now();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder price(double price) {
            this.price = price;
            return this;
        }

        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder orderSide(OrderSide orderSide) {
            this.orderSide = orderSide;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Order build() {
            if (symbol == null || symbol.trim().isEmpty()) {
                throw new IllegalArgumentException("Symbol is required");
            }

            if (orderSide == null) {
                throw new IllegalArgumentException("OrderSide is required");
            }

            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            return new Order(this);
        }
    }

}

