package com.myexampleproject.orderservice.controller;

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

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String placeOrder(@RequestBody OrderRequest orderRequest) {
        log.info("Placing Order (Event-Driven Mode)");
        orderService.placeOrder(orderRequest);
        return "Order Received! Your order is being processed.";
    }
}
