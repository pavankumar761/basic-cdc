package com.pavan.order_service.consumers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavan.order_service.dto.OrderEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * @author : Pavan Kumar
 * @created : 10/04/26, Friday
 */

@Service
@Slf4j
public class OrderCdcListener {
    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String ORDER_CACHE_KEY = "Order_";


    public OrderCdcListener(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "dbserver1.orders_db.orders", groupId = "order-cdc-group")
    public void handleOrderChange(@Payload(required = false) OrderEventDTO event, @Header(KafkaHeaders.RECEIVED_KEY) String rawKey) {
        if (event == null) {
            try {
                JsonNode keyNode = objectMapper.readTree(rawKey);
                long deletedId = keyNode.get("id").asLong();

                log.info("Detected Delete/Tombstone for ID: {}. Cleaning cache...", deletedId);
                redisTemplate.delete(ORDER_CACHE_KEY + deletedId);
                log.info("Cache DELETED for Order {}", deletedId);

            } catch (Exception e) {
                log.error("Failed to parse Kafka key for delete event: {}", rawKey);
            }
            return;
        }
        log.info("Received CDC event: {} for Order ID: {}", event.getOperation(), event.getId());

        switch (event.getOperation()) {
            case "c", "u", "r" -> {
                redisTemplate.opsForValue().set(ORDER_CACHE_KEY + event.getId(), event);
                log.info("Cache UPDATED for Order {}", event.getId());
            }
            case "d" -> {
                redisTemplate.opsForValue().getOperations().delete(ORDER_CACHE_KEY + event.getId());
                log.info("Cache DELETED for Order {}", event.getId());
            }
            default -> log.warn("Unknown operation type: {}", event.getOperation());
        }
    }
}
