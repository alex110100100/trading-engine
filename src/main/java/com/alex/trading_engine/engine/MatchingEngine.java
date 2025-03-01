package com.alex.trading_engine.engine;

import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import lombok.Getter;

import java.util.*;

@Getter
public class MatchingEngine {
    // Bids (sorted highest first) and Asks (sorted lowest first)
    private final TreeMap<Double, LinkedHashMap<String, Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Double, LinkedHashMap<String, Order>> asks = new TreeMap<>();

    public void processOrder(Order order) {
        if (order.getOrderSide() == OrderSide.BUY) {
            matchAgainstAsks(order); // Buy order matches against asks
        } else {
            matchAgainstBids(order); // Sell order matches against bids
        }
    }

    private void matchAgainstAsks(Order buyOrder) {
        Optional<Double> bestAskPrice = asks.keySet().stream().findFirst();
        if (bestAskPrice.isPresent() && buyOrder.getPrice() >= bestAskPrice.get()) {
            LinkedHashMap<String, Order> askOrders = asks.get(bestAskPrice.get());
            Order matchedAsk = askOrders.values().iterator().next(); // Get the oldest order at this price
            System.out.println("MATCHED! Buy order " + buyOrder.getId() + " @ " + bestAskPrice.get());

            askOrders.remove(matchedAsk.getId());
            if (askOrders.isEmpty()) {
                asks.remove(bestAskPrice.get()); // Remove the price level if no orders left at that price
            }
        } else {
            addToBook(buyOrder, bids);
        }
    }

    private void matchAgainstBids(Order sellOrder) {
        Optional<Double> bestBidPrice = bids.keySet().stream().findFirst();
        if (bestBidPrice.isPresent() && sellOrder.getPrice() <= bestBidPrice.get()) {
            LinkedHashMap<String, Order> bidOrders = bids.get(bestBidPrice.get());
            Order matchedBid = bidOrders.values().iterator().next(); // Get the oldest order at this price
            System.out.println("MATCHED! Sell order " + sellOrder.getId() + " @ " + bestBidPrice.get());

            bidOrders.remove(matchedBid.getId());
            if (bidOrders.isEmpty()) {
                bids.remove(bestBidPrice.get()); // Remove the price level if no orders left at that price
            }
        } else {
            addToBook(sellOrder, asks);
        }
    }

    private void addToBook(Order order, TreeMap<Double, LinkedHashMap<String, Order>> book) {
        book.computeIfAbsent(order.getPrice(), k -> new LinkedHashMap<>())
                .put(order.getId(), order);
        System.out.println("ADDED TO BOOK: " + order);
    }
}
