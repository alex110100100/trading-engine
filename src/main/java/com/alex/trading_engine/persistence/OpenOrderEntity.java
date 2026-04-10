package com.alex.trading_engine.persistence;

import com.alex.trading_engine.model.OrderSide;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One resting (open) order in the book. Synced from in-memory state after each mutation for a symbol.
 */
@Entity
@Table(name = "open_orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenOrderEntity {

    @Id
    @Column(name = "order_id", nullable = false, length = 128)
    private String orderId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide orderSide;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal remainingQuantity;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal originalQuantity;

    @Column(nullable = false)
    private Instant createdAt;

    public OpenOrderEntity(
            String orderId,
            String symbol,
            OrderSide orderSide,
            BigDecimal price,
            BigDecimal remainingQuantity,
            BigDecimal originalQuantity,
            Instant createdAt) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.orderSide = orderSide;
        this.price = price;
        this.remainingQuantity = remainingQuantity;
        this.originalQuantity = originalQuantity;
        this.createdAt = createdAt;
    }
}
