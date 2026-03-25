package com.ticketblitz.backend.service;
import com.ticketblitz.backend.dto.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingEventProducer {
    private final ApplicationEventPublisher eventPublisher;
    public void publishConfirmation(BookingConfirmedEvent event) {
        eventPublisher.publishEvent(event);
        System.out.println("? Dispatched async booking notification for Order: " + event.getOrderId());
    }
}
