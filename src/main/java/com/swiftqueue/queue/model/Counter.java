package com.swiftqueue.queue.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "counter")
public class Counter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long ownerId;
    private Integer sequence;
    private Integer averageTime;

    public Counter() {}

    public Counter(Long ownerId, Integer sequence, Integer averageTime) {
        this.ownerId = ownerId;
        this.sequence = sequence;
        this.averageTime = averageTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public Integer getAverageTime() { return averageTime; }
    public void setAverageTime(Integer averageTime) { this.averageTime = averageTime; }
}
