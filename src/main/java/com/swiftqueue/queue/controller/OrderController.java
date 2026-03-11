package com.swiftqueue.queue.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftqueue.queue.dto.CreateOrderForOwnerRequest;
import com.swiftqueue.queue.dto.NewOrderRequest;
import com.swiftqueue.queue.model.Order;
import com.swiftqueue.queue.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        return ResponseEntity.ok(orderService.getOrders(ownerId));
    }

    @PutMapping("/orders/{token}/serve")
    public ResponseEntity<?> serveOrder(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        orderService.serveOrder(ownerId, token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{token}/done")
    public ResponseEntity<?> completeOrder(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        orderService.completeOrder(ownerId, token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{token}/priority")
    public ResponseEntity<?> togglePriority(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        orderService.togglePriority(ownerId, token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{token}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Integer token, @RequestParam Long ownerId) {
        orderService.cancelOrder(ownerId, token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{id}/extend")
    public ResponseEntity<?> extendOrderTime(@PathVariable Long id, @RequestBody Map<String, Integer> payload, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        
        if (!payload.containsKey("extraMinutes")) {
            return ResponseEntity.badRequest().body(Map.of("error", "extraMinutes is required"));
        }
        int extraMinutes = payload.get("extraMinutes");

        Order savedOrder = orderService.extendOrderTime(ownerId, id, extraMinutes);
        return ResponseEntity.ok(savedOrder);
    }

    @GetMapping("/orders/history")
    public ResponseEntity<List<Order>> getHistory(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        return ResponseEntity.ok(orderService.getHistory(ownerId));
    }

    @GetMapping("/customers")
    public ResponseEntity<List<Map<String, Object>>> getCustomers(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        return ResponseEntity.ok(orderService.getCustomers(ownerId));
    }

    @PostMapping("/orders/token")
    public ResponseEntity<?> createOrderForOwner(@RequestBody CreateOrderForOwnerRequest request, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        return ResponseEntity.ok(orderService.createOrderForOwner(ownerId, request));
    }

    @PostMapping("/getNewOrder")
    public ResponseEntity<?> registerCustomer(@Valid @RequestBody NewOrderRequest request) {
        return ResponseEntity.ok(orderService.registerCustomer(request));
    }

    @GetMapping("/orders/{token}/status")
    public ResponseEntity<?> getCustomerOrderStatus(@PathVariable Integer token, @RequestParam Long ownerId) {
        return ResponseEntity.ok(orderService.getCustomerOrderStatus(ownerId, token));
    }
}