package com.ticketblitz.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.ticketblitz.backend.repository.UserRepository;
import com.ticketblitz.backend.model.User;
import com.ticketblitz.backend.model.Role;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void setupIndexes() {

        
        // Ensure atomic uniqueness of confirmed bookings per seat
        // This acts as the final safety net preventing double booking
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_seat_booking ON orders (seat_id) WHERE status = 'CONFIRMED'");
        
        // cleanup job filters by both — avoids full table scan
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_seat_event_status ON seat(event_id, status)");
        
        // Performance indexing for frequent lookups
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_seat_event ON seat(event_id)");
        
        // Idempotency safety to prevent duplicate requests
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_order_idempotency ON orders(idempotency_key)");
    }

    // This runs automatically when you start the server
    @Bean
    CommandLineRunner testConnection(StringRedisTemplate redisTemplate) {
        return args -> {
            System.out.println("---------------------------------------");
            System.out.println("⚡ TESTING REDIS CONNECTION...");

            try {
                // 1. Try to write to Redis
                redisTemplate.opsForValue().set("test_key", "TicketBlitz is Live!");

                // 2. Try to read from Redis
                String value = redisTemplate.opsForValue().get("test_key");

                System.out.println("⚡ REDIS RESPONSE: " + value);
            } catch (Exception e) {
                System.err.println("⚠️ Redis connection test failed (non-fatal): " + e.getMessage());
                System.out.println("⚡ App will continue without initial Redis test. Redis will reconnect lazily.");
            }
            System.out.println("---------------------------------------");
        };
    }

    @Bean
    CommandLineRunner seedDemoUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail("recruiter@ticketblitz.com").isEmpty()) {
                System.out.println("⚡ SEEDING DEMO RECRUITER ACCOUNT...");
                User demoUser = User.builder()
                        .name("Jane Recruiter")
                        .email("recruiter@ticketblitz.com")
                        .password(passwordEncoder.encode("TicketBlitz123!"))
                        .role(Role.ADMIN)
                        .build();
                userRepository.save(demoUser);
                System.out.println("✅ Demo account created: recruiter@ticketblitz.com");
            }
        };
    }
}
