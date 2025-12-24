# TicketBlitz Backend

## What problem this solves
TicketBlitz solves the flash sale double-booking problem where thousands of users attempt to purchase the exact same physical seat simultaneously. Simple integer counters and `SELECT FOR UPDATE` queries fail here because heavy contention immediately exhausts database connection pools, crashing the application before processing completes. This system implements extreme concurrency safety natively, preventing overselling under intense simultaneous load while degrading gracefully during external infrastructure failures.

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
   │                         [ Bypasses Redis securely ]
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
* **Layer 1: Redis** — It functions exclusively as an atomic speed guard (`SET NX EX 600`) shielding PostgreSQL from duplicate transactions by dropping thousands of identical requests identically before they ever reach a HikariCP thread.
* **Layer 2: Postgres unique index** — `CREATE UNIQUE INDEX ON orders(seat_id) WHERE status='CONFIRMED'` natively guarantees absolute correctness; if Redis crashes and the CircuitBreaker bypass falls through to the DB, Postgres definitively rejects identical lock attempts physically rolling back duplicate state operations.
* **Layer 3: Sweeper + Waitlist** — Recovers 10-minute abandoned checkouts asynchronously returning them cleanly into availability, utilizing atomic `ZPOPMIN` distributing waitlist notifications detaching explicitly avoiding multiple notification dispatches natively across 3 horizontal pods.

## Key engineering decisions
* **Atomic `SET NX EX` vs `SETNX + EXPIRE`** → Explicit atomic combination prevents deadlocks where an app crash between separate SETNX and EXPIRE calls permanently orphans the lock indefinitely.
* **Transactional Outbox vs direct `producer.send()`** → Direct sending drops notifications permanently if the Kafka broker is down; securely writing to an `outbox_events` table within the same DB transaction mathematically guarantees at-least-once notification delivery eventually.
* **ZSET vs LIST for waitlist** → ZSET automatically handles exact idempotency (`ZADD NX`) preventing impatient duplicate user joins naturally sorting strictly via UNIX timestamps chronologically.
* **ShedLock vs running sweeper on all nodes** → Distributing exclusively saves connection pools natively preventing all redundant EC2 instances dynamically firing the exact same redundant heavy SQL queries simultaneously.
* **Circuit breaker fallback vs hard 503 on Redis failure** → Degrading fallback dynamically bypasses unreachable caching layers entirely routing transactions directly utilizing Postgres Unique Indexes natively shielding core checkout availability.

## Known limitations
* **No Saga pattern** → If payment processing completely fails after `order.save()`, there is no automated distributed rollback natively scaling across nodes requiring manual admin API compensation routes.
* **Outbox poller delay** → Notifications are delayed by up to 5 seconds waiting on the polling interval; acceptable for email delivery but completely unacceptable for real-time push sockets.
* **Single Postgres node** → With no read replicas or sharding architecture currently deployed, the absolute physical write throughput ceiling restricts cleanly to ~5000 TPS.
* **Rate limiter uses fixed window** → A simple modulo boundary exploit is easily possible natively allowing 10 tight requests simultaneously dynamically hitting boundaries natively (5 right before minute boundary + 5 tightly after).
* **READ COMMITTED isolation** → Seat statuses may theoretically briefly appear `AVAILABLE` to another user's frontend millisecond windows intrinsically before competing synchronous transactions officially `COMMIT`.
* **Waitlist notification is a log statement** → Simulated tracking replaces physical push notifications dynamically avoiding WebSocket/SSE infrastructure layers inherently restricting true realtime operations.

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
Simulated standard concert ticketing flash sales actively pounding the `processPurchase` checkout routines utilizing exact `k6` local injection scripts scaling statically 500 concurrent threads correctly yielding purely Native DB rejects successfully proving absolutely zero instances double-booked accurately matching `load-test/results.txt` constraints natively.
