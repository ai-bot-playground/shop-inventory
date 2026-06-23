package com.shop.inventory.api;

import com.shop.inventory.domain.InventoryItem;
import com.shop.inventory.redis.StockRedis;
import com.shop.inventory.repo.InventoryItemRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final StockRedis stock;
    private final InventoryItemRepository items;

    public InventoryController(StockRedis stock, InventoryItemRepository items) {
        this.stock = stock;
        this.items = items;
    }

    @GetMapping("/{productId}")
    public Map<String, Object> available(@PathVariable String productId) {
        Long available = stock.getStock(productId);
        if (available == null) {
            // warm Redis from the source of truth
            available = items.findById(productId).map(InventoryItem::getTotalStock).orElse(0L);
            stock.setStock(productId, available);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("available", available);
        return body;
    }
}
