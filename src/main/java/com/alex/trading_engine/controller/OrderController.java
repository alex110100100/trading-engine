package com.alex.trading_engine.controller;

import com.alex.trading_engine.controller.dto.OrderBookResponse;
import com.alex.trading_engine.controller.dto.OrderStatusResponse;
import com.alex.trading_engine.controller.dto.SubmitOrderResponse;
import com.alex.trading_engine.controller.dto.TradeResponse;
import com.alex.trading_engine.engine.MatchingEngine;
import com.alex.trading_engine.engine.OrderBookSnapshot;
import com.alex.trading_engine.model.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@Tag(name = "Orders", description = "Submit orders, view trades, order books, and cancel resting orders")
public class OrderController {
    private final MatchingEngine matchingEngine;

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/order")
    @Operation(
            summary = "Submit an order",
            description = "Body is validated; invalid input returns 400 with structured errors.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(
                                            name = "Limit buy",
                                            summary = "Sample BUY",
                                            value = """
                                                    {
                                                      "id": "swagger-demo-buy",
                                                      "symbol": "BTC/USD",
                                                      "price": 50000,
                                                      "quantity": 0.1,
                                                      "orderSide": "BUY"
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "Limit sell",
                                            summary = "Sample SELL",
                                            value = """
                                                    {
                                                      "id": "swagger-demo-sell",
                                                      "symbol": "BTC/USD",
                                                      "price": 51000,
                                                      "quantity": 0.25,
                                                      "orderSide": "SELL"
                                                    }
                                                    """)
                            })))
    public SubmitOrderResponse submitOrder(@Valid @RequestBody Order order) {
        var result = matchingEngine.processOrder(order);
        return new SubmitOrderResponse(result.orderId(), result.status());
    }

    @GetMapping("/trades")
    @Operation(
            summary = "List all trades",
            description = "Sorted by execution time (deterministic ties). With JPA enabled, reads persisted trade history from the database.")
    public List<TradeResponse> getTrades() {
        return matchingEngine.getTrades().stream()
                .map(TradeResponse::from)
                .toList();
    }

    @GetMapping("/orderbook")
    @Operation(summary = "Order book snapshot", description = "Top bid/ask price levels for one symbol only (e.g. BTC/USD).")
    public OrderBookResponse getOrderBook(
            @Parameter(description = "Instrument, e.g. BTC/USD", example = "BTC/USD", required = true)
            @RequestParam @NotBlank(message = "symbol is required") String symbol,
            @Parameter(description = "Max price levels per side", example = "10")
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
    @Operation(summary = "Cancel a resting order", description = "Searches all symbol books by order id. 204 if removed, 404 if not resting.")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        if (matchingEngine.cancelOrder(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/order/{id}")
    @Operation(
            summary = "Get order status",
            description = "Returns the latest known status for a submitted order. Uses in-memory state when present; with JPA enabled, falls back to persisted status in the database.")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable String id) {
        return matchingEngine.getOrderStatus(id)
                .map(status -> ResponseEntity.ok(new OrderStatusResponse(id, status)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
