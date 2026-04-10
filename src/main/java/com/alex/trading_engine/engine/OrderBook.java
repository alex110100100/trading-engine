package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import com.alex.trading_engine.model.OrderStatus;
import com.alex.trading_engine.model.Trade;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order book for a single instrument (symbol): bids, asks, and trades for that symbol only.
 * Terminology:
 * <ul>
 *   <li><b>Incoming</b> - the order just submitted.</li>
 *   <li><b>Resting</b> - an order already in the book waiting to be matched (the other side).</li>
 * </ul>
 */
@Getter
public class OrderBook {

    /** Snapshot of one resting order for persistence / replay. */
    public record RestingOrderState(
            OrderSide side,
            BigDecimal price,
            BigDecimal remainingQuantity,
            BigDecimal originalQuantity,
            Instant createdAt) {}
    private final TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> bids =
            new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> asks = new TreeMap<>();
    private final List<Trade> trades = new ArrayList<>();

    public ProcessOrderResult processOrder(Order order) {
        BigDecimal remainingQty;
        if (order.getOrderSide() == OrderSide.BUY) {
            remainingQty = matchAgainstAsks(order);
        } else {
            remainingQty = matchAgainstBids(order);
        }
        OrderStatus status = computeStatus(remainingQty, order.getQuantity());
        return new ProcessOrderResult(order.getId(), status);
    }

