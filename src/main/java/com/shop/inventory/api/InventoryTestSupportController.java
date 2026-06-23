package com.shop.inventory.api;

import com.shop.inventory.domain.InventoryItem;
import com.shop.inventory.redis.StockRedis;
import com.shop.inventory.repo.InventoryItemRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-support endpoints used by acceptance tests to seed/clean stock.
 * Enabled only in non-prod via {@code shop.test-support.enabled=true}.
 */
@RestController
@RequestMapping("/inventory")
@ConditionalOnProperty(name = "shop.test-support.enabled", havingValue = "true")
public class InventoryTestSupportController {

    private final StockRedis stock;
    private final InventoryItemRepository items;

    public InventoryTestSupportController(StockRedis stock, InventoryItemRepository items) {
        this.stock = stock;
        this.items = items;
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> setStock(@PathVariable String productId, @RequestBody SetStockRequest req) {
        items.save(new InventoryItem(productId, req.stock()));
        stock.setStock(productId, req.stock());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(@PathVariable String productId) {
        stock.deleteStock(productId);
        items.deleteById(productId);
        return ResponseEntity.noContent().build();
    }
}
