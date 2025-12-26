# TicketBlitz Backend

## What problem this solves
TicketBlitz solves the flash sale double-booking problem.
Thousands of users attempt to buy the exact same seat simultaneously.
Current methods fail because heavy contention exhausts connection pools.
It prevents overselling using distributed locking, database constraints, and async event processing.
It degrades gracefully during infrastructure failures.

## Architecture
```
User (Checkout) 
   │
   ▼
[ Rate Limiter ]
   │
   ▼
[ JWT Filter ]
   │
   ▼
[ BookingService ] ──────▶ [ Redis Lock (SET NX EX) ]
   │                              │ (If Redis Down/Slow -> CircuitBreaker trips OPEN)
   │                              ▼
   │                         [ Bypasses Redis ]
   │                              │
   ▼                              ▼
[ DB Write (Unique Index catches duplicates) + Outbox Write ]
   │
   ▼
[ 200 OK Returned instantly to User HTTP thread ]
   │
   ▼
[ OutboxPoller ] ───────▶ [ Kafka (booking-confirmed topic) ]
                                  │
                                  ▼
[ Kafka Consumer ] ────▶ [ Email Generation / Audit Log ]
```

## Concurrency model (3 layers)
* **Layer 1: Redis** — Atomic SET NX EX 600 acquires a per-seat lock before any DB interaction. This prevents thousands of concurrent requests from reaching HikariCP simultaneously, which would exhaust the connection pool under flash sale load.
* **Layer 2: Postgres unique index** — `CREATE UNIQUE INDEX` guarantees correctness. If Redis crashes, Postgres rejects duplicate lock attempts.
* **Layer 3: Sweeper + Waitlist** — It recovers abandoned checkouts asynchronously. Atomic `ZPOPMIN` prevents multiple notification dispatches across nodes.

## Key engineering decisions
* **Atomic `SET NX EX` vs `SETNX + EXPIRE`** → Atomic execution prevents deadlocks. An app crash between separate commands orphans the lock.
* **Transactional Outbox vs direct `producer.send()`** → Direct sending drops notifications if the broker is down. Writing to an outbox table guarantees delivery.
* **ZSET vs LIST for waitlist** → ZSET handles idempotency via `ZADD NX`. It prevents duplicate joins. It sorts via UNIX timestamps.
* **ShedLock vs running sweeper on all nodes** → Running on one node saves connection pools. It prevents all instances from firing heavy SQL queries.
* **Circuit breaker fallback vs hard 503 on Redis failure** → Fallback bypasses unreachable caching layers. It routes transactions directly to Postgres. This preserves checkout availability.

## Known limitations
* **No Saga pattern** → There is no automated distributed rollback. Payment processing failures require manual compensation routes.
* **Outbox poller delay** → Notifications are delayed by up to 5 seconds. This is unacceptable for real-time push sockets.
* **Single Postgres node** → There are no read replicas. The physical write throughput ceiling restricts to ~5000 TPS.
* **Rate limiter uses fixed window** → Rate limiter uses a fixed window. A user can fire 5 requests at 11:59:59 and 5 more at 12:00:00 — 10 requests in under 2 seconds — bypassing the 5/min limit. Token bucket algorithm would fix this.
* **READ COMMITTED isolation** → Seat statuses may briefly appear `AVAILABLE`. This happens before competing transactions officially `COMMIT`.
* **Waitlist notification is a log statement** → Tracking replaces physical push notifications. It avoids WebSocket infrastructure layers.

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

## Load test results
We used k6 to simulate a concert flash sale with 500 concurrent threads targeting the same seat. Zero double-bookings occurred. All duplicate attempts were rejected at the Postgres constraint layer. Full output in load-test/results.txt.
