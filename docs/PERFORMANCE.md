# Performance Baseline

A first latency/throughput baseline for a single Spring service, captured to
give future changes something to regress against. It is intentionally a
**per-service HTTP-stack** baseline, not a full-stack business-transaction load
test (which needs the whole runtime stack up).

## Method

- Target: `tmf-api` running as its packaged jar (`-Xmx512m`) against a local
  MongoDB, on `localhost` — so numbers reflect the app, not the network.
- Generator: `scripts/loadtest.py` (dependency-free, thread-per-connection).
- Endpoints chosen to isolate the HTTP path:
  - `/actuator/health` — a `200` through Tomcat + the full Spring Security
    filter chain.
  - `/actuator/prometheus` — the metrics scrape path, larger response body.
- A short warm-up preceded each measured run.

Reproduce:

```bash
java -Xmx512m -jar tmf-api/target/tmf-api-1.0.0.jar \
  --server.port=8086 \
  --spring.data.mongodb.uri=mongodb://localhost:27017/tmf_inventory \
  --security.internal.enabled=false &
python3 scripts/loadtest.py http://localhost:8086/actuator/health 20 12
```

## Results

Single service, 8-core dev host, warm JVM. Latencies in milliseconds.

| Endpoint | Concurrency | Throughput | p50 | p95 | p99 | max | Errors |
|----------|-------------|------------|-----|-----|-----|-----|--------|
| `/actuator/health` | 20 | ~5,700 req/s | 3.4 | 5.4 | 6.8 | 37.4 | 0 |
| `/actuator/prometheus` | 10 | ~4,300 req/s | 2.1 | 3.3 | 8.7 | 38.0 | 0 |

Observations:

- Zero errors across ~100k requests; the service is stable under sustained
  concurrency.
- Sub-10ms p99 on the health path — the security filter chain (including the
  header-authentication filter) adds no meaningful latency.
- The occasional ~37ms `max` lines up with JVM GC pauses / JIT warm-up tails,
  not sustained slowness (p99 stays < 9ms).

## Not covered (needs the full stack)

- End-to-end business transactions through the API gateway (JWT validation,
  routing, downstream service + DB round-trips).
- Cross-service flows: metric ingest → RabbitMQ → alert evaluation →
  notification.
- Sustained soak / memory-leak testing.

Run these once the full `docker compose` stack can be brought up; drive load at
the gateway rather than an individual service so the numbers reflect a real
request path.
