package com.myexampleproject.orderservice.controller;

// === IMPORT MỚI CẦN THÊM ===
import com.myexampleproject.orderservice.dto.OrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
// === KẾT THÚC IMPORT MỚI ===

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.myexampleproject.orderservice.dto.OrderRequest;
import com.myexampleproject.orderservice.service.OrderService;

import lombok.RequiredArgsConstructor;

import java.security.Principal;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // --- PHƯƠNG THỨC POST (Bạn đã có) ---
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String placeOrder(@RequestBody OrderRequest orderRequest, Principal principal) {
        log.info("Placing Order (Event-Driven Mode)");
        // LẤY userId TỪ PRINCIPAL
        // principal.getName() sẽ trả về "subject" (ID) của user trong Keycloak
        String userId = principal.getName();
        orderService.placeOrder(orderRequest, userId);
        return "Order Received! Your order is being processed.";
    }

    // ==========================================================
    // === PHƯƠNG THỨC GET MỚI CẦN BỔ SUNG ===
    // ==========================================================
    @GetMapping("/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse getOrderDetails(@PathVariable String orderNumber) {
        log.info("Fetching order details for orderNumber: {}", orderNumber);
        return orderService.getOrderDetails(orderNumber);
    }
}