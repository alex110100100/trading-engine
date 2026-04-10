package com.alex.trading_engine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OpenOrderRepository extends JpaRepository<OpenOrderEntity, String> {

    List<OpenOrderEntity> findAllBySymbol(String symbol);

    List<OpenOrderEntity> findAllByOrderByCreatedAtAscOrderIdAsc();

    @Modifying
    @Query("DELETE FROM OpenOrderEntity o WHERE o.symbol = :symbol")
    void deleteBySymbol(@Param("symbol") String symbol);

    @Modifying
    @Query("DELETE FROM OpenOrderEntity o WHERE o.symbol = :symbol AND o.orderId NOT IN :keepIds")
    void deleteBySymbolAndOrderIdNotIn(@Param("symbol") String symbol, @Param("keepIds") List<String> keepIds);
}
