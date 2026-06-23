package com.shop.inventory.kafka;

import com.shop.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order-events; InventoryService filters OrderCreated / ReleaseStock.
 */
@Component
public class OrderEventListener {

    private final InventoryService service;

    public OrderEventListener(InventoryService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${shop.inventory.order-events-topic:order-events}")
    public void onMessage(String value) {
        service.handleEvent(value);
    }
}
