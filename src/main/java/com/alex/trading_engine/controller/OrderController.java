package com.alex.trading_engine.controller;

import com.alex.trading_engine.engine.MatchingEngine;
import com.alex.trading_engine.model.Order;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
    private final MatchingEngine matchingEngine = new MatchingEngine();

    @PostMapping("/order")
    public String submitOrder(@RequestBody Order order) {
        matchingEngine.processOrder(order);
        return "Order received: " + order.getId();
    }
}
