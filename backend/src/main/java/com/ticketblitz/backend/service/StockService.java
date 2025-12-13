package com.ticketblitz.backend.service;

import com.ticketblitz.backend.model.Event;
import com.ticketblitz.backend.model.Order;
import com.ticketblitz.backend.model.TicketTier;
import com.ticketblitz.backend.repository.EventRepository;
import com.ticketblitz.backend.repository.OrderRepository;
import com.ticketblitz.backend.repository.TicketTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;
    private final TicketTierRepository ticketTierRepository;
    private final EventRepository eventRepository;
    private final com.ticketblitz.backend.repository.SeatRepository seatRepository;

    // check if it's null so we don't poison redis cache with "event:null:tier:null"
    public void initializeStock(Long eventId, Long tierId, int amount) {
        if (eventId == null || tierId == null) {
            System.err.println("❌ ABORTING REDIS SYNC: Received Null IDs (Event: " + eventId + ", Tier: " + tierId + ")");
            return; // Exit early to prevent cache poisoning
        }

        String stockKey = String.format("event:%d:tier:%d", eventId, tierId);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(amount));

        broadcastStockUpdate(eventId, tierId, (long) amount);
        System.out.println("🔄 Redis Cache Initialized: Event " + eventId + " Tier " + tierId + " -> " + amount);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean processPurchase(Long eventId, Long tierId, Long seatId, String userId, String idempotencyKey) {
        if (eventId == null || tierId == null || seatId == null || idempotencyKey == null) return false;

        // Mod 6: Idempotency safety. Let the DB constraint serve as the ultimate truth.
        // We first do a SELECT here (and the DB unique constraint on idempotency_key is another safety net).
        if (orderRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            System.out.println("🔄 Duplicate idempotent request ignored for user: " + userId);
            return true;
        }

        String lockKey = "seat:lock:" + seatId;

        // Mod 2 & Mod 8: Redis Distributed Lock with Failure Simulation (Option A - CP over AP)
        Boolean lockAcquired;
        try {
            lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, userId, 10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            // Redis unavailable - CP over AP
            throw new RuntimeException("Redis is unavailable. Taking node down to prevent split brain and overselling.");
        }

        // TODO: might want to add retry logic here later if Redis is just flapping
        if (Boolean.FALSE.equals(lockAcquired)) {
            System.out.println("❌ Lock already acquired for seat: " + seatId);
            return false;
        }

        try {
            com.ticketblitz.backend.model.Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new com.ticketblitz.backend.exception.SeatUnavailableException("Seat not found"));
            
            // DB is the final source of truth for seat availability
            if (seat.getStatus() != com.ticketblitz.backend.model.SeatStatus.AVAILABLE) {
                throw new com.ticketblitz.backend.exception.SeatUnavailableException("Seat already booked in DB");
            }
            
            seat.setStatus(com.ticketblitz.backend.model.SeatStatus.RESERVED);
            seat.setReservedAt(LocalDateTime.now());
            seatRepository.save(seat);

            String stockKey = String.format("event:%d:tier:%d", eventId, tierId);
            Long remainingStock = redisTemplate.opsForValue().decrement(stockKey, 1);

            if (remainingStock != null && remainingStock >= 0) {
                TicketTier tier = ticketTierRepository.findById(tierId)
                        .orElseThrow(() -> new RuntimeException("Tier not found"));
                Event event = eventRepository.findById(eventId)
                        .orElseThrow(() -> new RuntimeException("Event not found"));

                // DB decrement
                tier.setAvailableStock(remainingStock.intValue());
                ticketTierRepository.save(tier);

                // Mod 5: Booking status lifecycle (Starts as RESERVED)
                Order newOrder = Order.builder()
                        .userId(userId)
                        .event(event)
                        .seat(seat)
                        .tierName(tier.getTierName())
                        .quantity(1)
                        .totalAmount(tier.getPrice())
                        .orderTime(LocalDateTime.now())
                        .status(com.ticketblitz.backend.model.BookingStatus.RESERVED)
                        .idempotencyKey(idempotencyKey)
                        .build();

                orderRepository.save(newOrder);

                broadcastStockUpdate(eventId, tierId, remainingStock);
                return true;

            } else {
                // Tier logically sold out despite seat availability (inconsistent state fallback)
                redisTemplate.opsForValue().increment(stockKey, 1);
                throw new com.ticketblitz.backend.exception.SeatUnavailableException("Tier stock exhausted");
            }

        } catch (com.ticketblitz.backend.exception.SeatUnavailableException e) {
            redisTemplate.delete(lockKey); // release immediately if seat taken
            return false;
        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            System.err.println("🚨 TRANSACTION ABORTED: " + e.getMessage());
            throw new RuntimeException("Database rejected the save. Redis lock released.", e);
        }
    }

    // sends websocket payload downstream
    private void broadcastStockUpdate(Long eventId, Long tierId, Long remaining) {
        Map<String, Object> update = new HashMap<>();
        update.put("tierId", tierId);
        update.put("remaining", remaining);

        messagingTemplate.convertAndSend(
                "/topic/stock/" + eventId,
                (Object) update,
                (Map<String, Object>) null
        );
    }

    public int getStockCount(Long eventId, Long tierId) {
        if (eventId == null || tierId == null) return 0;
        String stockKey = String.format("event:%d:tier:%d", eventId, tierId);
        String val = redisTemplate.opsForValue().get(stockKey);
        return val != null ? Integer.parseInt(val) : 0;
    }
}
