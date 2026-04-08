package com.alex.trading_engine.controller;

import com.alex.trading_engine.controller.dto.OrderBookResponse;
import com.alex.trading_engine.controller.dto.SubmitOrderResponse;
import com.alex.trading_engine.controller.dto.TradeResponse;
import com.alex.trading_engine.engine.MatchingEngine;
import com.alex.trading_engine.engine.OrderBookSnapshot;
import com.alex.trading_engine.model.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
public class OrderController {
    private final MatchingEngine matchingEngine;

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/order")
    public SubmitOrderResponse submitOrder(@Valid @RequestBody Order order) {
        var result = matchingEngine.processOrder(order);
        return new SubmitOrderResponse(result.orderId(), result.status());
    }

    @GetMapping("/trades")
    public List<TradeResponse> getTrades() {
        return matchingEngine.getTrades().stream()
                .map(TradeResponse::from)
                .toList();
    }

    @GetMapping("/orderbook")
    public OrderBookResponse getOrderBook(
            @RequestParam @NotBlank(message = "symbol is required") String symbol,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "limit must be at least 1") int limit) {
        OrderBookSnapshot snapshot = matchingEngine.getOrderBookSnapshot(symbol, limit);
        List<OrderBookResponse.PriceLevel> bids = snapshot.bids().stream()
                .map(p -> new OrderBookResponse.PriceLevel(p.price(), p.totalQuantity()))
                .toList();
        List<OrderBookResponse.PriceLevel> asks = snapshot.asks().stream()
                .map(p -> new OrderBookResponse.PriceLevel(p.price(), p.totalQuantity()))
                .toList();
        return new OrderBookResponse(bids, asks);
    }

    @DeleteMapping("/order/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        if (matchingEngine.cancelOrder(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
