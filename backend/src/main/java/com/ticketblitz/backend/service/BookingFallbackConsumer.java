package com.ticketblitz.backend.service;

import com.ticketblitz.backend.dto.BookingConfirmedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback event consumer for free-tier deployments where Kafka is unavailable.
 * Uses Spring's in-process ApplicationEvent bus to achieve the same async notification
 * pattern without requiring an external message broker.
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "false")
public class BookingFallbackConsumer {

    private final Set<Long> processedAudits = ConcurrentHashMap.newKeySet();

    @Async
    @EventListener
    public void consume(BookingConfirmedEvent event) {
        if (processedAudits.contains(event.getOrderId())) {
            System.out.println("⚠️ Dropping duplicate delivery for Order: " + event.getOrderId());
            return;
        }

        System.out.println("📧 [ASYNC] Sending Ticket Email for User: " + event.getUserId());
        processedAudits.add(event.getOrderId());
    }
}
