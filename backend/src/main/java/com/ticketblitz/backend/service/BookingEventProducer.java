package com.ticketblitz.backend.service;

import com.ticketblitz.backend.dto.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingEventProducer {
    
    private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;
    private static final String TOPIC = "booking-confirmed";

    // Producer failure does not rollback DB commitment since booking is already verified natively
    public void publishConfirmation(BookingConfirmedEvent event) {
        // partition by eventId globally forces all seat updates for a specific event to stay ordered
        kafkaTemplate.send(TOPIC, String.valueOf(event.getEventId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    System.err.println("🚨 Failed to dispatch Kafka event for Order: " + event.getOrderId() + " - Best effort fallback.");
                } else {
                    System.out.println("✅ Dispatched async booking notification for Order: " + event.getOrderId());
                }
            });
    }
}
