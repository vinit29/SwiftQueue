package com.swiftqueue.queue.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftqueue.queue.model.Order;
import com.swiftqueue.queue.repository.OrderRepository;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/orders/token")
    public ResponseEntity<?> createOrderForOwner(@RequestBody Map<String, Object> payload, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        String customerName = (String) payload.getOrDefault("customerName", "Guest");
        String customerMobile = (String) payload.getOrDefault("customerMobile", "");
        
        return createOrder(ownerId, customerName, customerMobile, false);
    }

    @PostMapping("/getNewOrder")
    public ResponseEntity<?> registerCustomer(@RequestBody Map<String, Object> payload) {
        if (!payload.containsKey("ownerId")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Owner ID is required"));
        }

        Long ownerId = Long.valueOf(payload.get("ownerId").toString());
        String customerName = (String) payload.getOrDefault("customerName", "Guest");
        String customerMobile = (String) payload.getOrDefault("customerMobile", "");
        boolean isPriority = Boolean.TRUE.equals(payload.get("isPriority"));

        return createOrder(ownerId, customerName, customerMobile, isPriority);
    }

    private ResponseEntity<?> createOrder(Long ownerId, String customerName, String customerMobile, boolean isPriority) {
        // Simple token generation strategy: get max token for owner + 1
        // In a high-concurrency production app, use a dedicated Counter table/sequence with locking
        Integer maxToken = orderRepository.findMaxTokenByOwnerId(ownerId);
        int nextToken = (maxToken != null) ? maxToken + 1 : 101;

        Order order = new Order();
        order.setOwnerId(ownerId);
        order.setToken(nextToken);
        order.setCustomerName(customerName);
        order.setCustomerMobile(customerMobile);
        order.setPriority(isPriority);
        order.setStatus("waiting");
        order.setDuration(30); // Default duration

        Order savedOrder = orderRepository.save(order);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "token", nextToken,
            "id", savedOrder.getId(),
            "position", 0 // You might want to calculate actual position here
        ));
    }
}