package com.ticketblitz.backend.service;

import com.ticketblitz.backend.dto.BookingConfirmedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class BookingEventProducer {
    
    @Autowired(required = false)
    private KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final String TOPIC = "booking-confirmed";

    // Producer failure does not rollback DB commitment since booking is already verified natively
    public void publishConfirmation(BookingConfirmedEvent event) {
        if (kafkaTemplate == null) {
            // Fallback for free-tier deployments where Kafka is disabled
            System.out.println("✅ [MOCK KAFKA] Dispatched async booking notification for Order: " + event.getOrderId());
            eventPublisher.publishEvent(event);
            return;
        }

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
