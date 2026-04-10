package com.alex.trading_engine.engine;

import java.util.List;

/**
 * Result of matching the incoming order, plus status updates for orders on the passive (resting) side of trades.
 */
public record ProcessOrderOutcome(ProcessOrderResult incoming, List<PassiveOrderStatusUpdate> passiveUpdates) {
}
