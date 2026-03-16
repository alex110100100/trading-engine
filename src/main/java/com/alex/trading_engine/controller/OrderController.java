package com.alex.trading_engine.controller;

import com.alex.trading_engine.engine.MatchingEngine;
import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.Trade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OrderController {
    private final MatchingEngine matchingEngine;

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/order")
    public String submitOrder(@RequestBody Order order) {
        matchingEngine.processOrder(order);
        return "Order received: " + order.getId();
    }

    @GetMapping("/trades")
    public List<Trade> getTrades() {
        return matchingEngine.getTrades();
    }

    @DeleteMapping("/order/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        if (matchingEngine.cancelOrder(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
