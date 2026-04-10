package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderStatus;
import com.alex.trading_engine.model.Trade;
import com.alex.trading_engine.persistence.TradeEntity;
import com.alex.trading_engine.persistence.TradeRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Routes orders to a per-symbol {@link OrderBook}. Symbols are isolated: no cross-instrument matching.
 * <p>
 * Threading model:
 * <ul>
 *   <li>Each symbol has its own lock, so different symbols can process concurrently.</li>
 *   <li>Operations for the same symbol are serialized to preserve deterministic matching.</li>
 *   <li>Order status and order-to-symbol index updates happen inside the same symbol lock.</li>
 * </ul>
 */
@Service
@Getter
public class MatchingEngine {

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<String, OrderStatus> orderStatuses = new ConcurrentHashMap<>();
    private final Map<String, Object> symbolLocks = new ConcurrentHashMap<>();
    private final Map<String, String> orderToSymbol = new ConcurrentHashMap<>();
    private TradeRepository tradeRepository;

    private OrderBook bookFor(String symbol) {
        return books.computeIfAbsent(symbol, s -> new OrderBook());
    }

    private Object lockForSymbol(String symbol) {
        return symbolLocks.computeIfAbsent(symbol, s -> new Object());
    }

    @Autowired(required = false)
    void setTradeRepository(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public ProcessOrderResult processOrder(Order order) {
        String symbol = order.getSymbol();
        synchronized (lockForSymbol(symbol)) {
            OrderBook book = bookFor(symbol);
            int tradeCountBefore = book.getTrades().size();
            ProcessOrderResult result = book.processOrder(order);
            persistNewTrades(book, tradeCountBefore);
            orderStatuses.put(result.orderId(), result.status());
            orderToSymbol.put(result.orderId(), symbol);
            return result;
        }
    }

    private void persistNewTrades(OrderBook book, int tradeCountBefore) {
        if (tradeRepository == null) {
            return;
        }
        List<Trade> trades = book.getTrades();
        if (trades.size() <= tradeCountBefore) {
            return;
        }
        List<TradeEntity> newTrades = trades.subList(tradeCountBefore, trades.size()).stream()
                .map(TradeEntity::fromDomain)
                .toList();
        saveTrades(newTrades);
    }

    @SuppressWarnings("null")
    private void saveTrades(List<TradeEntity> newTrades) {
        tradeRepository.saveAll(newTrades);
    }

    public boolean cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        String symbol = orderToSymbol.get(orderId);
        if (symbol == null) {
            return false;
        }
        synchronized (lockForSymbol(symbol)) {
            OrderBook book = books.get(symbol);
            if (book == null) {
                return false;
            }
            if (book.cancelOrder(orderId)) {
                orderStatuses.put(orderId, OrderStatus.CANCELLED);
                return true;
            }
            return false;
        }
    }

    public Optional<OrderStatus> getOrderStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderStatuses.get(orderId));
    }

    /**
     * All trades across symbols, merged and sorted for a deterministic list.
     * <p>
     * Primary sort: {@link Trade#getTimestamp()}. If two trades share the same instant (possible when
     * several matches occur in the same JVM clock tick), timestamps compare equal, so we break ties
     * with buyer order id, then seller order id.
     */
    public List<Trade> getTrades() {
        List<Trade> allTrades = new ArrayList<>();
        for (Map.Entry<String, OrderBook> entry : books.entrySet()) {
            String symbol = entry.getKey();
            OrderBook book = entry.getValue();
            synchronized (lockForSymbol(symbol)) {
                allTrades.addAll(book.getTrades());
            }
        }
        return allTrades.stream()
                .sorted(Comparator.comparing(Trade::getTimestamp).thenComparing(Trade::getBuyerOrderId).thenComparing(Trade::getSellerOrderId))
                .collect(Collectors.toList());
    }

    /**
     * Snapshot for one symbol. Unknown or never-used symbol returns empty bids/asks.
     */
    public OrderBookSnapshot getOrderBookSnapshot(String symbol, int limit) {
        synchronized (lockForSymbol(symbol)) {
            OrderBook book = books.get(symbol);
            if (book == null) {
                return new OrderBookSnapshot(List.of(), List.of());
            }
            return book.getSnapshot(limit);
        }
    }

    /** For tests / inspection: bids for a symbol (empty book if none). */
    public TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> getBids(String symbol) {
        synchronized (lockForSymbol(symbol)) {
            OrderBook b = books.get(symbol);
            if (b == null) {
                return new TreeMap<>(Comparator.reverseOrder());
            }
            return b.getBids();
        }
    }

    /** For tests / inspection: asks for a symbol (empty book if none). */
    public TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> getAsks(String symbol) {
        synchronized (lockForSymbol(symbol)) {
            OrderBook b = books.get(symbol);
            if (b == null) {
                return new TreeMap<>();
            }
            return b.getAsks();
        }
    }
}