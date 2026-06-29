package com.shop.inventory.steps;

import com.shop.inventory.repo.InventoryItemRepository;
import com.shop.inventory.repo.OutboxRepository;
import com.shop.inventory.repo.ProcessedEventRepository;
import com.shop.inventory.repo.ReservationRepository;
import io.cucumber.java.Before;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wipes Postgres tables and Redis before every scenario, so each test is isolated.
 * Runs before Background steps.
 */
public class CleanupHooks {

    private final OutboxRepository outbox;
    private final ReservationRepository reservations;
    private final ProcessedEventRepository processed;
    private final InventoryItemRepository items;
    private final StringRedisTemplate redis;

    public CleanupHooks(OutboxRepository outbox,
                        ReservationRepository reservations,
                        ProcessedEventRepository processed,
                        InventoryItemRepository items,
                        StringRedisTemplate redis) {
        this.outbox = outbox;
        this.reservations = reservations;
        this.processed = processed;
        this.items = items;
        this.redis = redis;
    }

    @Before
    public void clean() {
        outbox.deleteAll();
        reservations.deleteAll();
        processed.deleteAll();
        items.deleteAll();
        RedisConnectionFactory factory = redis.getConnectionFactory();
        if (factory != null) {
            factory.getConnection().serverCommands().flushDb();
        }
    }
}
