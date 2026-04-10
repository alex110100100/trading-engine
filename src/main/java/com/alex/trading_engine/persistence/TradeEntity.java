package com.alex.trading_engine.persistence;

import com.alex.trading_engine.model.Trade;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Getter
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String buyerOrderId;

    @Column(nullable = false)
    private String sellerOrderId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant timestamp;

    protected TradeEntity() {
    }

    public TradeEntity(String buyerOrderId, String sellerOrderId, String symbol, BigDecimal price, BigDecimal quantity, Instant timestamp) {
        this.buyerOrderId = buyerOrderId;
        this.sellerOrderId = sellerOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public static TradeEntity fromDomain(Trade trade) {
        return new TradeEntity(
                trade.getBuyerOrderId(),
                trade.getSellerOrderId(),
                trade.getSymbol(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getTimestamp());
    }

    public Trade toDomain() {
        return new Trade(buyerOrderId, sellerOrderId, symbol, price, quantity, timestamp);
    }
}
