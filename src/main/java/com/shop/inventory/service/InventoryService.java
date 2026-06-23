package com.shop.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.inventory.domain.OutboxEvent;
import com.shop.inventory.domain.ProcessedEvent;
import com.shop.inventory.domain.Reservation;
import com.shop.inventory.redis.StockRedis;
import com.shop.inventory.repo.OutboxRepository;
import com.shop.inventory.repo.ProcessedEventRepository;
import com.shop.inventory.repo.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reservation logic: atomic Redis reserve/release + durable Postgres
 * (reservations, outbox) with at-least-once idempotency via processed_events.
 */
@Service
public class InventoryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StockRedis stock;
    private final ReservationRepository reservations;
    private final OutboxRepository outbox;
    private final ProcessedEventRepository processed;
    private final long ttlSeconds;

    public InventoryService(StockRedis stock,
                            ReservationRepository reservations,
                            OutboxRepository outbox,
                            ProcessedEventRepository processed,
                            @Value("${shop.inventory.reservation-ttl-seconds:600}") long ttlSeconds) {
        this.stock = stock;
        this.reservations = reservations;
        this.outbox = outbox;
        this.processed = processed;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public void handleEvent(String json) {
        JsonNode e = parse(json);
        String type = e.path("type").asText();
        switch (type) {
            case "OrderCreated" -> handleOrderCreated(e);
            case "ReleaseStock" -> handleReleaseStock(e);
            default -> { /* not interested */ }
        }
    }

    private void handleOrderCreated(JsonNode e) {
        String eventId = e.path("eventId").asText();
        if (eventId.isEmpty() || processed.existsById(eventId)) {
            return;
        }
        String orderId = e.path("orderId").asText();
        String productId = e.path("productId").asText();
        long qty = e.path("quantity").asLong();

        long result = stock.reserve(productId, orderId, qty, ttlSeconds);
        if (result == 1 || result == 2) {
            if (!reservations.existsById(orderId)) {
                reservations.save(new Reservation(orderId, productId, qty, "RESERVED"));
            }
            emit("StockReserved", orderId, productId, qty);
        } else {
            emit("StockReservationFailed", orderId, productId, qty);
        }
        processed.save(new ProcessedEvent(eventId));
    }

    private void handleReleaseStock(JsonNode e) {
        String eventId = e.path("eventId").asText();
        if (eventId.isEmpty() || processed.existsById(eventId)) {
            return;
        }
        String orderId = e.path("orderId").asText();
        Reservation res = reservations.findById(orderId).orElse(null);
        String productId = res != null ? res.getProductId() : e.path("productId").asText();
        long qty = res != null ? res.getQuantity() : e.path("quantity").asLong();

        stock.release(productId, orderId);
        if (res != null && "RESERVED".equals(res.getStatus())) {
            res.setStatus("RELEASED");
            reservations.save(res);
        }
        emit("StockReleased", orderId, productId, qty);
        processed.save(new ProcessedEvent(eventId));
    }

    private void emit(String type, String orderId, String productId, long qty) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("type", type);
        body.put("orderId", orderId);
        body.put("productId", productId);
        body.put("quantity", qty);
        outbox.save(OutboxEvent.create(eventId, type, productId, toJson(body)));
    }

    private JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid event JSON", ex);
        }
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
