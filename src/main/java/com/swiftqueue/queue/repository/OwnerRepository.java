package com.swiftqueue.queue.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftqueue.queue.model.Owner;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    Optional<Owner> findByEmail(String email);
    Boolean existsByEmail(String email);
}