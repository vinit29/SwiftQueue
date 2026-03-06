package com.swiftqueue.queue.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftqueue.queue.model.Counter;

public interface CounterRepository extends JpaRepository<Counter, Long> {
    Optional<Counter> findByOwnerId(Long ownerId);
}