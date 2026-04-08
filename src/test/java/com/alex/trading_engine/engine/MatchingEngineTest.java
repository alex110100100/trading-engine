package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import com.alex.trading_engine.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
        assertFalse(matchingEngine.getAsks("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
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
        assertFalse(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
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
        assertFalse(matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(31000.0)).containsKey("bid1"));
        assertTrue(matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(31000.0)).containsKey("bid2"));
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
        assertTrue(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(30000.0)));
        assertEquals(1, matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000.0)).size());
        assertTrue(matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000.0)).containsKey("buy1"));
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
        assertTrue(matchingEngine.getAsks("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
        assertEquals(1, matchingEngine.getAsks("BTC/USD").get(BigDecimal.valueOf(31000.0)).size());
        assertTrue(matchingEngine.getAsks("BTC/USD").get(BigDecimal.valueOf(31000.0)).containsKey("sell1"));
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
        assertFalse(matchingEngine.getAsks("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));

        // Verify the buy order was not added to the bids book (since it was fully matched)
        assertFalse(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
    }

    @Test
    void testPartialFillIncomingOrderRemainderResting() {
        Order ask = new Order.Builder()
                .id("ask1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(2)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(ask);

        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(5)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        // Ask fully filled and removed; buy partially filled: 3 should remain in bids
        assertFalse(matchingEngine.getAsks("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
        assertTrue(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
        assertEquals(1, matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(31000.0)).size());
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(31000.0)).get("buy1").getRemainingQuantity()));
    }

    @Test
    void testPartialFillRestingOrderRemainderStaysInBook() {
        Order ask = new Order.Builder()
                .id("ask1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(5)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(ask);

        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(2)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        // Buy fully filled; ask partially filled: 3 should remain at 31000 in asks
        assertFalse(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
        assertTrue(matchingEngine.getAsks("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
        assertEquals(1, matchingEngine.getAsks("BTC/USD").get(BigDecimal.valueOf(31000.0)).size());
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(matchingEngine.getAsks("BTC/USD").get(BigDecimal.valueOf(31000.0)).get("ask1").getRemainingQuantity()));
    }

    @Test
    void testTradeRecordedOnMatch() {
        Order ask = new Order.Builder()
                .id("ask1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(ask);

        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        assertEquals(1, matchingEngine.getTrades().size());
        Trade trade = matchingEngine.getTrades().get(0);
        assertEquals("buy1", trade.getBuyerOrderId());
        assertEquals("ask1", trade.getSellerOrderId());
        assertEquals("BTC/USD", trade.getSymbol());
        assertEquals(0, BigDecimal.valueOf(31000.0).compareTo(trade.getPrice()));
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(trade.getQuantity()));
    }

    @Test
    void testMultiplePriceLevelsIncomingEatsThroughBook() {
        matchingEngine.processOrder(new Order.Builder().id("ask1").symbol("BTC/USD").price(31000).quantity(1).orderSide(OrderSide.SELL).build());
        matchingEngine.processOrder(new Order.Builder().id("ask2").symbol("BTC/USD").price(31100).quantity(1).orderSide(OrderSide.SELL).build());

        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(31200)
                .quantity(2)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        // Both asks filled; two trades
        assertTrue(matchingEngine.getAsks("BTC/USD").isEmpty());
        assertEquals(2, matchingEngine.getTrades().size());
        assertEquals(0, BigDecimal.valueOf(31000.0).compareTo(matchingEngine.getTrades().get(0).getPrice()));
        assertEquals(0, BigDecimal.valueOf(31100.0).compareTo(matchingEngine.getTrades().get(1).getPrice()));
    }

    @Test
    void testCancelOrderRemovesFromBids() {
        Order buy = new Order.Builder()
                .id("buy1")
                .symbol("BTC/USD")
                .price(30000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);
        assertTrue(matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000.0)).containsKey("buy1"));

        assertTrue(matchingEngine.cancelOrder("buy1"));
        assertFalse(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(30000.0)));
    }

    @Test
    void testCancelOrderRemovesFromAsks() {
        Order sell = new Order.Builder()
                .id("sell1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(sell);
        assertTrue(matchingEngine.getAsks("BTC/USD").get(BigDecimal.valueOf(31000.0)).containsKey("sell1"));

        assertTrue(matchingEngine.cancelOrder("sell1"));
        assertFalse(matchingEngine.getAsks("BTC/USD").containsKey(BigDecimal.valueOf(31000.0)));
    }

    @Test
    void testCancelOrderUnknownIdReturnsFalse() {
        assertFalse(matchingEngine.cancelOrder("unknown"));
    }

    @Test
    void testCancelOrderAlreadyMatchedReturnsFalse() {
        Order ask = new Order.Builder().id("ask1").symbol("BTC/USD").price(31000).quantity(1).orderSide(OrderSide.SELL).build();
        Order buy = new Order.Builder().id("buy1").symbol("BTC/USD").price(31000).quantity(1).orderSide(OrderSide.BUY).build();
        matchingEngine.processOrder(ask);
        matchingEngine.processOrder(buy);
        // Both matched; neither is in the book
        assertFalse(matchingEngine.cancelOrder("ask1"));
        assertFalse(matchingEngine.cancelOrder("buy1"));
    }

    @Test
    void testSymbolsDoNotCrossMatch() {
        matchingEngine.processOrder(new Order.Builder()
                .id("eth-ask")
                .symbol("ETH/USD")
                .price(2000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build());
        matchingEngine.processOrder(new Order.Builder()
                .id("btc-buy")
                .symbol("BTC/USD")
                .price(2000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build());

        assertTrue(matchingEngine.getAsks("ETH/USD").containsKey(BigDecimal.valueOf(2000)));
        assertTrue(matchingEngine.getBids("BTC/USD").containsKey(BigDecimal.valueOf(2000)));
        assertTrue(matchingEngine.getTrades().isEmpty());
    }

    @Test
    void testGetOrderBookSnapshotUnknownSymbolReturnsEmpty() {
        OrderBookSnapshot snap = matchingEngine.getOrderBookSnapshot("UNKNOWN/PAIR", 10);
        assertTrue(snap.bids().isEmpty());
        assertTrue(snap.asks().isEmpty());
    }
}