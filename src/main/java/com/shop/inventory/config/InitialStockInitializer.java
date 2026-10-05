package com.shop.inventory.config;

import com.shop.inventory.redis.StockRedis;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds missing catalog product counters before application readiness.
 * Existing counters are preserved and Redis failures abort startup.
 */
@Component
public class InitialStockInitializer implements ApplicationRunner {

    private final StockRedis stockRedis;

    public InitialStockInitializer(StockRedis stockRedis) {
        this.stockRedis = stockRedis;
    }

    @Override
    public void run(ApplicationArguments args) {
        stockRedis.initializeStock("1", 100L);
        stockRedis.initializeStock("2", 100L);
        stockRedis.initializeStock("3", 100L);
    }
}
