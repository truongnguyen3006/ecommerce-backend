package com.myexampleproject.orderservice.service;

import java.util.List;
import java.util.UUID;

import com.myexampleproject.orderservice.event.OrderProcessingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
@Transactional
@Slf4j
public class OrderService {
	
	private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderProcessingEvent> kafkaTemplate;

	public void placeOrder(OrderRequest orderRequest) {
		Order order = new Order();
		order.setOrderNumber(UUID.randomUUID().toString());
		List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
			.stream()
			.map(this::mapToDto)
			.toList();

		order.setOrderLineItemsList(orderLineItems);

        order.setStatus("PENDING");
        orderRepository.save(order);

        //Tạo sự kiện mới để inventory xử lý
        OrderProcessingEvent event = new OrderProcessingEvent(
                order.getOrderNumber(),
                orderRequest.getOrderLineItemsDtoList()
        );

        kafkaTemplate.send("order-processing-topic", event);
        log.info("Order {} received and sent to processing topic.", order.getOrderNumber());

	}

	private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
		OrderLineItems orderLineItems = new OrderLineItems();
		orderLineItems.setPrice(orderLineItemsDto.getPrice());
		orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
		orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
		return orderLineItems;
	}
}
