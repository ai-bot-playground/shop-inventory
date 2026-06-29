package com.shop.inventory.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hot-path stock counter in Redis. Reservation and release run as atomic Lua
 * scripts so thousands of concurrent buyers cannot oversell.
 */
@Component
public class StockRedis {

    // reserve: idempotent per orderId; returns 1 reserved, 2 already-reserved, 0 insufficient
    static final String RESERVE_LUA = """
            if redis.call('EXISTS', KEYS[2]) == 1 then return 2 end
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            local qty = tonumber(ARGV[1])
            if stock >= qty then
              redis.call('DECRBY', KEYS[1], qty)
              redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
              return 1
            else
              return 0
            end
            """;

    // release: idempotent; returns released qty, or 0 if nothing to release
    static final String RELEASE_LUA = """
            if redis.call('EXISTS', KEYS[2]) == 0 then return 0 end
            local qty = tonumber(redis.call('GET', KEYS[2]))
            redis.call('INCRBY', KEYS[1], qty)
            redis.call('DEL', KEYS[2])
            return qty
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> reserveScript = RedisScript.of(RESERVE_LUA, Long.class);
    private final RedisScript<Long> releaseScript = RedisScript.of(RELEASE_LUA, Long.class);

    public StockRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long reserve(String productId, String orderId, long qty, long ttlSeconds) {
        Long r = redis.execute(reserveScript, List.of(stockKey(productId), reservationKey(orderId)),
                Long.toString(qty), Long.toString(ttlSeconds));
        return r == null ? 0L : r;
    }

    public long release(String productId, String orderId) {
        Long r = redis.execute(releaseScript, List.of(stockKey(productId), reservationKey(orderId)));
        return r == null ? 0L : r;
    }

    public void setStock(String productId, long units) {
        redis.opsForValue().set(stockKey(productId), Long.toString(units));
    }

    public Long getStock(String productId) {
        String v = redis.opsForValue().get(stockKey(productId));
        return v == null ? null : Long.parseLong(v);
    }

    public void deleteStock(String productId) {
        redis.delete(stockKey(productId));
    }

    private static String stockKey(String productId) {
        return "stock:" + productId;
    }

    private static String reservationKey(String orderId) {
        return "reservation:" + orderId;
    }
}
