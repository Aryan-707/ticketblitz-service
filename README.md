# TicketBlitz

## What this is
TicketBlitz is a high-concurrency event ticketing backend built on Spring Boot that focuses entirely on processing thousands of concurrent bookings without overselling a single physical seat. It replaces standard counter-based inventory with deterministic individual seat models secured by distributed locking and final database constraints. 

## Why I built it this way
- I used Redis SETNX locks per specific seat instead of just pessimistic database locking because tying up active DB connections during high-contention traffic loops crashes instances; Redis handles the immediate mutual exclusion effortlessly without blocking Postgres threads.
- I moved idempotency validation directly to the Service layer coupled with a DB unique index rather than generic filter intercepts so that it bounds tightly around our core `processPurchase` bounds safely rejecting duplicate mobile app retries mid-partition.
- I picked CP (Consistency over Availability) for the Redis failure mode. If Redis disconnects during a flash sale, the system throws a 503 rather than falling through to raw DB writes, completely shielding us from the possibility of split-brain double-booking.

## What I'd do differently
- The `ReservationCleanupJob` currently sweeps the database every 2 minutes. Under truly massive scale with millions of stranded carts, this could cause a slight I/O spike, ideally replacing it later with exactly-once Kafka delay-queues.
- Wait-listing for specific seats isn't supported gracefully right now (users just get a 409). A websocket notification queue would be better for UX when the 10-minute hold TTL expires.
- We still return generic 500s on unexpected DB crashes instead of standardizing the precise failure format via `GlobalExceptionHandler` everywhere. 

## Known limitations
- The rate limiter uses a simple Redis `INCR` window technique. A malicious user hitting the exact boundary of a discrete minute can fire 10 requests (5 right before modulo, 5 tight after) bypassing the strict 5/min continuous flow. Token bucket is superior but overkill for v1.
- Eventual consistency on the seat map GET. Because we read `READ COMMITTED`, a user might briefly see a seat as AVAILABLE just seconds before another user's transaction officially COMMITS it. 

## One thing that was harder than expected
Refactoring the existing quantity-based logic to individual seat tracking was a headache. Migrating `Order` relationships to point exactly to `seatId` and ensuring the compensating transactions (abandonment job) cleanly unraveled the DB state required heavily tweaking how JPA manages cascade and orphan flushes. 

## Running locally

Copy the environment layout and set your connections:
```bash
export DB_URL="jdbc:postgresql://localhost:5432/ticketing_db"
export DB_USERNAME="postgres"
export DB_PASSWORD="password"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export JWT_SECRET="YOUR_256_BIT_SECRET"
```

Start the infrastructure using docker-compose:
```bash
docker-compose up -d
```

Start the application:
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## API
Live Render URL: https://ticketblitz.onrender.com (Note: the user hosts the frontend/API gateway matching it).
Swagger UI: http://localhost:8080/swagger-ui/index.html

Import `docs/postman-collection.json` into Postman. Set `base_url` to `http://localhost:8080` for local or the deployed version to test all endpoints.
