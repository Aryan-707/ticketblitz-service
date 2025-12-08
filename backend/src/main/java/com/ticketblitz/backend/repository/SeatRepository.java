package com.ticketblitz.backend.repository;

import com.ticketblitz.backend.model.Seat;
import com.ticketblitz.backend.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventId(Long eventId);
    List<Seat> findByStatusAndReservedAtBefore(SeatStatus status, LocalDateTime time);
}
