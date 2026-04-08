package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.Trade;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Routes orders to a per-symbol {@link OrderBook}. Symbols are isolated: no cross-instrument matching.
 */
@Service
@Getter
public class MatchingEngine {

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    private OrderBook bookFor(String symbol) {
        return books.computeIfAbsent(symbol, s -> new OrderBook());
    }

    public ProcessOrderResult processOrder(Order order) {
        return bookFor(order.getSymbol()).processOrder(order);
    }

    public boolean cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        for (OrderBook book : books.values()) {
            if (book.cancelOrder(orderId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * All trades across symbols, merged and sorted for a deterministic list.
     * <p>
     * Primary sort: {@link Trade#getTimestamp()}. If two trades share the same instant (possible when
     * several matches occur in the same JVM clock tick), timestamps compare equal, so we break ties
     * with buyer order id, then seller order id. That way the order is stable across runs and tests,
     * not dependent on hash-map iteration or stream merge order.
     */
    public List<Trade> getTrades() {
        return books.values().stream()
                .flatMap(b -> b.getTrades().stream())
                .sorted(Comparator.comparing(Trade::getTimestamp).thenComparing(Trade::getBuyerOrderId).thenComparing(Trade::getSellerOrderId))
                .collect(Collectors.toList());
    }

    /**
     * Snapshot for one symbol. Unknown or never-used symbol returns empty bids/asks.
     */
    public OrderBookSnapshot getOrderBookSnapshot(String symbol, int limit) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            return new OrderBookSnapshot(List.of(), List.of());
        }
        return book.getSnapshot(limit);
    }

    /** For tests / inspection: bids for a symbol (empty book if none). */
    public TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> getBids(String symbol) {
        OrderBook b = books.get(symbol);
        if (b == null) {
            return new TreeMap<>(Comparator.reverseOrder());
        }
        return b.getBids();
    }

    /** For tests / inspection: asks for a symbol (empty book if none). */
    public TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> getAsks(String symbol) {
        OrderBook b = books.get(symbol);
        if (b == null) {
            return new TreeMap<>();
        }
        return b.getAsks();
    }
}
