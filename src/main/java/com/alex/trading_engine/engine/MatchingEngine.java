package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import com.alex.trading_engine.model.Trade;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Matches orders using price-time priority. Terminology:
 * <ul>
 *   <li><b>Incoming</b> – the order just submitted.</li>
 *   <li><b>Resting</b> – an order already in the book waiting to be matched (the "other side").</li>
 * </ul>
 */
@Service
@Getter
public class MatchingEngine {
    // Two order books: Bids (sorted highest first) and Asks (sorted lowest first); each level holds BookEntry for partial-fill tracking
    private final TreeMap<Double, LinkedHashMap<String, BookEntry>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Double, LinkedHashMap<String, BookEntry>> asks = new TreeMap<>();
    private final List<Trade> trades = new ArrayList<>();

    public void processOrder(Order order) {
        if (order.getOrderSide() == OrderSide.BUY) {
            matchAgainstAsks(order);
        } else {
            matchAgainstBids(order);
        }
    }


    private void matchAgainstAsks(Order buyOrder) {
        double remainingQty = buyOrder.getQuantity();
        String symbol = buyOrder.getSymbol();

        while (remainingQty > 0 && !asks.isEmpty()) {
            Double bestAskPrice = asks.firstKey();
            if (buyOrder.getPrice() < bestAskPrice) {
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
            double tradeQty = Math.min(remainingQty, resting.getRemainingQuantity());

            trades.add(new Trade(buyOrder.getId(), restingOrderId, symbol, bestAskPrice, tradeQty, Instant.now()));

            resting.reduceBy(tradeQty);
            if (resting.isFilled()) {
                level.remove(restingOrderId);
                if (level.isEmpty()) {
                    asks.remove(bestAskPrice);
                }
            }
            remainingQty -= tradeQty;
        }

        addRemainderToBook(buyOrder, remainingQty, bids);
    }

    private void matchAgainstBids(Order sellOrder) {
        double remainingQty = sellOrder.getQuantity();
        String symbol = sellOrder.getSymbol();

        while (remainingQty > 0 && !bids.isEmpty()) {
            Double bestBidPrice = bids.firstKey();
            if (sellOrder.getPrice() > bestBidPrice) {
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
            double tradeQty = Math.min(remainingQty, resting.getRemainingQuantity());

            trades.add(new Trade(restingOrderId, sellOrder.getId(), symbol, bestBidPrice, tradeQty, Instant.now()));

            resting.reduceBy(tradeQty);
            if (resting.isFilled()) {
                level.remove(restingOrderId);
                if (level.isEmpty()) {
                    bids.remove(bestBidPrice);
                }
            }
            remainingQty -= tradeQty;
        }

        addRemainderToBook(sellOrder, remainingQty, asks);
    }

    private void addRemainderToBook(Order order, double remainingQty,
                                    TreeMap<Double, LinkedHashMap<String, BookEntry>> book) {
        if (remainingQty <= 0) {
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

    private void addToBook(Order order, TreeMap<Double, LinkedHashMap<String, BookEntry>> book) {
        book.computeIfAbsent(order.getPrice(), k -> new LinkedHashMap<>())
                .put(order.getId(), new BookEntry(order, order.getQuantity()));
    }
}
