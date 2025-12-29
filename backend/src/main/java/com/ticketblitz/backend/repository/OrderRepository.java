package com.ticketblitz.backend.repository;

import com.ticketblitz.backend.model.BookingStatus;
import com.ticketblitz.backend.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o " +
            "JOIN FETCH o.event e " +
            "JOIN FETCH e.venue " +
            "WHERE o.userId = :userId")
    List<Order> findByUserIdWithDetails(@Param("userId") String userId);

    List<Order> findByUserId(String userId);

    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM Order o")
    Long sumTotalTicketsSold();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0.0) FROM Order o")
    Double sumTotalRevenue();


    Optional<Order> findByIdempotencyKey(String idempotencyKey);


    List<Order> findBySeatIdAndStatus(Long seatId, BookingStatus status);
}
