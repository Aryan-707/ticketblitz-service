package com.ticketblitz.backend.service;

import com.ticketblitz.backend.dto.BookingConfirmedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BookingConfirmedConsumer {

    @Autowired(required = false)
    private KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;
    // Mocking an audit table for idempotent Kafka ingestion
    private final Set<Long> processedAudits = ConcurrentHashMap.newKeySet();
    private static final String DLQ_TOPIC = "booking-confirmed-dlq";

    @KafkaListener(topics = "booking-confirmed", groupId = "notification-service-group", autoStartup = "${kafka.enabled:true}")
    public void consume(BookingConfirmedEvent event, Acknowledgment ack) {
        try {
            processEvent(event);
            // Manual ack explicitly guarantees At-Least-Once reliability
            ack.acknowledge();
        } catch (Exception e) {
            // For production this is managed by Spring @Retryable logic hitting a DLQ
            System.err.println("🚨 Consumer Failed. Routing to DLQ -> " + DLQ_TOPIC);
            if (kafkaTemplate != null) {
                kafkaTemplate.send(DLQ_TOPIC, String.valueOf(event.getEventId()), event);
            }
            ack.acknowledge(); // Drop message from primary topic natively
        }
    }

    // Fallback listener for free-tier deployments without Kafka
    @Async
    @EventListener
    public void consumeFallback(BookingConfirmedEvent event) {
        if (kafkaTemplate == null) {
            processEvent(event);
        }
    }

    private void processEvent(BookingConfirmedEvent event) {
        // Idempotency check: Skip if already written to audit
        if (processedAudits.contains(event.getOrderId())) {
            System.out.println("⚠️ Dropping duplicate delivery for Order: " + event.getOrderId());
            return;
        }

        // Mock: Execute slow email + audit append operations without blocking the frontend HTTP pool
        System.out.println("📧 [ASYNC] Sending Ticket Email for User: " + event.getUserId());
        processedAudits.add(event.getOrderId());
    }
}
