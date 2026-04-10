package com.alex.trading_engine.persistence;

import com.alex.trading_engine.model.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Latest known status for an order id. Survives process restarts; in-memory state is checked first at runtime.
 */
@Entity
@Table(name = "order_states")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStateEntity {

    @Id
    @Column(name = "order_id", nullable = false, length = 128)
    private String orderId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    public OrderStateEntity(String orderId, String symbol, OrderStatus status) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.status = status;
    }
}
