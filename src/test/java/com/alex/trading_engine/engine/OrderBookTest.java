package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    @Test
    void placeRestingWithoutMatchRejectsDuplicateOrderId() {
        OrderBook book = new OrderBook();
        Order order = new Order.Builder()
                .id("dup")
                .symbol("BTC/USD")
                .price(30000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        book.placeRestingWithoutMatch(order);
        assertThrows(IllegalStateException.class, () -> book.placeRestingWithoutMatch(order));
    }

    @Test
    void collectRestingOrdersIncludesBidAndAsk() {
        OrderBook book = new OrderBook();
        book.placeRestingWithoutMatch(new Order.Builder()
                .id("b1")
                .symbol("BTC/USD")
                .price(30000)
                .quantity(2)
                .orderSide(OrderSide.BUY)
                .build());
        book.placeRestingWithoutMatch(new Order.Builder()
                .id("a1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build());

        var resting = book.collectRestingOrders();
        assertEquals(2, resting.size());
        assertEquals(OrderSide.BUY, resting.get("b1").side());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(resting.get("b1").remainingQuantity()));
        assertEquals(OrderSide.SELL, resting.get("a1").side());
    }
}
