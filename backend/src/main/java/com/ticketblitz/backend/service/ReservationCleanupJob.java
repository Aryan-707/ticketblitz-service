package com.ticketblitz.backend.service;

import com.ticketblitz.backend.model.BookingStatus;
import com.ticketblitz.backend.model.Order;
import com.ticketblitz.backend.model.Seat;
import com.ticketblitz.backend.model.SeatStatus;
import com.ticketblitz.backend.repository.OrderRepository;
import com.ticketblitz.backend.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationCleanupJob {

    private final SeatRepository seatRepository;
    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;

    // Mod 4: Scheduled job every 2 mins to run abandonment cleanup
    @Scheduled(fixedRate = 120000)
    @Transactional
    public void cleanupExpiredReservations() {
        // cleanup job filters by both — avoids full table scan
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(10);
        List<Seat> expiredSeats = seatRepository.findByStatusAndReservedAtBefore(SeatStatus.RESERVED, expiryTime);

        for (Seat seat : expiredSeats) {
            System.out.println("🧹 Cleaning up abandoned seat lock: " + seat.getId());
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setReservedAt(null);
            seatRepository.save(seat);

            List<Order> relatedOrders = orderRepository.findBySeatIdAndStatus(seat.getId(), BookingStatus.RESERVED);
            for (Order order : relatedOrders) {
                order.setStatus(BookingStatus.FAILED);
                orderRepository.save(order);
            }

            // Explicitly delete explicitly locked Redis key
            redisTemplate.delete("seat:lock:" + seat.getId());
        }
    }
}
