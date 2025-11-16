package com.myexampleproject.cartservice.service;

import com.myexampleproject.common.event.*;
import com.myexampleproject.cartservice.model.CartEntity;
import com.myexampleproject.cartservice.model.CartItemEntity;
import com.myexampleproject.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- Giữ import này cho listener
import org.springframework.kafka.annotation.KafkaListener; // <-- Thêm import này

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // SỬA: Đổi kiểu của KafkaTemplate để khớp với listener
    private final KafkaTemplate<String, CartCheckoutEvent> kafkaTemplate;

    private static final String REDIS_CART_PREFIX = "cart:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final String CHECKOUT_TOPIC = "cart-checkout-topic";
    private static final String CART_CLEANER_GROUP_ID = "cart-cleaner-group";

    public CompletableFuture<Void> checkoutAsync(String userId) {
        return CompletableFuture.runAsync(() -> {
            CartEntity cart = this.viewCart(userId);

            if (cart == null || cart.getItems().isEmpty()) {
                // Ném lỗi để .exceptionally() trong Controller bắt được
                throw new IllegalStateException("Cart empty");
            }

            List<CartLineItem> items = cart.getItems().stream()
                    .map(i -> new CartLineItem(i.getSkuCode(), i.getQuantity(), i.getPrice()))
                    .collect(Collectors.toList());

            CartCheckoutEvent event = new CartCheckoutEvent(userId, items);

            // Gọi hàm gửi Kafka (đã được đánh dấu @Async)
            sendKafkaEvent(event);

            // Hàm này trả về ngay, không chờ Kafka
        });
    }

    @Async // <-- QUAN TRỌNG
    public void sendKafkaEvent(CartCheckoutEvent event) {
        String userId = event.getUserId();
        try {
            log.info("ASYNC SEND: Gửi checkout event cho user {}", userId);

            // Lệnh này BÂY GIỜ sẽ block một luồng "Async"
            // mà không ảnh hưởng đến luồng HTTP
            kafkaTemplate.send(CHECKOUT_TOPIC, userId, event).whenComplete((md, ex) -> {
                if (ex != null) {
                    log.error("ASYNC SEND FAILED for user {}: {}", userId, ex.getMessage());
                } else {
                    log.info("ASYNC SEND SUCCESS for user {}", userId);
                }
            });

        } catch (Exception e) {
            log.error("Lỗi khi gửi Kafka bất đồng bộ: {}", e.getMessage(), e);
        }
    }

    /**
     * Hàm helper: Đọc từ Cache (Redis) trước, nếu không có thì đọc từ DB.
     * (Hàm này giữ nguyên logic, nó đã tối ưu cho việc đọc)
     */
    public CartEntity getCartFromCacheOrDb(String userId) {
        String key = REDIS_CART_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof CartEntity) {
            // log.debug("CACHE HIT for user {}", userId);
            return (CartEntity) cached;
        }

        // log.debug("CACHE MISS for user {}", userId);
        Optional<CartEntity> db = cartRepository.findById(userId);
        if (db.isPresent()) {
            // log.debug("DB HIT for user {}", userId);
            CartEntity cartFromDb = db.get();
            redisTemplate.opsForValue().set(key, cartFromDb, REDIS_TTL); // Warm up cache
            return cartFromDb;
        }

        // log.debug("NEW CART for user {}", userId);
        // Trả về Cart mới (rỗng) nếu không có ở đâu cả
        return CartEntity.builder().userId(userId).items(new ArrayList<>()).build();
    }

