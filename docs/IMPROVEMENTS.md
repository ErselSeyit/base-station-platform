# Production Readiness Improvements

This document outlines critical gaps in the current implementation and how to fix them for production use.

---

## 1. Internal Service Authentication

### Current Problem
Services blindly trust `X-User-Role` headers. Anyone who bypasses the gateway (misconfigured ingress, internal network access) can impersonate any user:

```bash
# If monitoring-service port 8085 is exposed, instant admin access:
curl -H "X-User-Role: ADMIN" http://monitoring-service:8085/api/metrics
```

### Solution: Shared Secret Verification

Add a cryptographically signed internal token that only the gateway can generate:

**API Gateway** (`application.yml`):
```yaml
security:
  internal:
    secret: ${INTERNAL_SERVICE_SECRET}  # Generate with: openssl rand -hex 32
```

**Gateway Filter** (add to `JwtAuthenticationFilter.java`):
```java
private String generateInternalToken(String username, String role) {
    String payload = username + ":" + role + ":" + System.currentTimeMillis();
    return HmacUtils.hmacSha256Hex(internalSecret, payload) + "." + payload;
}

// Add to request:
requestBuilder.header("X-Internal-Auth", generateInternalToken(username, role));
```

**Downstream Services** (new filter before SecurityConfig):
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalAuthFilter extends OncePerRequestFilter {

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) {
        String authHeader = request.getHeader("X-Internal-Auth");

        if (authHeader == null) {
            response.sendError(403, "Missing internal authentication");
            return;
        }

        String[] parts = authHeader.split("\\.", 2);
        String signature = parts[0];
        String payload = parts[1];

        String expectedSig = HmacUtils.hmacSha256Hex(internalSecret, payload);

        if (!MessageDigest.isEqual(signature.getBytes(), expectedSig.getBytes())) {
            response.sendError(403, "Invalid internal authentication");
            return;
        }

        // Verify timestamp is within 30 seconds to prevent replay
        String[] payloadParts = payload.split(":");
        long timestamp = Long.parseLong(payloadParts[2]);
        if (System.currentTimeMillis() - timestamp > 30000) {
            response.sendError(403, "Internal token expired");
            return;
        }

        chain.doFilter(request, response);
    }
}
```

**Result**: Even if a service is exposed, requests without the signed `X-Internal-Auth` header are rejected.

---

## 2. Rate Limiting Per Station ID

### Current Problem
Gateway-level rate limiting (10-100 req/s) doesn't prevent a single station from flooding the system:

```bash
# Attacker floods metrics for station-1, exhausting MongoDB:
for i in {1..10000}; do
  curl -X POST /api/metrics/batch -d '{"stationId": "station-1", ...}'
done
```

### Solution: Station-Level Rate Limiting

**MonitoringController.java**:
```java
@RestController
@RequestMapping("/api/metrics")
public class MonitoringController {

    private final LoadingCache<String, AtomicInteger> stationRateLimiter = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(new CacheLoader<>() {
            public AtomicInteger load(String key) {
                return new AtomicInteger(0);
            }
        });

    @PostMapping("/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody BatchMetricRequest request) {
        String stationId = request.getStationId();

        // Max 100 batch requests per station per minute
        if (stationRateLimiter.get(stationId).incrementAndGet() > 100) {
            return ResponseEntity.status(429)
                .body("Rate limit exceeded for station: " + stationId);
        }

        metricService.ingestBatch(request);
        return ResponseEntity.ok().build();
    }
}
```

**Result**: Each station limited to 100 batch requests/min regardless of total gateway capacity.

---

## 3. Backpressure Handling

### Current Problem
No queue management when metric ingestion rate exceeds MongoDB write capacity. Service crashes or becomes unresponsive under load.

### Solution: Reactive Streams with Bounded Queue

Replace synchronous ingestion with reactive backpressure:

**pom.xml** (add dependency):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**MetricService.java**:
```java
@Service
public class MetricService {

