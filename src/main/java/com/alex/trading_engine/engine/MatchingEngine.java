package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderStatus;
import com.alex.trading_engine.model.Trade;
import com.alex.trading_engine.persistence.OpenOrderEntity;
import com.alex.trading_engine.persistence.OpenOrderPersistenceService;
import com.alex.trading_engine.persistence.OrderStatePersistenceService;
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
 *   <li>Open resting orders are synced to {@code open_orders} after each change (when persistence is enabled).</li>
 *   <li>Order status is written to {@code order_states} when persistence is enabled so {@link #getOrderStatus(String)} can answer after a restart.</li>
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
    private OpenOrderPersistenceService openOrderPersistenceService;
    private OrderStatePersistenceService orderStatePersistenceService;

    @Autowired(required = false)
    void setTradeRepository(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Autowired(required = false)
    void setOpenOrderPersistenceService(OpenOrderPersistenceService openOrderPersistenceService) {
        this.openOrderPersistenceService = openOrderPersistenceService;
    }

    @Autowired(required = false)
    void setOrderStatePersistenceService(OrderStatePersistenceService orderStatePersistenceService) {
        this.orderStatePersistenceService = orderStatePersistenceService;
    }
    
    private OrderBook bookFor(String symbol) {
        return books.computeIfAbsent(symbol, s -> new OrderBook());
    }

    private Object lockForSymbol(String symbol) {
        return symbolLocks.computeIfAbsent(symbol, s -> new Object());
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
            persistOrderState(result.orderId(), symbol, result.status());
            syncOpenOrdersForSymbol(symbol);
            return result;
        }
    }

    private void syncOpenOrdersForSymbol(String symbol) {
        if (openOrderPersistenceService == null) {
            return;
        }
        OrderBook book = books.get(symbol);
        Map<String, OrderBook.RestingOrderState> state =
                book == null ? Map.of() : book.collectRestingOrders();
        openOrderPersistenceService.syncSymbol(symbol, state);
    }

    /**
     * Loads {@code open_orders} from the database into memory (application startup / recovery).
     */
    public void replayOpenOrdersFromDatabase() {
        if (openOrderPersistenceService == null) {
            return;
        }
        for (OpenOrderEntity row : openOrderPersistenceService.loadAllOpenOrdersForReplay()) {
            Order order = new Order.Builder()
                    .id(row.getOrderId())
                    .symbol(row.getSymbol())
                    .price(row.getPrice())
                    .quantity(row.getRemainingQuantity())
                    .orderSide(row.getOrderSide())
                    .timestamp(row.getCreatedAt())
                    .build();
            restoreRestingOrderAfterReplay(order, row.getOriginalQuantity());
        }
    }

    private void restoreRestingOrderAfterReplay(Order order, BigDecimal originalQuantity) {
        String symbol = order.getSymbol();
        synchronized (lockForSymbol(symbol)) {
            bookFor(symbol).placeRestingWithoutMatch(order);
            OrderStatus status = order.getQuantity().compareTo(originalQuantity) < 0
                    ? OrderStatus.PARTIALLY_FILLED
                    : OrderStatus.ACCEPTED;
            orderStatuses.put(order.getId(), status);
            orderToSymbol.put(order.getId(), symbol);
            persistOrderState(order.getId(), symbol, status);
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
                persistOrderState(orderId, symbol, OrderStatus.CANCELLED);
                syncOpenOrdersForSymbol(symbol);
                return true;
            }
            return false;
        }
    }

    /**
     * Returns status from memory if present; otherwise loads from {@code order_states} when JPA is enabled
     * (e.g. {@code FILLED} or {@code CANCELLED} after a restart when the order is no longer in the book).
     */
    public Optional<OrderStatus> getOrderStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        OrderStatus inMemory = orderStatuses.get(orderId);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        if (orderStatePersistenceService != null) {
            return orderStatePersistenceService.findStatus(orderId);
        }
        return Optional.empty();
    }

    private void persistOrderState(String orderId, String symbol, OrderStatus status) {
        if (orderStatePersistenceService == null) {
            return;
        }
        orderStatePersistenceService.save(orderId, symbol, status);
    }

    /**
     * All trades, sorted for a deterministic list (timestamp, then buyer id, then seller id).
     * <p>
     * When a {@link TradeRepository} is present (running app with JPA), reads from the database so
     * history survives restarts. Unit tests without a repository still read from in-memory books.
     */
    public List<Trade> getTrades() {
        if (tradeRepository != null) {
            return tradeRepository.findAllByOrderByTimestampAscBuyerOrderIdAscSellerOrderIdAsc().stream()
                    .map(TradeEntity::toDomain)
                    .toList();
        }
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