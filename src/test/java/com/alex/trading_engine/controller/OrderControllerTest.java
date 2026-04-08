package com.alex.trading_engine.controller;

import com.alex.trading_engine.engine.MatchingEngine;
import com.alex.trading_engine.engine.OrderBookSnapshot;
import com.alex.trading_engine.engine.ProcessOrderResult;
import com.alex.trading_engine.model.Order;
import com.alex.trading_engine.model.OrderSide;
import com.alex.trading_engine.model.OrderStatus;
import com.alex.trading_engine.model.Trade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchingEngine matchingEngine;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Register the JavaTimeModule
    }

    @Test
    void testSubmitOrder() throws Exception {
        // Create a test order using the Builder pattern
        Order order = new Order.Builder()
                .id("order1")
                .symbol("BTC/USD")
                .price(31000)
                .quantity(1)
                .orderSide(OrderSide.BUY)
                .build();

        when(matchingEngine.processOrder(any(Order.class)))
                .thenReturn(new ProcessOrderResult("order1", OrderStatus.ACCEPTED));

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void testGetTrades() throws Exception {
        Instant now = Instant.now();
        Trade trade = new Trade("buy1", "sell1", "BTC/USD", 31000.0, 1.0, now);
        when(matchingEngine.getTrades()).thenReturn(List.of(trade));

        mockMvc.perform(get("/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buyerOrderId").value("buy1"))
                .andExpect(jsonPath("$[0].sellerOrderId").value("sell1"))
                .andExpect(jsonPath("$[0].symbol").value("BTC/USD"))
                .andExpect(jsonPath("$[0].price").value(31000.0))
                .andExpect(jsonPath("$[0].quantity").value(1.0));
    }

    @Test
    void testCancelOrderReturnsNoContentWhenFound() throws Exception {
        when(matchingEngine.cancelOrder("order1")).thenReturn(true);

        mockMvc.perform(delete("/order/order1"))
                .andExpect(status().isNoContent());
        verify(matchingEngine).cancelOrder("order1");
    }

    @Test
    void testCancelOrderReturnsNotFoundWhenNotInBook() throws Exception {
        when(matchingEngine.cancelOrder("unknown")).thenReturn(false);

        mockMvc.perform(delete("/order/unknown"))
                .andExpect(status().isNotFound());
        verify(matchingEngine).cancelOrder("unknown");
    }

    @Test
    void testGetOrderBook() throws Exception {
        when(matchingEngine.getOrderBookSnapshot(10))
                .thenReturn(new OrderBookSnapshot(
                        List.of(new OrderBookSnapshot.PriceLevel(31000.0, 2.0)),
                        List.of(new OrderBookSnapshot.PriceLevel(31100.0, 1.0))));

        mockMvc.perform(get("/orderbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids.length()").value(1))
                .andExpect(jsonPath("$.bids[0].price").value(31000.0))
                .andExpect(jsonPath("$.bids[0].totalQuantity").value(2.0))
                .andExpect(jsonPath("$.asks.length()").value(1))
                .andExpect(jsonPath("$.asks[0].price").value(31100.0))
                .andExpect(jsonPath("$.asks[0].totalQuantity").value(1.0));
    }

    @Test
    void testSubmitOrderValidationFailureReturnsBadRequest() throws Exception {
        String invalidBody = """
                {
                  "id": "order1",
                  "symbol": "",
                  "price": 31000,
                  "quantity": 0,
                  "orderSide": "BUY"
                }
                """;

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.symbol").exists())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    void testSubmitOrderMalformedBodyReturnsBadRequest() throws Exception {
        String invalidEnumBody = """
                {
                  "id": "order1",
                  "symbol": "BTC/USD",
                  "price": 31000,
                  "quantity": 1,
                  "orderSide": "BID"
                }
                """;

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEnumBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }
}