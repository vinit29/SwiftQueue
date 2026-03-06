package com.swiftqueue.queue.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.swiftqueue.queue.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Replicates: SELECT * FROM orders WHERE owner_id = ? AND status != 'completed' ORDER BY isPriority DESC, token ASC
    List<Order> findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(Long ownerId, String status);

    @Query("SELECT MAX(o.token) FROM Order o WHERE o.ownerId = :ownerId")
    Integer findMaxTokenByOwnerId(Long ownerId);

    Optional<Order> findByTokenAndOwnerId(Integer token, Long ownerId);
    List<Order> findByOwnerIdAndStatusOrderByIdDesc(Long ownerId, String status);

    @Query("SELECT new map(MAX(o.id) as id, COALESCE(o.customerName, 'Guest') as name, COALESCE(o.customerMobile, '') as mobile, COUNT(o) as visitCount, MAX(o.createdAt) as lastVisit) FROM Order o WHERE o.ownerId = :ownerId GROUP BY o.customerName, o.customerMobile")
    List<Map<String, Object>> findCustomerStatsByOwnerId(Long ownerId);
}