# TicketBlitz Distributed Architecture 

## 1. Request Flow
```
User (Checkout) 
   │
   ▼
[ Rate Limiter ] ──────(429 if > 5 req/min) 
   │
   ▼
[ JWT Security Filter ] 
   │
   ▼
[ StockService (DB Tx starts) ]
   │
   ├──▶ [ RedisLockService ]
   │      ├─ [Acquire Lock -> OK]
   │      ├─ [Acquire Lock -> FAIL] ──▶ Returns 409 SEAT_UNAVAILABLE
   │      └─ [Circuit Breaker: Redis DOWN] ──▶ Bypasses logically trusting PG
   │
   ├──▶ [ PostgreSQL DB Write ]
   │      ├─ Verify SeatStatus (AVAILABLE)
   │      ├─ Insert Order (state=RESERVED, uniquely constrained on seat_id)
   │      └─ Commit Transaction
   │
   ├──▶ [ Kafka Producer Publish ] ──▶ (Fire & Forget to 'booking-confirmed')
   │
   ▼
[ HTTP 200 Return to User ] (Frontend displays "Waiting for Email")

   ... MeanWhile (Async) ...

[ Kafka Consumer ] ◀── ('booking-confirmed')
   ├─ Checks `processedAudits` via EventID (Idempotent processing)
   ├─ Simulates SMTP delay (Sends Ticket)
   └─ Acknowledges Offset (Manual ACK mechanism ensures 0 drops)
```

## 2. Concurrency Model
**Layer 1: Redis (Speed Guard)**
Redis utilizes atomic `SET NX EX` blocks to immediately drop secondary overlapping requests out of the web container completely transparently without choking HikariCP connections. This protects backend IO threads significantly.

**Layer 2: Postgres Unique Index (Correctness Guarantee)**
Because Distributed Systems break, Postgres executes a rigid `UNIQUE (seat_id) WHERE status = 'CONFIRMED'`. If Redis experiences Split Brain or the Lock Circuit Breaker kicks open bypassing Redis, PostgreSQL will definitively `Rollback` any duplicate insertion on a single physical seat unconditionally.

**Layer 3: Sweeper & Waitlist (Recovery & Fairness)**
If an un-committed user drops off, `ReservationCleanupJob` runs a batch background scan every 2 minutes. Abandoned reservations fall back into availability. Simultaneously it evaluates a Redis `ZSET` populated queue via `ZPOPMIN`, assuring waiting users receive deterministic allocations fairly.

## 3. Failure Scenarios
| Component Failed | What Breaks | What Catches It | Recovery Strategy |
|------------------|-------------|-----------------|-------------------|
| **Redis slow** | High latency locks holding HTTP threads | Resilience4j Circuit Breaker | Trips Open! Fallbacks directly to PG relying instantly on standard DB isolation levels. |
| **Redis down** | Locking mechanism halts entirely | Resilience4j Circuit Breaker | Continues gracefully tracking uniqueness at the Data layer exclusively. |
| **Kafka down** | Emails/Audits don't send immediately | Spring Kafka Retry Template | At-least-once configuration guarantees. Pushes failed retries straight to `booking-confirmed-dlq`. |
| **DB Failure** | Service stops taking write checkouts | Platform Auto-Scaling | The frontend receives 500/503. The system acts entirely stateless recovering cleanly automatically. |
| **App crash mid-lock**| App dies after `SETNX` but before DB commit | Seat stuck logically in cache | Redis TTL natively clears the lock implicitly. The database rollback handles DB orphans. |
| **Consumer lag spike**| Topic backlogging email queue | Kafka Consumer group balancing | Standardizes `max-poll-records` effectively dropping starvation scenarios buffering gracefully. |

## 4. Horizontal Scaling Strategy
We intend to scale behind 3 app instances managed by AWS ALB round-robining routing. 
- **Why Redis blocking works natively**: The `SETNX` command lives entirely on the external Redis execution string. Because Redis is single-threaded contextually across all 3 nodes, mutual exclusion is naturally replicated uniformly. 
- **DB Safety Net necessity**: Network partitioning exists. If two nodes separate and both acquire duplicate fake Redis locks thinking they control it, PostgreSQL will safely abort one guaranteeing consistency definitively.
- **Kafka Handling**: Standard consumer grouping mandates that a unique partition is specifically bound exclusively to 1 node, automatically avoiding redundant email notifications seamlessly spanning across our ALB framework.
