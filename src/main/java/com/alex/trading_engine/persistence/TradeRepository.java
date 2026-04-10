package com.alex.trading_engine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    List<TradeEntity> findAllByOrderByTimestampAscBuyerOrderIdAscSellerOrderIdAsc();
}
