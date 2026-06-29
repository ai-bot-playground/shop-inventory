package com.shop.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryApplication {

    private static final Logger log = LoggerFactory.getLogger(InventoryApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
        log.info("shop-inventory service started and ready to serve requests");
    }
}
