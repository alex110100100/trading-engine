package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import com.alex.trading_engine.model.OrderStatus;
import com.alex.trading_engine.model.Trade;
import com.alex.trading_engine.persistence.TradeEntity;
import com.alex.trading_engine.persistence.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.*;

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

    @Test
    void testGetOrderStatusAcceptedForRestingOrder() {
        matchingEngine.processOrder(new Order.Builder()
                .id("resting")
                .symbol("BTC/USD")
                .price(30000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build());

        assertEquals(OrderStatus.ACCEPTED, matchingEngine.getOrderStatus("resting").orElseThrow());
    }

    @Test
    void testGetOrderStatusCancelledAfterCancel() {
        matchingEngine.processOrder(new Order.Builder()
                .id("cancel-me")
                .symbol("BTC/USD")
                .price(30000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build());

        assertTrue(matchingEngine.cancelOrder("cancel-me"));
        assertEquals(OrderStatus.CANCELLED, matchingEngine.getOrderStatus("cancel-me").orElseThrow());
    }

    @Test
    void testConcurrentSubmitsRemainConsistent() throws Exception {
        int threadCount = 8;
        int orderCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        // Start all workers together to maximize overlap and expose race conditions.
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < orderCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    matchingEngine.processOrder(new Order.Builder()
                            .id("buy-" + idx)
                            .symbol("BTC/USD")
                            .price(30000)
                            .quantity(1)
                            .orderSide(OrderSide.BUY)
                            .build());
                    return null;
                }));
            }

            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        LinkedHashMap<String, BookEntry> level = matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000));
        assertNotNull(level);
        assertEquals(orderCount, level.size());
        for (int i = 0; i < orderCount; i++) {
            assertEquals(OrderStatus.ACCEPTED, matchingEngine.getOrderStatus("buy-" + i).orElseThrow());
        }
    }

    @Test
    void testConcurrentSubmitAndCancelEndsInValidState() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // Both tasks wait on this gate so submit/cancel race at nearly the same instant.
        CountDownLatch startGate = new CountDownLatch(1);

        Future<?> submitFuture = executor.submit(() -> {
            startGate.await();
            matchingEngine.processOrder(new Order.Builder()
                    .id("race-order")
                    .symbol("BTC/USD")
                    .price(30000)
                    .quantity(1)
                    .orderSide(OrderSide.BUY)
                    .build());
            return null;
        });

        Future<Boolean> cancelFuture = executor.submit(() -> {
            startGate.await();
            return matchingEngine.cancelOrder("race-order");
        });

        try {
            startGate.countDown();
            submitFuture.get(5, TimeUnit.SECONDS);
            boolean cancelResult = cancelFuture.get(5, TimeUnit.SECONDS);

            OrderStatus status = matchingEngine.getOrderStatus("race-order").orElseThrow();
            // Both outcomes are valid in a race:
            // 1) cancel wins -> status CANCELLED and not in book
            // 2) submit wins -> status ACCEPTED and present in book
            // This test checks that whichever path occurs, final state is consistent.
            if (cancelResult) {
                assertEquals(OrderStatus.CANCELLED, status);
                LinkedHashMap<String, BookEntry> level = matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000));
                if (level != null) {
                    assertFalse(level.containsKey("race-order"));
                }
            } else {
                assertEquals(OrderStatus.ACCEPTED, status);
                LinkedHashMap<String, BookEntry> level = matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000));
                assertNotNull(level);
                assertTrue(level.containsKey("race-order"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testConcurrentSubmitsAcrossSymbolsRemainIsolated() throws Exception {
        int perSymbolOrders = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        // Start together so both symbols receive concurrent traffic.
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < perSymbolOrders; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    matchingEngine.processOrder(new Order.Builder()
                            .id("btc-" + idx)
                            .symbol("BTC/USD")
                            .price(30000)
                            .quantity(1)
                            .orderSide(OrderSide.BUY)
                            .build());
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    startGate.await();
                    matchingEngine.processOrder(new Order.Builder()
                            .id("eth-" + idx)
                            .symbol("ETH/USD")
                            .price(2000)
                            .quantity(1)
                            .orderSide(OrderSide.BUY)
                            .build());
                    return null;
                }));
            }

            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        LinkedHashMap<String, BookEntry> btcLevel = matchingEngine.getBids("BTC/USD").get(BigDecimal.valueOf(30000));
        LinkedHashMap<String, BookEntry> ethLevel = matchingEngine.getBids("ETH/USD").get(BigDecimal.valueOf(2000));
        assertNotNull(btcLevel);
        assertNotNull(ethLevel);
        assertEquals(perSymbolOrders, btcLevel.size());
        assertEquals(perSymbolOrders, ethLevel.size());
        assertTrue(matchingEngine.getTrades().isEmpty());

        // Verify no accidental cross-symbol ids leaked into the wrong book.
        assertFalse(btcLevel.keySet().stream().anyMatch(id -> id.startsWith("eth-")));
        assertFalse(ethLevel.keySet().stream().anyMatch(id -> id.startsWith("btc-")));
    }

    @Test
    @SuppressWarnings("null")
    void testMatchedTradesArePersistedWhenRepositoryAvailable() {
        TradeRepository tradeRepository = mock(TradeRepository.class);
        matchingEngine.setTradeRepository(tradeRepository);

        Order ask = new Order.Builder()
                .id("ask-persist")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.SELL)
                .build();
        matchingEngine.processOrder(ask);
        verifyNoInteractions(tradeRepository);

        Order buy = new Order.Builder()
                .id("buy-persist")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();
        matchingEngine.processOrder(buy);

        verify(tradeRepository, times(1)).saveAll(notNull());
    }

    @Test
    void testGetTradesUsesRepositoryWhenPresent() {
        TradeRepository tradeRepository = mock(TradeRepository.class);
        Instant ts = Instant.parse("2026-01-01T12:00:00Z");
        TradeEntity entity = new TradeEntity("b1", "s1", "BTC/USD", BigDecimal.valueOf(100), BigDecimal.ONE, ts);
        when(tradeRepository.findAllByOrderByTimestampAscBuyerOrderIdAscSellerOrderIdAsc())
                .thenReturn(List.of(entity));
        matchingEngine.setTradeRepository(tradeRepository);

        List<Trade> trades = matchingEngine.getTrades();

        assertEquals(1, trades.size());
        assertEquals("b1", trades.get(0).getBuyerOrderId());
        assertEquals("s1", trades.get(0).getSellerOrderId());
        verify(tradeRepository, times(1)).findAllByOrderByTimestampAscBuyerOrderIdAscSellerOrderIdAsc();
    }
}