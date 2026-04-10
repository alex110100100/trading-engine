package com.alex.trading_engine.config;

import com.alex.trading_engine.engine.MatchingEngine;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Reloads resting orders from {@code open_orders} into memory after JPA is ready (e.g. after restart).
 */
@Component
@Order(20)
public class OpenOrderReplayRunner implements ApplicationRunner {

    private final MatchingEngine matchingEngine;

    public OpenOrderReplayRunner(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @Override
    public void run(ApplicationArguments args) {
        matchingEngine.replayOpenOrdersFromDatabase();
    }
}