//    * HÀM MỚI: Chỉ đọc từ Redis.
//            * Nhanh, an toàn, không bao giờ tấn công CSDL.
//     */
    private CartEntity getCartFromRedis(String userId) {
        String key = REDIS_CART_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof CartEntity) {
            return (CartEntity) cached;
        }
        return null; // Không có trong cache
    }

    // ==========================================================
    // CẢI THIỆN HIỆU NĂNG: Sửa hàm addItem (Redis-only)
    // ==========================================================

    // 1. XÓA @Transactional
    public CartEntity addItem(String userId, CartLineItem line) {
        // 1. Chỉ đọc từ REDIS (Không bao giờ đọc từ DB)
        CartEntity cart = getCartFromRedis(userId);
        // 2. Nếu không có, tạo mới
        if (cart == null) {
            cart = CartEntity.builder().userId(userId).items(new ArrayList<>()).build();
        }

        // (Logic nghiệp vụ giữ nguyên)
        Optional<CartItemEntity> exists = cart.getItems().stream()
                .filter(i -> i.getSkuCode().equals(line.getSkuCode()))
                .findFirst();

        if (exists.isPresent()) {
            // ... (cập nhật quantity/price)
            CartItemEntity e = exists.get();
            e.setQuantity(e.getQuantity() + line.getQuantity());
            e.setPrice(line.getPrice());
        } else {
            // ... (thêm item mới)
            CartItemEntity item = CartItemEntity.builder()
                    .skuCode(line.getSkuCode())
                    .quantity(line.getQuantity())
                    .price(line.getPrice())
                    .build();
            cart.getItems().add(item);
        }

        // 3. XÓA BỎ VIỆC GHI VÀO CSDL (MySQL)
        // CartEntity saved = cartRepository.save(cart); // <-- XÓA DÒNG NÀY

        // 4. GHI TRỰC TIẾP VÀO REDIS (rất nhanh)
        redisTemplate.opsForValue().set(REDIS_CART_PREFIX + userId, cart, REDIS_TTL);
        return cart;
    }

    // ==========================================================
    // CẢI THIỆN HIỆU NĂNG: Sửa hàm removeItem (Redis-only)
    // ==========================================================

    // 1. XÓA @Transactional
    public CartEntity removeItem(String userId, String sku) {
        // 1. Chỉ đọc từ REDIS
        CartEntity cart = getCartFromRedis(userId);
        if (cart == null) return null; // Không có gì để xóa

        // 2. Xử lý logic xóa
        boolean removed = cart.getItems().removeIf(i -> i.getSkuCode().equals(sku));

        if (removed) {
            // 4. XÓA BỎ VIỆC GHI VÀO CSDL (MySQL)
            // CartEntity saved = cartRepository.save(cart); // <-- XÓA DÒNG NÀY

            // 5. GHI TRỰC TIẾP VÀO REDIS
            redisTemplate.opsForValue().set(REDIS_CART_PREFIX + userId, cart, REDIS_TTL);
        }
        return cart;
    }

    /**
     * Hàm xem giỏ hàng (giữ nguyên)
     */
    public CartEntity viewCart(String userId) {
        return getCartFromCacheOrDb(userId);
    }

    // ==========================================================
    // HÀM CHECKOUT VÀ LISTENER (Giữ nguyên như đã sửa)
    // ==========================================================

    /**
     * Hàm checkout: Chỉ đọc (từ cache) và gửi Kafka.
     * (Hàm này giữ nguyên như chúng ta đã sửa trước đó)
     */
    public void checkout(String userId) {
        CartEntity cart = this.viewCart(userId);

        if (cart == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart empty");
        }

        List<CartLineItem> items = cart.getItems().stream()
                .map(i -> new CartLineItem(i.getSkuCode(), i.getQuantity(), i.getPrice()))
                .collect(Collectors.toList());

        CartCheckoutEvent event = new CartCheckoutEvent(userId, items);

        kafkaTemplate.send(CHECKOUT_TOPIC, userId, event).whenComplete((md, ex) -> {
            if (ex != null) {
                log.error("Failed to send checkout event for user {}: {}", userId, ex.getMessage());
            } else {
                log.info("Checkout event sent for user {} partition={}", userId, md.getRecordMetadata().partition());
            }
        });
    }

    /**
     * Kafka Listener: Dọn dẹp CSDL và Redis bất đồng bộ.
     * (Hàm này giữ nguyên như chúng ta đã sửa trước đó)
     */
    @KafkaListener(topics = CHECKOUT_TOPIC, groupId = CART_CLEANER_GROUP_ID)
    @Transactional
    public void handleCheckoutCleanup(CartCheckoutEvent event) {
        String userId = event.getUserId();
        log.info("CLEANUP: Bắt đầu dọn dẹp giỏ hàng cho user {}", userId);

        try {
            cartRepository.deleteById(userId);
            redisTemplate.delete(REDIS_CART_PREFIX + userId);
            log.info("CLEANUP: Đã dọn dẹp giỏ hàng (DB & Redis) cho user {}", userId);
        } catch (Exception e) {
            log.error("CLEANUP FAILED: Lỗi khi dọn dẹp giỏ hàng cho user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}