package com.ticketblitz.backend.config;

import com.ticketblitz.backend.model.Order;
import com.ticketblitz.backend.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    @Autowired
    private OrderRepository orderRepository;

    private final Map<String, String> storedResponses = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        if (idempotencyKey != null) {
            Optional<Order> order = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (order.isPresent()) {
                response.setStatus(200);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(storedResponses.getOrDefault(idempotencyKey, "Successfully secured seat " + order.get().getSeat().getId() + "!"));
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        if (idempotencyKey != null && response.getStatus() == 200) {
            storedResponses.put(idempotencyKey, "Successfully secured seat!");
        }
    }
}
