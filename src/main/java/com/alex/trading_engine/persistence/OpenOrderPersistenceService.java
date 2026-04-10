package com.alex.trading_engine.persistence;

import com.alex.trading_engine.engine.OrderBook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpenOrderPersistenceService {

    private final OpenOrderRepository openOrderRepository;

    public OpenOrderPersistenceService(OpenOrderRepository openOrderRepository) {
        this.openOrderRepository = openOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<OpenOrderEntity> loadAllOpenOrdersForReplay() {
        return openOrderRepository.findAllByOrderByCreatedAtAscOrderIdAsc();
    }

    /**
     * Replaces {@code open_orders} rows for this symbol with the current resting snapshot from the book.
     */
    @Transactional
    public void syncSymbol(String symbol, Map<String, OrderBook.RestingOrderState> restingByOrderId) {
        if (restingByOrderId.isEmpty()) {
            openOrderRepository.deleteBySymbol(symbol);
            return;
        }
        openOrderRepository.deleteBySymbolAndOrderIdNotIn(symbol, new ArrayList<>(restingByOrderId.keySet()));

        for (Map.Entry<String, OrderBook.RestingOrderState> e : restingByOrderId.entrySet()) {
            String orderId = e.getKey();
            OrderBook.RestingOrderState s = e.getValue();
            OpenOrderEntity entity = openOrderRepository.findById(orderId).orElse(null);
            if (entity == null) {
                entity = new OpenOrderEntity(
                        orderId,
                        symbol,
                        s.side(),
                        s.price(),
                        s.remainingQuantity(),
                        s.originalQuantity(),
                        s.createdAt());
            } else {
                entity.setRemainingQuantity(s.remainingQuantity());
            }
            openOrderRepository.save(entity);
        }
    }
}
