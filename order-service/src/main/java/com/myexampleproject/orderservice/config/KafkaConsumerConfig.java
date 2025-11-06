package com.myexampleproject.orderservice.config;

// Import thêm ObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    // Bean này cần để chuyển đổi Map -> POJO
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // --- Tạo MỘT ConsumerFactory chung ---
    @Bean
    public ConsumerFactory<String, Object> genericConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class); // Vẫn dùng Schema Deserializer
        props.put("schema.registry.url", "http://localhost:8081");
        props.put("json.ignore.unknown", true);

        // QUAN TRỌNG: KHÔNG chỉ định "json.value.type".
        // Điều này sẽ khiến nó deserialize thành Map<String, Object>.

        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-updater-group"); // Vẫn dùng chung group

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // --- Tạo MỘT ContainerFactory chung ---
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(genericConsumerFactory());

        factory.setConcurrency(10);

        // *** BẬT CHẾ ĐỘ BATCH ***
        // Đây là tối ưu CỰC LỚN
        factory.setBatchListener(true);
        return factory;
    }

}