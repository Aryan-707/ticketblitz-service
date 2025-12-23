package com.ticketblitz.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final StringRedisTemplate redisTemplate;

    // Use ZSET vs LIST because ZADD NX automatically drops duplicate entry requests natively
    public boolean joinWaitlist(Long seatId, String userId) {
        String key = "waitlist:seat:" + seatId;
        long score = System.currentTimeMillis();
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().addIfAbsent(key, userId, score));
    }

    public String getNextUser(Long seatId) {
        String key = "waitlist:seat:" + seatId;
        // ZPOPMIN pulls out the single oldest user cleanly ensuring mutually exclusive distribution across cluster nodes
        ZSetOperations.TypedTuple<String> min = redisTemplate.opsForZSet().popMin(key);
        if (min != null) {
            return min.getValue();
        }
        return null;
    }

    public void removeStaleEntries(Long seatId) {
        String key = "waitlist:seat:" + seatId;
        long thirtyMinsAgo = System.currentTimeMillis() - (30 * 60 * 1000);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, thirtyMinsAgo);
    }
}
