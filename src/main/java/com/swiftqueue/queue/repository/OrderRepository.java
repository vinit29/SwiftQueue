package com.swiftqueue.queue.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftqueue.queue.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Replaces: SELECT * FROM orders WHERE owner_id = ? AND status != 'completed'
    List<Order> findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(Long ownerId, String status);
}
