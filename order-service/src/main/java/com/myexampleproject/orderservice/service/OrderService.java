package com.myexampleproject.orderservice.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myexampleproject.orderservice.dto.OrderResponse;
import com.myexampleproject.orderservice.event.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myexampleproject.orderservice.dto.OrderLineItemsDto;
import com.myexampleproject.orderservice.dto.OrderRequest;
import com.myexampleproject.orderservice.model.Order;
import com.myexampleproject.orderservice.model.OrderLineItems;
import com.myexampleproject.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void placeOrder(OrderRequest orderRequest) {
        String orderNumber = UUID.randomUUID().toString();
        log.info("Order {} received. Sending processing events ASYNC...", orderNumber);

        // 1. Gửi TẤT CẢ sự kiện xử lý (trừ kho)
        for (OrderLineItemsDto item : orderRequest.getOrderLineItemsDtoList()) {
            OrderProcessingEvent itemEvent = new OrderProcessingEvent(
                    orderNumber,
                    List.of(item)
            );

            // === SỬA ĐỔI QUAN TRỌNG ===
            // Gửi và KHÔNG CHỜ (fire and forget)
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send("order-processing-topic", item.getSkuCode(), itemEvent);

            // Thêm xử lý lỗi (rất quan trọng)
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Lỗi khi gửi (bất đồng bộ) item {} cho order {}: {}",
                            item.getSkuCode(), orderNumber, ex.getMessage());
                }
            });
        }

        // 2. Gửi sự kiện "OrderPlaced"
        OrderPlacedEvent placedEvent = new OrderPlacedEvent(
                orderNumber,
                orderRequest.getOrderLineItemsDtoList()
        );

        // === SỬA ĐỔI QUAN TRỌNG ===
        CompletableFuture<SendResult<String, Object>> placedFuture =
                kafkaTemplate.send("order-placed-topic", orderNumber, placedEvent);

        placedFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Lỗi khi gửi (bất đồng bộ) placed-event cho order {}: {}",
                        orderNumber, ex.getMessage());
            }
        });

        log.info("All events for Order {} queued. Returning 200 OK.", orderNumber);

        // Hàm này KẾT THÚC NGAY LẬP TỨC
        // HTTP 200 OK được trả về trong khi Kafka producer tự xử lý trong nền.
    }

    @Transactional(readOnly = true) // Giao dịch chỉ đọc, nhanh hơn
    public OrderResponse getOrderDetails(String orderNumber) {
        log.info("Fetching order details for: {}", orderNumber);

        // 1. Tìm Order trong CSDL
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        // 2. Map từ Entity (Order) sang DTO (OrderResponse)
        return mapToOrderResponse(order);
    }

    /**
     * Helper: Chuyển đổi Entity Order -> DTO OrderResponse.
     */
    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderLineItemsList(order.getOrderLineItemsList()
                        .stream()
                        .map(this::mapToOrderLineItemsDto) // Tái sử dụng logic map
                        .toList())
                .build();
    }

    /**
     * Helper: Chuyển đổi Entity OrderLineItems -> DTO OrderLineItemsDto.
     * (Đây là logic ngược lại với hàm mapToDto bạn đã có)
     */
    private OrderLineItemsDto mapToOrderLineItemsDto(OrderLineItems orderLineItems) {
        return OrderLineItemsDto.builder()
                .id(orderLineItems.getId()) // Giả sử DTO của bạn cũng có Id
                .skuCode(orderLineItems.getSkuCode())
                .price(orderLineItems.getPrice())
                .quantity(orderLineItems.getQuantity())
                .build();
    }

    @KafkaListener(
            topics = {
                    "order-placed-topic",
                    "order-failed-topic",
            },
            containerFactory = "kafkaListenerContainerFactory" // <-- Dùng factory chung
    )
    public void handleOrderEvents(List<ConsumerRecord<String, Object>> records) {
        log.info("Received a batch of {} events", records.size());

        // Loop qua danh sách
        for (ConsumerRecord<String, Object> record : records) {
            String topic = record.topic();
            Object payload = record.value();
            log.debug("Processing event from topic [{}], key [{}]", topic, record.key());

            // Logic switch-case của bạn giữ nguyên
            try {
                switch (topic) {
                    case "order-placed-topic":
                        OrderPlacedEvent placedEvent = objectMapper.convertValue(payload, OrderPlacedEvent.class);
                        handleOrderPlacement(placedEvent); // Hàm private này giữ nguyên
                        break;

                    case "order-failed-topic":
                        OrderFailedEvent failedEvent = objectMapper.convertValue(payload, OrderFailedEvent.class);
                        handleOrderFailure(failedEvent); // Hàm private này giữ nguyên
                        break;

                    case "payment-processed-topic":
                        PaymentProcessedEvent processedEvent = objectMapper.convertValue(payload, PaymentProcessedEvent.class);
                        handlePaymentSuccess(processedEvent);
                        break;

                    case "payment-failed-topic":
                        PaymentFailedEvent paymentFailedEvent = objectMapper.convertValue(payload, PaymentFailedEvent.class);
                        handlePaymentFailure(paymentFailedEvent);
                        break;

                    default:
                        log.warn("Received message on unhandled topic: {}", topic);
                }
            } catch (Exception e) {
                log.error("LỖI KHI XỬ LÝ MESSAGE: {}. Sẽ KHÔNG retry.", record.key(), e);
            }
        }
    }

    public <T> T toEvent(Object payload, Class<T> clazz) {
        return objectMapper.convertValue(payload, clazz);
    }


    @KafkaListener(
            topics = "payment-validated-topic",
            groupId = "order-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleValidatedPayment(List<ConsumerRecord<String, Object>> records) {
        for (ConsumerRecord<String, Object> rec : records) {
            try {
                Object payload = rec.value(); // <-- LẤY GIÁ TRỊ
                PaymentProcessedEvent event =
                        objectMapper.convertValue(payload, PaymentProcessedEvent.class);
                handlePaymentSuccess(event);
            } catch (Exception e) {
                log.error("❌ Error converting/processing PaymentValidatedEvent at {}: {}",
                        rec.topic() + "-" + rec.partition() + "@" + rec.offset(), e.getMessage(), e);
            }
        }
    }





    @Transactional
    protected void handleOrderPlacement(OrderPlacedEvent event) {
        log.info("Async Save: Saving Order {} to database...", event.getOrderNumber());

        Order order = new Order();
        order.setOrderNumber(event.getOrderNumber());
        List<OrderLineItems> orderLineItems = event.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);
        order.setStatus("PENDING"); // Trạng thái PENDING ban đầu

        orderRepository.save(order);
        log.info("Async Save: Order {} saved to database.", event.getOrderNumber());
        // Sau khi lưu order vào DB
        OrderStatusEvent statusEvent = new OrderStatusEvent(event.getOrderNumber(), "PENDING");
        kafkaTemplate.send("order-status-topic", event.getOrderNumber(), statusEvent);
    }

    @Transactional
    protected void handleOrderFailure(OrderFailedEvent failedEvent) {
        log.info("Using OrderFailedEvent class: {}", failedEvent.getClass().getName());
        log.warn("INVENTORY FAILED: Received feedback for Order {}. Reason: {}",
                failedEvent.getOrderNumber(), failedEvent.getReason());

        Order order = orderRepository.findByOrderNumber(failedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + failedEvent.getOrderNumber()));
        if (order.getStatus().equals("PENDING")) {
            order.setStatus("FAILED");
            orderRepository.save(order);
            log.warn("Order {} status updated to FAILED due to inventory issue.", order.getOrderNumber());
            kafkaTemplate.send("order-status-topic", order.getOrderNumber(),
                    new OrderStatusEvent(order.getOrderNumber(), order.getStatus()));

        } else {
            log.warn("Received failure event for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    @Transactional
    protected void handlePaymentSuccess(PaymentProcessedEvent paymentProcessedEvent) {
        log.info("SUCCESS: Received PaymentProcessedEvent for Order {}. Payment ID: {}. Updating status...",
                paymentProcessedEvent.getOrderNumber(), paymentProcessedEvent.getPaymentId());

        // Không cần try-catch ở đây nữa vì đã có ở hàm listener chính
        Order order = orderRepository.findByOrderNumber(paymentProcessedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + paymentProcessedEvent.getOrderNumber()));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            log.info("Order {} status updated to COMPLETED.", order.getOrderNumber());
            kafkaTemplate.send("order-status-topic", order.getOrderNumber(),
                    new OrderStatusEvent(order.getOrderNumber(), order.getStatus()));
        } else {
            log.warn("Received payment success for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    @Transactional
    protected void handlePaymentFailure(PaymentFailedEvent paymentFailedEvent) {
        log.warn("FAILED: Received PaymentFailedEvent for Order {}. Reason: {}. Updating status...",
                paymentFailedEvent.getOrderNumber(), paymentFailedEvent.getReason());

        Order order = orderRepository.findByOrderNumber(paymentFailedEvent.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + paymentFailedEvent.getOrderNumber()));

        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("PAYMENT_FAILED");
            orderRepository.save(order);
            log.warn("Order {} status updated to PAYMENT_FAILED.", order.getOrderNumber());
            kafkaTemplate.send("order-status-topic", order.getOrderNumber(),
                    new OrderStatusEvent(order.getOrderNumber(), order.getStatus()));
        } else {
            log.warn("Received payment failure for order {} but status was not PENDING (Status: {}).",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}