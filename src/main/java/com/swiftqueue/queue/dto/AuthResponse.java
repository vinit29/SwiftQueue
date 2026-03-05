package com.swiftqueue.queue.dto;

public record AuthResponse(String token, Long ownerId, String businessName, String businessType) {}