package com.ticketblitz.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
// Adding partial unique constraint to only allow one CONFIRMED order per seat
@Table(name = "orders", uniqueConstraints = {
    // Note: Standard JPA doesn't support partial unique constraints directly on the table annotation 
    // without executing a custom SQL script, so we will enforce it via SQL or native index later.
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;      // email or unique ID of the buyer

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnoreProperties({"orders", "ticketTiers", "seats"})
    private Event event;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    @JsonIgnoreProperties({"orders", "event"})
    private Seat seat;

    private String tierName;    // e.g., "VIP" or "General Admission"
    private int quantity;       // Number of tickets bought
    private double totalAmount; // The final price paid (Price * Quantity)
    private LocalDateTime orderTime;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;


    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    public Order(String userId, Event event, String tierName, int quantity, double totalAmount) {
        this.userId = userId;
        this.event = event;
        this.tierName = tierName;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.orderTime = LocalDateTime.now();
        this.status = BookingStatus.CONFIRMED; // Default fallback for old code
    }
}
