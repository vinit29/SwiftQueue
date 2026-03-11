package com.swiftqueue.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NewOrderRequest(
    @NotNull(message = "Owner ID is required")
    Long ownerId,

    @NotBlank(message = "Customer name is required")
    String customerName,

    @Pattern(regexp = "^\\d{10}$", message = "A valid 10-digit mobile number is required")
    String customerMobile,
    
    Boolean isPriority
) {}