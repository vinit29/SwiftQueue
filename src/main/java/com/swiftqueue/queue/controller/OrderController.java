package com.swiftqueue.queue.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftqueue.queue.model.Counter;
import com.swiftqueue.queue.model.Order;
import com.swiftqueue.queue.model.Owner;
import com.swiftqueue.queue.repository.CounterRepository;
import com.swiftqueue.queue.repository.OrderRepository;
import com.swiftqueue.queue.repository.OwnerRepository;

import tools.jackson.databind.ObjectMapper;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CounterRepository counterRepository;
    private final OwnerRepository ownerRepository;
    private final ObjectMapper objectMapper;

    public OrderController(OrderRepository orderRepository, CounterRepository counterRepository, OwnerRepository ownerRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.counterRepository = counterRepository;
        this.ownerRepository = ownerRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        
        List<Map<String, Object>> response = new ArrayList<>();
        int cumulativeWaitTime = 0;

        for (Order order : orders) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orderMap = objectMapper.convertValue(order, Map.class);
            
            if ("serving".equals(order.getStatus())) {
                // If serving, the wait time for THIS customer is 0 (or serving)
                // But they contribute their duration to the NEXT customer's wait
                orderMap.put("estimatedWaitTime", 0);
                cumulativeWaitTime += order.getDuration();
            } else {
                // If waiting, their wait time is the sum of durations of everyone ahead
                orderMap.put("estimatedWaitTime", cumulativeWaitTime);
                cumulativeWaitTime += order.getDuration();
            }
            
            response.add(orderMap);
        }
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/orders/{token}/serve")
    @Transactional
    public ResponseEntity<?> serveOrder(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        Order order = orderRepository.findByTokenAndOwnerId(token, ownerId).orElse(null);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        order.setStatus("serving");
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{token}/done")
    @Transactional
    public ResponseEntity<?> completeOrder(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        Order order = orderRepository.findByTokenAndOwnerId(token, ownerId).orElse(null);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        order.setStatus("completed");
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{token}/priority")
    @Transactional
    public ResponseEntity<?> togglePriority(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        Order order = orderRepository.findByTokenAndOwnerId(token, ownerId).orElse(null);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        order.setPriority(!order.isPriority());
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/orders/history")
    public ResponseEntity<List<Order>> getHistory(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        List<Order> orders = orderRepository.findByOwnerIdAndStatusOrderByIdDesc(ownerId, "completed");
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customers")
    public ResponseEntity<List<Map<String, Object>>> getCustomers(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        List<Map<String, Object>> customers = orderRepository.findCustomerStatsByOwnerId(ownerId);
        return ResponseEntity.ok(customers);
    }

    @PostMapping("/orders/token")
    @Transactional
    public ResponseEntity<?> createOrderForOwner(@RequestBody Map<String, Object> payload, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        String customerName = (String) payload.get("customerName");
        String customerMobile = (String) payload.get("customerMobile");

        if (customerName == null || customerName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Customer name is required when creating a token manually."));
        }

        if (customerMobile == null) {
            customerMobile = "";
        }
        
        return createOrder(ownerId, customerName.trim(), customerMobile.trim(), false);
    }

    @PostMapping("/getNewOrder")
    @Transactional
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
        Counter counter = counterRepository.findByOwnerId(ownerId).orElse(null);
        
        // Fallback if counter doesn't exist (e.g. old owners)
        if (counter == null) {
            counter = new Counter(ownerId, 100, 30);
            counterRepository.save(counter);
        }

        int nextToken = counter.getSequence() + 1;
        counter.setSequence(nextToken);
        counterRepository.save(counter);

        Order order = new Order();
        order.setOwnerId(ownerId);
        order.setToken(nextToken);
        order.setCustomerName(customerName);
        order.setCustomerMobile(customerMobile);
        order.setPriority(isPriority);
        order.setStatus("waiting");
        order.setDuration(counter.getAverageTime()); // Use average time from counter

        Order savedOrder = orderRepository.save(order);

        Owner owner = ownerRepository.findById(ownerId).orElse(null);
        String businessName = (owner != null) ? owner.getBusinessName() : "";

        return ResponseEntity.ok(Map.of(
            "success", true,
            "token", nextToken,
            "id", savedOrder.getId(),
            "position", 0, // You might want to calculate actual position here
            "businessName", businessName,
            "customerName", customerName,
            "customerMobile", customerMobile
        ));
    }

    @GetMapping("/orders/{token}/status")
    public ResponseEntity<?> getCustomerOrderStatus(@PathVariable Integer token, @RequestParam Long ownerId) {
        Order order = orderRepository.findByTokenAndOwnerId(token, ownerId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        // Calculate wait time for this specific order
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        int waitTime = 0;
        
        for (Order o : orders) {
            if (o.getId().equals(order.getId())) {
                break;
            }
            waitTime += o.getDuration();
        }

        // If status is serving, wait time is 0
        if ("serving".equals(order.getStatus())) {
            waitTime = 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.convertValue(order, Map.class);
        response.put("estimatedWaitTime", waitTime);
        
        return ResponseEntity.ok(response);
    }
}