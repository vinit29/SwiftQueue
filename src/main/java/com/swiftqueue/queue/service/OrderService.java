package com.swiftqueue.queue.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftqueue.queue.dto.CreateOrderForOwnerRequest;
import com.swiftqueue.queue.dto.NewOrderRequest;
import com.swiftqueue.queue.model.Counter;
import com.swiftqueue.queue.model.Order;
import com.swiftqueue.queue.model.Owner;
import com.swiftqueue.queue.repository.CounterRepository;
import com.swiftqueue.queue.repository.OrderRepository;
import com.swiftqueue.queue.repository.OwnerRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CounterRepository counterRepository;
    private final OwnerRepository ownerRepository;

    public OrderService(OrderRepository orderRepository, CounterRepository counterRepository, OwnerRepository ownerRepository) {
        this.orderRepository = orderRepository;
        this.counterRepository = counterRepository;
        this.ownerRepository = ownerRepository;
    }

    public List<Map<String, Object>> getOrders(Long ownerId) {
        List<Order> orders = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed");
        orders.removeIf(o -> "cancelled".equals(o.getStatus()));

        // Ensure 'serving' status is always first
        orders.sort(Comparator
            .comparing((Order o) -> "serving".equals(o.getStatus()) ? 0 : 1)
            .thenComparing(Order::isPriority, Comparator.reverseOrder())
            .thenComparing(Order::getToken));

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
        return response;
    }

    @Transactional
    public void serveOrder(Long ownerId, Integer token) {
        updateOrderStatus(ownerId, token, order -> {
            order.setStatus("serving");
            order.setUpdatedAt(LocalDateTime.now());
        });
    }

    @Transactional
    public void completeOrder(Long ownerId, Integer token) {
        updateOrderStatus(ownerId, token, order -> order.setStatus("completed"));
    }

    @Transactional
    public void togglePriority(Long ownerId, Integer token) {
        updateOrderStatus(ownerId, token, order -> order.setPriority(!order.isPriority()));
    }

    @Transactional
    public void cancelOrder(Long ownerId, Integer token) {
        updateOrderStatus(ownerId, token, order -> order.setStatus("cancelled"));
    }

    private void updateOrderStatus(Long ownerId, Integer token, Consumer<Order> action) {
        // Optimized: fetch single order by token instead of iterating list
        Order order = orderRepository.findByOwnerIdAndToken(ownerId, token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!"completed".equals(order.getStatus()) && !"cancelled".equals(order.getStatus())) {
             action.accept(order);
             orderRepository.save(order);
        }
    }

    @Transactional
    public Order extendOrderTime(Long ownerId, Long orderId, int extraMinutes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to modify this order.");
        }

        int newDuration = order.getDuration() + extraMinutes;
        if (newDuration < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration cannot be less than 1 minute.");
        }

        order.setDuration(newDuration);
        return orderRepository.save(order);
    }

    public List<Order> getHistory(Long ownerId) {
        return orderRepository.findByOwnerIdAndStatusOrderByIdDesc(ownerId, "completed");
    }

    public List<Map<String, Object>> getCustomers(Long ownerId) {
        return orderRepository.findCustomerStatsByOwnerId(ownerId);
    }

    @Transactional
    public Map<String, Object> createOrderForOwner(Long ownerId, CreateOrderForOwnerRequest request) {
        String customerName = request.customerName();
        String customerMobile = request.customerMobile();

        if (customerName == null || customerName.trim().isEmpty()) {
            customerName = "Guest";
        }
        if (customerMobile == null) {
            customerMobile = "";
        }
        return createOrder(ownerId, customerName.trim(), customerMobile.trim(), false);
    }

    @Transactional
    public Map<String, Object> registerCustomer(NewOrderRequest request) {
        boolean isPriority = Boolean.TRUE.equals(request.isPriority());
        return createOrder(request.ownerId(), request.customerName().trim(), request.customerMobile().trim(), isPriority);
    }

    private Map<String, Object> createOrder(Long ownerId, String customerName, String customerMobile, boolean isPriority) {
        Counter counter = counterRepository.findByOwnerId(ownerId).orElseGet(() -> {
            Counter newCounter = new Counter(ownerId, 100, 30);
            return counterRepository.save(newCounter);
        });

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
        order.setDuration(counter.getAverageTime());

        Order savedOrder = orderRepository.save(order);

        Owner owner = ownerRepository.findById(ownerId).orElse(null);
        String businessName = (owner != null) ? owner.getBusinessName() : "";

        // Optimization: Use DB aggregation instead of iterating all orders in memory
        int estimatedWaitTime = orderRepository.getWaitTimeAhead(ownerId, isPriority, nextToken).intValue();
        int position = orderRepository.getPositionAhead(ownerId, isPriority, nextToken).intValue();

        return Map.of(
            "success", true, "token", nextToken, "id", savedOrder.getId(),
            "position", position, "businessName", businessName, "customerName", customerName,
            "customerMobile", customerMobile, "duration", savedOrder.getDuration(),
            "estimatedWaitTime", estimatedWaitTime
        );
    }

    public Map<String, Object> getCustomerOrderStatus(Long ownerId, Integer token) {
        // Optimized: Fetch specific order directly
        Order order = orderRepository.findByOwnerIdAndToken(ownerId, token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Fetch serving orders for display (usually small list)
        List<Map<String, Object>> servingNowList = orderRepository.findByOwnerIdAndStatusNotOrderByIsPriorityDescTokenAsc(ownerId, "completed")
            .stream()
            .filter(o -> "serving".equals(o.getStatus()))
            .map(servingOrder -> {
                Map<String, Object> servingMap = new HashMap<>();
                servingMap.put("token", servingOrder.getToken());
                long totalSeconds = servingOrder.getDuration() * 60L;
                long remainingSeconds = totalSeconds;
                if (servingOrder.getUpdatedAt() != null) {
                    long elapsedSeconds = Duration.between(servingOrder.getUpdatedAt(), LocalDateTime.now()).getSeconds();
                    remainingSeconds = Math.max(0, totalSeconds - elapsedSeconds);
                }
                servingMap.put("timeLeft", remainingSeconds);
                return servingMap;
            }).toList();

        int waitTime = 0;
        int position = 0;

        if ("waiting".equals(order.getStatus())) {
            // Optimization: Use DB aggregation
            waitTime = orderRepository.getWaitTimeAhead(ownerId, order.isPriority(), token).intValue();
            position = orderRepository.getPositionAhead(ownerId, order.isPriority(), token).intValue();
        }

        Map<String, Object> orderMap = Map.of(
            "id", order.getId(), "token", order.getToken(), "customerName", order.getCustomerName(),
            "customerMobile", order.getCustomerMobile(), "isPriority", order.isPriority(),
            "status", order.getStatus(), "duration", order.getDuration(), "ownerId", order.getOwnerId()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("order", orderMap);
        response.put("estimatedWaitTime", waitTime);
        response.put("position", position);
        response.put("totalAhead", position);
        response.put("servingNow", servingNowList);

        return response;
    }
}