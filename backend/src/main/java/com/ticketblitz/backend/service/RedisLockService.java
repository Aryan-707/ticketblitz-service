package com.ticketblitz.backend.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    // @CircuitBreaker prevents HikariCP DB pool exhaustion by short-circuiting fast if Redis stalls.
    @CircuitBreaker(name = "redisLock", fallbackMethod = "acquireLockFallback")
    public boolean acquireSeatLock(Long seatId, String userId) {
        String lockKey = "seat:lock:" + seatId;
        // setIfAbsent fundamentally leverages atomic SET NX EX preventing crash-induced orphan locks
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, 10, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean acquireLockFallback(Long seatId, String userId, Throwable t) {
        System.err.println("🚨 Redis degraded/down. CircuitBreaker OPEN. Bypassing lock for seat: " + seatId);
        // Fallback: Return true to push transaction entirely to the PostgreSQL unique constraint safety net
        return true; 
    }

    public void releaseLockSafely(Long seatId) {
        try {
            redisTemplate.delete("seat:lock:" + seatId);
        } catch (Exception e) {
            // Ignore failure on cleanup during degraded mode
        }
    }
}
