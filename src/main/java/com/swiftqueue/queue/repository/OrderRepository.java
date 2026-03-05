package com.swiftqueue.queue.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.swiftqueue.queue.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Replicates: SELECT * FROM orders WHERE owner_id = ? AND status != 'completed' ORDER BY isPriority DESC, token ASC
    List<Order> findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(Long ownerId, String status);

    @Query("SELECT MAX(o.token) FROM Order o WHERE o.ownerId = :ownerId")
    Integer findMaxTokenByOwnerId(Long ownerId);
}