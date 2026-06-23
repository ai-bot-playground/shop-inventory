package com.shop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "total_stock", nullable = false)
    private long totalStock;

    protected InventoryItem() {
    }

    public InventoryItem(String productId, long totalStock) {
        this.productId = productId;
        this.totalStock = totalStock;
    }

    public String getProductId() {
        return productId;
    }

    public long getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(long totalStock) {
        this.totalStock = totalStock;
    }
}
