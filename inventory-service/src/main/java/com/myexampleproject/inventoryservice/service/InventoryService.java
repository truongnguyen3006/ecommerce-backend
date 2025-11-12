package com.myexampleproject.inventoryservice.service;

import com.myexampleproject.inventoryservice.event.*;
import com.myexampleproject.inventoryservice.dto.OrderLineItemsDto;
import lombok.extern.slf4j.Slf4j;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.util.Map;
import java.util.Objects;

@Configuration
@Slf4j
@EnableKafkaStreams
public class InventoryService {

    public static final String INVENTORY_STORE_NAME = "inventory-store";

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private <T> Serde<T> createJsonSchemaSerde(Class<T> dtoClass) {
        final Serde<T> serde = new KafkaJsonSchemaSerde<>(dtoClass);
        serde.configure(Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl), false);
        return serde;
    }

    @Bean
    @DependsOn("kafkaAdmin")
    public KStream<String, OrderValidatedEvent> inventoryTopology(StreamsBuilder builder) {

        Serde<ProductCreatedEvent> productCreatedSerde = createJsonSchemaSerde(ProductCreatedEvent.class);
        Serde<OrderProcessingEvent> orderProcessingSerde = createJsonSchemaSerde(OrderProcessingEvent.class);
        Serde<OrderValidatedEvent> orderValidatedSerde = createJsonSchemaSerde(OrderValidatedEvent.class);
        Serde<OrderFailedEvent> orderFailedSerde = createJsonSchemaSerde(OrderFailedEvent.class);
        Serde<InventoryAdjustmentEvent> adjustmentSerde = createJsonSchemaSerde(InventoryAdjustmentEvent.class);

        // ===== 1️⃣ KHAI BÁO STATE STORE =====
        StoreBuilder<KeyValueStore<String, Integer>> inventoryStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(INVENTORY_STORE_NAME),
                Serdes.String(),
                Serdes.Integer()
        );
        builder.addStateStore(inventoryStoreBuilder);

        // ===== 2️⃣ KHỞI TẠO SẢN PHẨM MỚI =====
        builder.stream("product-created-topic", Consumed.with(Serdes.String(), productCreatedSerde))
                .process(() -> new Processor<String, ProductCreatedEvent, Void, Void>() {

                    private KeyValueStore<String, Integer> store;

                    @Override
                    public void init(ProcessorContext<Void, Void> context) {
                        this.store = context.getStateStore(INVENTORY_STORE_NAME);
                    }

                    @Override
                    public void process(Record<String, ProductCreatedEvent> record) {
                        String sku = record.key();
                        if (sku == null || record.value() == null) return;

                        int initialQty = record.value().getInitialQuantity();
                        Integer old = store.get(sku);

                        if (old == null) {
                            store.put(sku, initialQty);
                            log.info("[INIT] SKU {} initialized with {} units", sku, initialQty);
                        } else {
                            log.info("[INIT] SKU {} already exists with {}, ignoring init {}", sku, old, initialQty);
                        }
                    }

                    @Override
                    public void close() {}
                }, INVENTORY_STORE_NAME);

        // ===== 3️⃣ XỬ LÝ ĐIỀU CHỈNH THỦ CÔNG (ADJUSTMENT) =====
        builder.stream("inventory-adjustment-topic", Consumed.with(Serdes.String(), adjustmentSerde))
                .process(() -> new Processor<String, InventoryAdjustmentEvent, Void, Void>() {

                    private KeyValueStore<String, Integer> store;

                    @Override
                    public void init(ProcessorContext<Void, Void> context) {
                        this.store = context.getStateStore(INVENTORY_STORE_NAME);
                    }

                    @Override
                    public void process(Record<String, InventoryAdjustmentEvent> record) {
                        String sku = record.key();
                        InventoryAdjustmentEvent event = record.value();

                        if (sku == null || event == null) return;

                        Integer current = store.get(sku);
                        if (current == null) current = 0;

                        int delta = event.getAdjustmentQuantity();

                        // Nếu delta = 0, không làm gì cả
                        if (delta == 0) {
                            log.warn("[ADJUST] SKU {} received adjustment of 0, ignoring.", sku);
                            return;
                        }

                        long newStockLong = (long) current + (long) delta;
                        int newStock;
                        if (newStockLong < 0) {
                            // Giữ hành vi hiện tại: không để âm, đặt về 0 và log rõ
                            log.warn("[ADJUST] SKU {} attempted to reduce below zero (current={}, adj={}), forcing newStock=0",
                                    sku, current, delta);
                            newStock = 0;
                        } else if (newStockLong > Integer.MAX_VALUE) {
                            // Phòng overflow hiếm, clamp
                            newStock = Integer.MAX_VALUE;
                            log.warn("[ADJUST] SKU {} newStock overflow, clamped to Integer.MAX_VALUE", sku);
                        } else {
                            newStock = (int) newStockLong;
                        }

                        // CHỈ CẬP NHẬT KHI GIÁ TRỊ THAY ĐỔI
                        if (Objects.equals(current, newStock)) {
                            log.debug("[ADJUST] SKU {} stock unchanged (current={}, new={}), skipping put.", sku, current, newStock);
                            return;
                        }

                        store.put(sku, newStock);

                        log.info("[ADJUST] SKU {} adjusted by {} → old: {}, new: {}, reason: {}",
                                sku, delta, current, newStock, event.getReason());
                    }

                    @Override
                    public void close() {}
                }, INVENTORY_STORE_NAME);


        // ===== 4️⃣ XỬ LÝ ĐƠN HÀNG (ORDER PROCESSING) =====
        KStream<String, OrderProcessingEvent> orderStream = builder.stream(
                "order-processing-topic", Consumed.with(Serdes.String(), orderProcessingSerde));

        KStream<String, Object> resultStream = orderStream.process(
                () -> new Processor<String, OrderProcessingEvent, String, Object>() {
                    private KeyValueStore<String, Integer> store;
                    private ProcessorContext<String, Object> context;

                    @Override
                    public void init(ProcessorContext<String, Object> context) {
                        this.context = context;
                        this.store = context.getStateStore(INVENTORY_STORE_NAME);
                    }

                    @Override
                    public void process(Record<String, OrderProcessingEvent> record) {
                        OrderProcessingEvent event = record.value();
                        if (event == null) return;

                        String orderNumber = event.getOrderNumber();

                        if (event.getOrderLineItemsDtoList() == null || event.getOrderLineItemsDtoList().isEmpty()) {
                            log.warn("[ORDER] Received empty/invalid order {}. Ignoring.", orderNumber);
                            return;
                        }

                        // ===== PHASE 1: KIỂM TRA TỒN KHO (ALL OR NOTHING) =====
                        boolean allItemsSufficient = true;
                        String failureReason = null;

                        for (OrderLineItemsDto item : event.getOrderLineItemsDtoList()) {
                            String sku = item.getSkuCode();
                            int requested = item.getQuantity();
                            if (requested <= 0) continue; // Bỏ qua nếu số lượng không hợp lệ

                            Integer stock = store.get(sku);
                            if (stock == null) stock = 0;

                            if (stock < requested) {
                                log.warn("[ORDER] CHECK FAILED for Order {}. SKU {} insufficient stock: have {}, need {}.",
                                        orderNumber, sku, stock, requested);
                                allItemsSufficient = false;
                                failureReason = "Not enough stock for " + sku;
                                break; // Dừng ngay khi phát hiện 1 sản phẩm không đủ
                            }
                        }

                        // ===== PHASE 2: COMMIT HOẶC ROLLBACK =====
                        Object result;
                        if (allItemsSufficient) {
                            // --- COMMIT: Tất cả đều đủ, tiến hành trừ kho ---
                            log.info("[ORDER] CHECK OK for Order {}. Committing stock adjustments.", orderNumber);

                            for (OrderLineItemsDto item : event.getOrderLineItemsDtoList()) {
                                String sku = item.getSkuCode();
                                int requested = item.getQuantity();
                                if (requested <= 0) continue;

                                Integer stock = store.get(sku);
                                if (stock == null) stock = 0;

                                long newStockLong = (long) stock - (long) requested;
                                int newStock = newStockLong < 0 ? 0 : (int) newStockLong; // safety clamp
                                store.put(sku, newStock);
                                log.info("[ORDER] COMMIT: Order {}. SKU {} ordered {} → {} left.",
                                        orderNumber, sku, requested, newStock);
                            }
                            result = new OrderValidatedEvent(orderNumber);

                        } else {
                            // --- ROLLBACK: Không đủ hàng, gửi sự kiện Fail ---
                            log.warn("[ORDER] FINAL FAIL for Order {}. Reason: {}", orderNumber, failureReason);
                            result = new OrderFailedEvent(orderNumber, failureReason);
                        }

                        // Gửi kết quả (Thành công hoặc Thất bại)
                        context.forward(record.withValue(result));
                    }

                    @Override
                    public void close() {}
                }, INVENTORY_STORE_NAME);

        // ===== 5️⃣ CHIA NHÁNH KẾT QUẢ =====
        Map<String, KStream<String, Object>> branches = resultStream.split(Named.as("validation-"))
                .branch((k, v) -> v instanceof OrderValidatedEvent, Branched.as("success"))
                .branch((k, v) -> v instanceof OrderFailedEvent, Branched.as("fail"))
                .noDefaultBranch();

        branches.get("validation-success")
                .mapValues(v -> (OrderValidatedEvent) v)
                .to("order-validated-topic", Produced.with(Serdes.String(), orderValidatedSerde));

        branches.get("validation-fail")
                .mapValues(v -> (OrderFailedEvent) v)
                .to("order-failed-topic", Produced.with(Serdes.String(), orderFailedSerde));

        // Note: giữ nguyên return null như cũ để không thay đổi behavior bean (nếu project đang dùng)
        return null;
    }

    @Autowired
    private StreamsBuilderFactoryBean factoryBean;

    /**
     * ✅ Trả về instance KafkaStreams hiện tại để controller có thể truy vấn state store.
     */
    public KafkaStreams getKafkaStreams() {
        if (factoryBean == null) {
            return null;
        }
        try {
            return factoryBean.getKafkaStreams();
        } catch (Exception e) {
            log.warn("Kafka Streams chưa sẵn sàng: {}", e.getMessage());
            return null;
        }
    }
}
