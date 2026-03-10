package com.swiftqueue.queue.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftqueue.queue.model.Counter;
import com.swiftqueue.queue.model.Order;
import com.swiftqueue.queue.model.Owner;
import com.swiftqueue.queue.repository.CounterRepository;
import com.swiftqueue.queue.repository.OrderRepository;
import com.swiftqueue.queue.repository.OwnerRepository;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CounterRepository counterRepository;
    private final OwnerRepository ownerRepository;

    public OrderController(OrderRepository orderRepository, CounterRepository counterRepository, OwnerRepository ownerRepository) {
        this.orderRepository = orderRepository;
        this.counterRepository = counterRepository;
        this.ownerRepository = ownerRepository;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        
        // Filter out cancelled orders
        orders.removeIf(o -> "cancelled".equals(o.getStatus()));
        
        // The default repository sort is by priority then token.
        // We need to ensure 'serving' status is always first, then priority items.
        // So we re-sort the list here to establish the correct order.
        orders.sort(Comparator
            .comparing((Order o) -> "serving".equals(o.getStatus()) ? 0 : 1) // 'serving' comes first
            .thenComparing(Order::isPriority, Comparator.reverseOrder())      // then priority items
            .thenComparing(Order::getToken));                                 // then by token number
        
        List<Map<String, Object>> response = new ArrayList<>(orders.size());
        int cumulativeWaitTime = 0;

        for (Order order : orders) {
            int estimatedWaitTime = "serving".equals(order.getStatus()) ? 0 : cumulativeWaitTime;
            
            cumulativeWaitTime += order.getDuration();
            
            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("id", order.getId());
            orderMap.put("token", order.getToken());
            orderMap.put("customerName", order.getCustomerName());
            orderMap.put("customerMobile", order.getCustomerMobile());
            orderMap.put("isPriority", order.isPriority());
            orderMap.put("status", order.getStatus());
            orderMap.put("duration", order.getDuration());
            orderMap.put("estimatedWaitTime", estimatedWaitTime);
            
            response.add(orderMap);
        }
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/orders/{token}/serve")
    @Transactional
    public ResponseEntity<?> serveOrder(@PathVariable Integer token, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        Order order = orders.stream().filter(o -> o.getToken().equals(token)).findFirst().orElse(null);

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
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        Order order = orders.stream().filter(o -> o.getToken().equals(token)).findFirst().orElse(null);

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
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        Order order = orders.stream().filter(o -> o.getToken().equals(token)).findFirst().orElse(null);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        order.setPriority(!order.isPriority());
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{token}/cancel")
    @Transactional
    public ResponseEntity<?> cancelOrder(@PathVariable Integer token, @RequestParam Long ownerId) {
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        Order order = orders.stream().filter(o -> o.getToken().equals(token)).findFirst().orElse(null);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        order.setStatus("cancelled");
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/orders/{id}/extend")
    @Transactional
    public ResponseEntity<?> extendOrderTime(@PathVariable Long id, @RequestBody Map<String, Integer> payload, Principal principal) {
        Long ownerId = Long.valueOf(principal.getName());
        
        if (!payload.containsKey("extraMinutes")) {
            return ResponseEntity.badRequest().body(Map.of("error", "extraMinutes is required"));
        }
        int extraMinutes = payload.get("extraMinutes");

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Security check: ensure the order belongs to the authenticated owner
        if (!order.getOwnerId().equals(ownerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to modify this order."));
        }

        int newDuration = order.getDuration() + extraMinutes;
        if (newDuration < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Duration cannot be less than 1 minute."));
        }

        order.setDuration(newDuration);
        Order savedOrder = orderRepository.save(order);

        return ResponseEntity.ok(savedOrder);
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
            customerName = "Guest";
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
        
        String customerName = (String) payload.get("customerName");
        if (customerName == null || customerName.trim().isEmpty()) {
            customerName = "Guest";
        }
        
        String customerMobile = (String) payload.get("customerMobile");
        if (customerMobile == null) {
            customerMobile = "";
        }
        
        boolean isPriority = Boolean.TRUE.equals(payload.get("isPriority"));

        return createOrder(ownerId, customerName.trim(), customerMobile.trim(), isPriority);
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

        // Calculate estimated wait time for the new order
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        orders.sort(Comparator
            .comparing((Order o) -> "serving".equals(o.getStatus()) ? 0 : 1)
            .thenComparing(Order::isPriority, Comparator.reverseOrder())
            .thenComparing(Order::getToken));

        int estimatedWaitTime = 0;
        int cumulativeWaitTime = 0;
        int position = 0;
        for (Order o : orders) {
            int currentWait = "serving".equals(o.getStatus()) ? 0 : cumulativeWaitTime;
            if (o.getId().equals(savedOrder.getId())) {
                estimatedWaitTime = currentWait;
                break;
            }
            position++;
            cumulativeWaitTime += o.getDuration();
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "token", nextToken,
            "id", savedOrder.getId(),
            "position", position,
            "businessName", businessName,
            "customerName", customerName,
            "customerMobile", customerMobile,
            "duration", savedOrder.getDuration(),
            "estimatedWaitTime", estimatedWaitTime
        ));
    }

    @GetMapping("/orders/{token}/status")
    public ResponseEntity<?> getCustomerOrderStatus(@PathVariable Integer token, @RequestParam Long ownerId) {
        // Calculate wait time for this specific order
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        
        // Apply consistent sorting: Serving -> Priority -> Token
        orders.sort(Comparator
            .comparing((Order o) -> "serving".equals(o.getStatus()) ? 0 : 1)
            .thenComparing(Order::isPriority, Comparator.reverseOrder())
            .thenComparing(Order::getToken));

        Order order = orders.stream().filter(o -> o.getToken().equals(token)).findFirst().orElse(null);
        
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        int waitTime = 0;
        int cumulativeWaitTime = 0;
        int position = 0;
        
        for (Order o : orders) {
            int currentWait = "serving".equals(o.getStatus()) ? 0 : cumulativeWaitTime;
            if (o.getId().equals(order.getId())) {
                waitTime = currentWait;
                break;
            }
            position++;
            cumulativeWaitTime += o.getDuration();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", order.getId());
        response.put("token", order.getToken());
        response.put("customerName", order.getCustomerName());
        response.put("customerMobile", order.getCustomerMobile());
        response.put("isPriority", order.isPriority());
        response.put("status", order.getStatus());
        response.put("duration", order.getDuration());
        response.put("estimatedWaitTime", waitTime);
        response.put("position", position);
        response.put("ownerId", order.getOwnerId());
        
        return ResponseEntity.ok(response);
    }
}