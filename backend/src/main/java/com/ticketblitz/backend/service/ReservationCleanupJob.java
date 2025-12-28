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
    private final WaitlistService waitlistService;

    // Mod 4: Scheduled job every 2 mins to run abandonment cleanup
    @Scheduled(fixedRate = 120000)
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "reservationSweeper", lockAtMostFor = "1m", lockAtLeastFor = "30s")
    @Transactional
    public void cleanupStrandedReservations() {
        // cleanup job filters by both — avoids full table scan
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(10);
        List<Seat> expiredSeats = seatRepository.findByStatusAndReservedAtBefore(SeatStatus.HELD, expiryTime);

        for (Seat seat : expiredSeats) {
            System.out.println("🧹 Cleaning up abandoned seat lock: " + seat.getId());
            // Transition: Sweeper runs -> AVAILABLE
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setReservedAt(null);
            seatRepository.save(seat);

            // Waitlist Notification: Check if someone is waiting fairly on this seat
            waitlistService.removeStaleEntries(seat.getId());
            String nextUser = waitlistService.getNextUser(seat.getId());
            if (nextUser != null) {
                System.out.println("📨 [MOCK NOTIFY] Waitlist: Seat " + seat.getId() + " is now free. Notifying user " + nextUser);
            }

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
