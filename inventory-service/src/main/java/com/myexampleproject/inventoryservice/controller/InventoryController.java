package com.myexampleproject.inventoryservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myexampleproject.inventoryservice.event.InventoryAdjustmentEvent;
import com.myexampleproject.inventoryservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final StreamsBuilderFactoryBean factoryBean;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.kafka.streams.properties.application.server.id:localhost:8082}")
    private String applicationServerConfig;

    public InventoryController(StreamsBuilderFactoryBean factoryBean,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               InventoryService inventoryService) {
        this.factoryBean = factoryBean;
        this.kafkaTemplate = kafkaTemplate;
        this.inventoryService = inventoryService;
    }

    /**
     * Nhận raw JSON để chắc chắn lấy đúng adjustmentQuantity (giữ dấu).
     * Ví dụ body:
     * { "skuCode": "SKU123", "adjustmentQuantity": -50, "reason": "test" }
     */
    @PostMapping("/adjust")
    public ResponseEntity<?> adjustInventory(@RequestBody String rawBody) {
        log.info("RAW REQUEST BODY: {}", rawBody);

        InventoryAdjustmentEvent adjustmentEvent;
        Integer parsedQty = null;

        try {
            JsonNode root = objectMapper.readTree(rawBody);

            // skuCode bắt buộc
            JsonNode skuNode = root.get("skuCode");
            if (skuNode == null || skuNode.isNull() || skuNode.asText().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "skuCode is required"));
            }
            String sku = skuNode.asText();

            // parse adjustmentQuantity explicitly (giữ dấu)
            JsonNode qtyNode = root.get("adjustmentQuantity");
            if (qtyNode == null || qtyNode.isNull()) {
                return ResponseEntity.badRequest().body(Map.of("error", "adjustmentQuantity is required"));
            }
            if (!qtyNode.isNumber()) {
                // cho phép chuỗi số nhưng parse thử
                try {
                    parsedQty = Integer.parseInt(qtyNode.asText());
                } catch (NumberFormatException ex) {
                    return ResponseEntity.badRequest().body(Map.of("error", "adjustmentQuantity must be a number"));
                }
            } else {
                parsedQty = qtyNode.intValue();
            }

            String reason = root.has("reason") && !root.get("reason").isNull() ? root.get("reason").asText() : null;

            adjustmentEvent = new InventoryAdjustmentEvent(sku, parsedQty, reason);
        } catch (Exception e) {
            log.error("Failed to parse request body: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON body"));
        }

        // kiểm tra giá trị
        int delta = adjustmentEvent.getAdjustmentQuantity();
        if (delta == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Adjustment quantity cannot be zero"));
        }

        log.info("PARSED ADJUSTMENT EVENT: sku={}, parsedQty={}, event.toString={}",
                adjustmentEvent.getSkuCode(), delta, adjustmentEvent);

        // Lấy KafkaStreams
        KafkaStreams streams = inventoryService.getKafkaStreams();
        if (streams == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Kafka Streams not ready"));
        }

        // Đọc currentQty (retry)
        Integer currentQty = null;
        for (int i = 0; i < 10; i++) {
            try {
                ReadOnlyKeyValueStore<String, Integer> store = streams.store(
                        StoreQueryParameters.fromNameAndType(
                                InventoryService.INVENTORY_STORE_NAME,
                                QueryableStoreTypes.keyValueStore()
                        )
                );
                Integer v = store.get(adjustmentEvent.getSkuCode());
                currentQty = (v == null) ? 0 : v;
                break;
            } catch (InvalidStateStoreException ise) {
                log.warn("State store chưa sẵn sàng khi đọc hiện tại (lần {}): {}", i + 1, ise.getMessage());
            } catch (Exception ex) {
                log.error("Lỗi khi đọc state store hiện tại (lần {}): {}", i + 1, ex.getMessage());
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        }

        if (currentQty == null) {
            log.warn("Không thể đọc tồn kho hiện tại cho {}. Từ chối gửi event.", adjustmentEvent.getSkuCode());
            return ResponseEntity.status(503).body(Map.of("error", "Cannot read current inventory; try again later"));
        }

        long newQtyLong = (long) currentQty + (long) delta;
        if (newQtyLong < 0) {
            log.warn("INVALID ADJUSTMENT: SKU {} current={} attempted adj={} → would be negative, rejecting.",
                    adjustmentEvent.getSkuCode(), currentQty, delta);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Adjustment would make inventory negative",
                    "skuCode", adjustmentEvent.getSkuCode(),
                    "currentQuantity", currentQty,
                    "attemptedAdjustment", delta
            ));
        }

        // Log trước khi gửi để kiểm tra chính xác delta đang gửi đi
        log.info("SENDING ADJUSTMENT EVENT TO KAFKA: sku={}, delta={}, currentQty={}",
                adjustmentEvent.getSkuCode(), delta, currentQty);

        kafkaTemplate.send("inventory-adjustment-topic", adjustmentEvent.getSkuCode(), adjustmentEvent);

        // Chờ cập nhật (tuần tự như trước)
        Integer updatedQty = null;
        for (int i = 0; i < 10; i++) {
            try {
                ReadOnlyKeyValueStore<String, Integer> store = streams.store(
                        StoreQueryParameters.fromNameAndType(
                                InventoryService.INVENTORY_STORE_NAME,
                                QueryableStoreTypes.keyValueStore()
                        )
                );
                updatedQty = store.get(adjustmentEvent.getSkuCode());
                if (updatedQty != null) break;
            } catch (InvalidStateStoreException e) {
                log.warn("State store chưa sẵn sàng khi chờ cập nhật (lần {}): {}", i + 1, e.getMessage());
            } catch (Exception e) {
                log.error("Lỗi khi đọc state store khi chờ cập nhật (lần {}): {}", i + 1, e.getMessage());
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        }

        if (updatedQty == null) {
            log.warn("Event đã gửi nhưng Kafka Streams chưa cập nhật kịp cho {}", adjustmentEvent.getSkuCode());
            return ResponseEntity.accepted().body(Map.of(
                    "status", "queued",
                    "skuCode", adjustmentEvent.getSkuCode(),
                    "message", "Kafka Streams updating in background"
            ));
        }

        log.info("✅ Inventory adjusted successfully for {} → new quantity: {}", adjustmentEvent.getSkuCode(), updatedQty);
        return ResponseEntity.ok(Map.of(
                "status", "Inventory adjusted",
                "skuCode", adjustmentEvent.getSkuCode(),
                "newQuantity", updatedQty
        ));
    }

    /**
     * Xem tồn kho hiện tại (đọc trực tiếp từ Kafka state store)
     */
    @GetMapping("/{sku}")
    public ResponseEntity<?> getInventory(@PathVariable String sku) {
        KafkaStreams streams = inventoryService.getKafkaStreams();
        if (streams == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Kafka Streams not ready"));
        }

        try {
            ReadOnlyKeyValueStore<String, Integer> store = streams.store(
                    StoreQueryParameters.fromNameAndType(
                            InventoryService.INVENTORY_STORE_NAME,
                            QueryableStoreTypes.keyValueStore()
                    )
            );

            Integer qty = store.get(sku);
            if (qty == null) qty = 0;

            return ResponseEntity.ok(Map.of("skuCode", sku, "quantity", qty));

        } catch (InvalidStateStoreException e) {
            return ResponseEntity.status(503).body(Map.of("error", "Kafka Streams state store not ready"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }
}
