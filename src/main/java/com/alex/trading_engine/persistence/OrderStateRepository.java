package com.alex.trading_engine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStateRepository extends JpaRepository<OrderStateEntity, String> {
}
