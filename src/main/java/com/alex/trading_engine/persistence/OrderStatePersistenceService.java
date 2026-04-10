package com.alex.trading_engine.persistence;

import com.alex.trading_engine.model.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderStatePersistenceService {

    private final OrderStateRepository orderStateRepository;

    public OrderStatePersistenceService(OrderStateRepository orderStateRepository) {
        this.orderStateRepository = orderStateRepository;
    }

    @Transactional
    public void save(String orderId, String symbol, OrderStatus status) {
        OrderStateEntity entity = orderStateRepository.findById(orderId).orElse(null);
        if (entity == null) {
            entity = new OrderStateEntity(orderId, symbol, status);
        } else {
            entity.setSymbol(symbol);
            entity.setStatus(status);
        }
        orderStateRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<OrderStatus> findStatus(String orderId) {
        return orderStateRepository.findById(orderId).map(OrderStateEntity::getStatus);
    }
}
