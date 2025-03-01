package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
    }

    @Test
    void testBuyOrderMatchesBestAsk() {
        // Add an ask to the order book using the Builder pattern
        Order ask = new Order.Builder()
                .id("ask1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(ask);

        // Submit a buy order using the Builder pattern
        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        // Verify the ask was matched and removed
        assertFalse(matchingEngine.getAsks().containsKey(31000.0));
    }

    @Test
    void testSellOrderMatchesBestBid() {
        // Add a bid to the order book using the Builder pattern
        Order bid = new Order.Builder()
                .id("bid1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(bid);

        // Submit a sell order using the Builder pattern
        Order sell = new Order.Builder()
                .id("sell1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(sell);

        // Verify the bid was matched and removed
        assertFalse(matchingEngine.getBids().containsKey(31000.0));
    }

    @Test
    void testPriceTimePriority() {
        // Add two bids at the same price using the Builder pattern
        Order bid1 = new Order.Builder()
                .id("bid1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(bid1);

        Order bid2 = new Order.Builder()
                .id("bid2")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(bid2);

        // Submit a sell order using the Builder pattern
        Order sell = new Order.Builder()
                .id("sell1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(sell);

        // Verify the first bid was matched
        assertFalse(matchingEngine.getBids().get(31000.0).containsKey("bid1"));
        assertTrue(matchingEngine.getBids().get(31000.0).containsKey("bid2"));
    }

    @Test
    void testUnmatchedBuyOrderAddedToBids() {
        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(30000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        // Verify the buy order was added to the bids book
        assertTrue(matchingEngine.getBids().containsKey(30000.0));
        assertEquals(1, matchingEngine.getBids().get(30000.0).size());
        assertTrue(matchingEngine.getBids().get(30000.0).containsKey("buy1"));
    }

    @Test
    void testUnmatchedSellOrderAddedToAsks() {
        Order sell = new Order.Builder()
                .id("sell1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(sell);

        // Verify the sell order was added to the asks book
        assertTrue(matchingEngine.getAsks().containsKey(31000.0));
        assertEquals(1, matchingEngine.getAsks().get(31000.0).size());
        assertTrue(matchingEngine.getAsks().get(31000.0).containsKey("sell1"));
    }

    @Test
    void testOrderBookStateAfterMatching() {
        // Add a sell order using the Builder pattern
        Order ask = new Order.Builder()
                .id("ask1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(ask);

        // Add a buy order that matches the ask using the Builder pattern
        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        // Verify the ask was matched and removed
        assertFalse(matchingEngine.getAsks().containsKey(31000.0));

        // Verify the buy order was not added to the bids book (since it was fully matched)
        assertFalse(matchingEngine.getBids().containsKey(31000.0));
    }
}