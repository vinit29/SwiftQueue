package com.swiftqueue.queue.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftqueue.queue.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(Long ownerId, String status);

    List<Order> findByOwnerIdAndStatusOrderByIdDesc(Long ownerId, String status);

    @Query("SELECT new map(o.customerName as name, COUNT(o) as visitCount, MAX(o.createdAt) as lastVisit) FROM Order o WHERE o.ownerId = :ownerId GROUP BY o.customerName")
    List<Map<String, Object>> findCustomerStatsByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT COALESCE(SUM(o.duration), 0) FROM Order o WHERE o.ownerId = :ownerId AND o.status IN ('waiting', 'serving') AND (o.status = 'serving' OR (o.isPriority = true AND :isPriority = false) OR (o.isPriority = :isPriority AND o.token < :token))")
    Long getWaitTimeAhead(@Param("ownerId") Long ownerId, @Param("isPriority") boolean isPriority, @Param("token") Integer token);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.ownerId = :ownerId AND o.status IN ('waiting', 'serving') AND (o.status = 'serving' OR (o.isPriority = true AND :isPriority = false) OR (o.isPriority = :isPriority AND o.token < :token))")
    Long getPositionAhead(@Param("ownerId") Long ownerId, @Param("isPriority") boolean isPriority, @Param("token") Integer token);

    Optional<Order> findByOwnerIdAndToken(Long ownerId, Integer token);
}