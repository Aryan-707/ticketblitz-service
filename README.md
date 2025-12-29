# TicketBlitz

A high-performance backend engine for scaling instantaneous ticketing events and flash sales, completely eliminating double-booking under extreme concurrency. 

TicketBlitz handles thousands of users attempting to purchase the exact same seat concurrently, gracefully recovering from infrastructure degradation to guarantee transactional consistency using database constraints, distributed caching structures, and background synchronization.

## System Capabilities 

- **Atomic Lock Management**: Per-seat distributed locks prevent simultaneous processing of massive checkout payloads.
- **Fail-Safe Integrity**: A layered concurrency model uses caching (Redis) for speed and database constraints (Postgres) as the absolute source of truth.
- **Async Waitlist**: As abandoned checkout locks expire, an automated TTL polling mechanism instantly releases seats to waiting patrons.
- **Transactional Outbox**: Booking confirmation notifications are safely written to outbox tables and asynchronously distributed via Kafka to prevent timeouts on the user's checkout thread.

## Tech Stack
- **Backend**: Spring Boot 3, Java 17, Spring Data JPA
- **Database**: PostgreSQL 15 
- **Caching**: Redis 7
- **Messaging**: Apache Kafka
- **Resiliency**: Resilience4j (Circuit Breakers)

## Local Development

Ensure your environment defines the proper credentials to boot infrastructure:

### 1. Set Environment Variables
```bash
export DB_URL="jdbc:postgresql://localhost:5432/ticketing_db"
export DB_USERNAME="postgres"
export DB_PASSWORD="password"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export JWT_SECRET="YOUR_256_BIT_SECRET_FOR_LOCAL_DEV"
```

### 2. Boot Infrastructure
To start Redis, Postgres, and Kafka, use Docker Compose:
```bash
docker-compose up -d
```

### 3. Run Backend Server
Inside the `backend` directory, run:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4. Run Frontend Client
Inside the `frontend` directory, run:
```bash
npm install
npm run dev
```

## Production Readiness
This project has been load-tested simulating 500 concurrent threads targeting the exact same seat object. Zero double-bookings were recorded, with duplicates seamlessly aborted by the underlying Postgres unique constraint. Production deployments should configure clustered Redis and scaled out HTTP workers, while assigning Kafka and the transactional outbox sweeper to a dedicated worker node.