    private static OrderStatus computeStatus(BigDecimal remainingQty, BigDecimal originalQty) {
        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) return OrderStatus.FILLED;
        if (remainingQty.compareTo(originalQty) < 0) return OrderStatus.PARTIALLY_FILLED;
        return OrderStatus.ACCEPTED;
    }

    public boolean cancelOrder(String orderId) {
        if (removeFromBook(orderId, bids)) {
            return true;
        }
        return removeFromBook(orderId, asks);
    }

    private boolean removeFromBook(String orderId, TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> book) {
        for (Iterator<Map.Entry<BigDecimal, LinkedHashMap<String, BookEntry>>> it = book.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BigDecimal, LinkedHashMap<String, BookEntry>> levelEntry = it.next();
            LinkedHashMap<String, BookEntry> level = levelEntry.getValue();
            if (level.containsKey(orderId)) {
                level.remove(orderId);
                if (level.isEmpty()) {
                    it.remove();
                }
                return true;
            }
        }
        return false;
    }

    private BigDecimal matchAgainstAsks(Order buyOrder) {
        BigDecimal remainingQty = buyOrder.getQuantity();
        String symbol = buyOrder.getSymbol();

        while (remainingQty.compareTo(BigDecimal.ZERO) > 0 && !asks.isEmpty()) {
            BigDecimal bestAskPrice = asks.firstKey();
            if (buyOrder.getPrice().compareTo(bestAskPrice) < 0) {
                break;
            }
            LinkedHashMap<String, BookEntry> level = asks.get(bestAskPrice);
            if (level.isEmpty()) {
                asks.remove(bestAskPrice);
                continue;
            }
            Map.Entry<String, BookEntry> first = level.entrySet().iterator().next();
            String restingOrderId = first.getKey();
            BookEntry resting = first.getValue();
            BigDecimal tradeQty = remainingQty.min(resting.getRemainingQuantity());

            trades.add(new Trade(
                    buyOrder.getId(),
                    restingOrderId,
                    symbol,
                    bestAskPrice,
                    tradeQty,
                    Instant.now()));

            resting.reduceBy(tradeQty);
            if (resting.isFilled()) {
                level.remove(restingOrderId);
                if (level.isEmpty()) {
                    asks.remove(bestAskPrice);
                }
            }
            remainingQty = remainingQty.subtract(tradeQty);
        }

        addRemainderToBook(buyOrder, remainingQty, bids);
        return remainingQty;
    }

    private BigDecimal matchAgainstBids(Order sellOrder) {
        BigDecimal remainingQty = sellOrder.getQuantity();
        String symbol = sellOrder.getSymbol();

        while (remainingQty.compareTo(BigDecimal.ZERO) > 0 && !bids.isEmpty()) {
            BigDecimal bestBidPrice = bids.firstKey();
            if (sellOrder.getPrice().compareTo(bestBidPrice) > 0) {
                break;
            }
            LinkedHashMap<String, BookEntry> level = bids.get(bestBidPrice);
            if (level.isEmpty()) {
                bids.remove(bestBidPrice);
                continue;
            }
            Map.Entry<String, BookEntry> first = level.entrySet().iterator().next();
            String restingOrderId = first.getKey();
            BookEntry resting = first.getValue();
            BigDecimal tradeQty = remainingQty.min(resting.getRemainingQuantity());

            trades.add(new Trade(
                    restingOrderId,
                    sellOrder.getId(),
                    symbol,
                    bestBidPrice,
                    tradeQty,
                    Instant.now()));

            resting.reduceBy(tradeQty);
            if (resting.isFilled()) {
                level.remove(restingOrderId);
                if (level.isEmpty()) {
                    bids.remove(bestBidPrice);
                }
            }
            remainingQty = remainingQty.subtract(tradeQty);
        }

        addRemainderToBook(sellOrder, remainingQty, asks);
        return remainingQty;
    }

    private void addRemainderToBook(Order order, BigDecimal remainingQty,
                                      TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> book) {
        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Order remainder = new Order.Builder()
                .id(order.getId())
                .symbol(order.getSymbol())
                .price(order.getPrice())
                .quantity(remainingQty)
                .orderSide(order.getOrderSide())
                .build();
        addToBook(remainder, book);
    }

    private void addToBook(Order order, TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> book) {
        book.computeIfAbsent(order.getPrice(), k -> new LinkedHashMap<>())
                .put(order.getId(), new BookEntry(order, order.getQuantity()));
    }

    /**
     * Places a resting order without matching (used when replaying {@code open_orders} after restart).
     */
    public void placeRestingWithoutMatch(Order order) {
        TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> sideBook =
                order.getOrderSide() == OrderSide.BUY ? bids : asks;
        for (LinkedHashMap<String, BookEntry> level : sideBook.values()) {
            if (level.containsKey(order.getId())) {
                throw new IllegalStateException("Order id already in book: " + order.getId());
            }
        }
        addToBook(order, sideBook);
    }

    /** All resting orders in this book (bids and asks), keyed by order id. */
    public Map<String, RestingOrderState> collectRestingOrders() {
        Map<String, RestingOrderState> out = new LinkedHashMap<>();
        collectSide(bids, OrderSide.BUY, out);
        collectSide(asks, OrderSide.SELL, out);
        return out;
    }

    private static void collectSide(
            TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> book,
            OrderSide side,
            Map<String, RestingOrderState> out) {
        for (Map.Entry<BigDecimal, LinkedHashMap<String, BookEntry>> levelEntry : book.entrySet()) {
            BigDecimal price = levelEntry.getKey();
            for (Map.Entry<String, BookEntry> e : levelEntry.getValue().entrySet()) {
                BookEntry be = e.getValue();
                Order o = be.getOrder();
                out.put(
                        o.getId(),
                        new RestingOrderState(
                                side,
                                price,
                                be.getRemainingQuantity(),
                                o.getQuantity(),
                                o.getTimestamp()));
            }
        }
    }

    public OrderBookSnapshot getSnapshot(int limit) {
        List<OrderBookSnapshot.PriceLevel> bidLevels = levelsFromBook(bids, limit);
        List<OrderBookSnapshot.PriceLevel> askLevels = levelsFromBook(asks, limit);
        return new OrderBookSnapshot(bidLevels, askLevels);
    }

    private List<OrderBookSnapshot.PriceLevel> levelsFromBook(
            TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>> book, int limit) {
        return book.entrySet().stream()
                .limit(limit)
                .map(e -> {
                    BigDecimal totalQty = e.getValue().values().stream()
                            .map(BookEntry::getRemainingQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new OrderBookSnapshot.PriceLevel(e.getKey(), totalQty);
                })
                .collect(Collectors.toList());
    }
}
