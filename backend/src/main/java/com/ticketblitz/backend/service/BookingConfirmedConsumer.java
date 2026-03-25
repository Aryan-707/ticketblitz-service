package com.ticketblitz.backend.service;
import com.ticketblitz.backend.dto.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class BookingConfirmedConsumer {
    private final Set<Long> processedAudits = ConcurrentHashMap.newKeySet();
    @Async
    @EventListener
    public void consume(BookingConfirmedEvent event) {
        if (processedAudits.contains(event.getOrderId())) return;
        System.out.println("?? [ASYNC] Sending Ticket Email for User: " + event.getUserId());
        processedAudits.add(event.getOrderId());
    }
}
