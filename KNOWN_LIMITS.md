## Connection Pool Ceiling
maximum-pool-size=20 supports ~200 concurrent transactions 
assuming avg 100ms transaction time.
Beyond this, requests queue at HikariCP level.
Horizontal scaling (more app nodes) linearly increases 
total pool capacity. Redis and Kafka are stateless 
horizontally — only Postgres is the bottleneck.

## Outbox Delay (Kafka Asynchronous Waitlist)
Notifications are deliberately decoupled asynchronously to save synchronous HTTP locking.
Delays are usually sub-10ms. Acceptable for email confirmation generation.
Unacceptable for real-time push — would require WebSocket or SSE layer natively.

## No Saga Pattern
If the external pseudo-payment service fails after order.save(), 
there is no automated distributed rollback natively scaling across nodes.
Compensation must be triggered manually via admin API routes.
This is the single largest production gap.

## Single Postgres Node
No read replicas. No sharding.
Write throughput ceiling: ~5000 TPS on standard cloud instance.
Read scaling would require adding a replica 
and routing read-only queries natively via @Transactional(readOnly=true).