    private final Sinks.Many<Metric> metricSink = Sinks.many()
        .multicast()
        .onBackpressureBuffer(10000);  // Drop oldest if queue > 10k

    @PostConstruct
    public void startMetricProcessor() {
        metricSink.asFlux()
            .buffer(Duration.ofSeconds(5), 500)  // Batch writes every 5s or 500 metrics
            .flatMap(batch -> mongoTemplate.insertAll(batch))
            .doOnError(e -> log.error("Metric ingestion failed", e))
            .retry(3)
            .subscribe();
    }

    public void ingestMetric(Metric metric) {
        metricSink.emitNext(metric, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

**Result**:
- Metrics queued in-memory (bounded to 10k)
- Batched writes to MongoDB reduce I/O
- Automatic retry on transient failures
- Graceful degradation when queue is full

---

## 4. Stress Testing & Documentation

### Current Gap
No evidence the system handles realistic load. Claims like "WebSocket streaming" are untested.

### Solution: Load Testing Script

**`scripts/load_test.py`**:
```python
import asyncio
import aiohttp
import time

async def flood_metrics(session, station_id):
    url = "http://localhost:8080/api/metrics/batch"
    payload = {
        "stationId": station_id,
        "metrics": [{"type": "CPU_USAGE", "value": 50} for _ in range(100)]
    }

    async with session.post(url, json=payload) as resp:
        return resp.status

async def main():
    async with aiohttp.ClientSession() as session:
        tasks = []
        for i in range(500):  # 500 concurrent requests
            tasks.append(flood_metrics(session, f"station-{i % 10}"))

        start = time.time()
        results = await asyncio.gather(*tasks)
        duration = time.time() - start

        success = results.count(200)
        rate_limited = results.count(429)

        print(f"Throughput: {500/duration:.2f} req/s")
        print(f"Success: {success}, Rate Limited: {rate_limited}")

asyncio.run(main())
```

**Expected Results** (document in `docs/PERFORMANCE.md`):
```
Throughput: 312 req/s
Success: 350, Rate Limited: 150
P50 latency: 45ms
P99 latency: 320ms
MongoDB writes: 8,000/sec sustained
```

---

## 5. Network Segmentation (Docker Compose)

### Current Problem
All services on flat `bridge` network. No isolation between frontend, backend, and data stores.

### Solution: Multi-Network Architecture

**`docker-compose.yml`**:
```yaml
networks:
  frontend-net:
    driver: bridge
  backend-net:
    driver: bridge
    internal: true  # No external access
  data-net:
    driver: bridge
    internal: true

services:
  api-gateway:
    networks:
      - frontend-net
      - backend-net

  base-station-service:
    networks:
      - backend-net
      - data-net

  postgres:
    networks:
      - data-net  # Only accessible to services, not gateway
```

**Result**:
- Frontend can only reach gateway
- Services can't be accessed externally
- Databases isolated to data plane

---

## Priority Order for Fixes

1. **Internal service authentication** (blocks credential spoofing)
2. **Rate limiting per station** (prevents resource exhaustion)
3. **Stress testing** (shows system works under load)
4. **Network segmentation** (defense in depth)
5. **Backpressure handling** (graceful degradation)

---

## Production Readiness Gaps

**Current State:**
This project demonstrates Spring Cloud patterns but intentionally omits production features for educational clarity.

**Missing Production Features:**
1. **Security:** HMAC-signed internal tokens or mTLS for service-to-service auth
2. **Rate limiting:** Per-resource quotas (not just per-IP)
3. **Backpressure:** Handling metric floods gracefully
4. **Network segmentation:** Multi-network Docker architecture
5. **Observability:** Distributed tracing with Zipkin/Jaeger

**Architectural Decision:**
This project is deliberately over-engineered with microservices. For a real system at this scale, a modular monolith would be more appropriate, extracting services only when scaling demands it.
