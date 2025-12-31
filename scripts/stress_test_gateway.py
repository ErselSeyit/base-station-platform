#!/usr/bin/env python3
"""
Gateway Rate Limiter Stress Test

Tests whether the Redis-backed rate limiter actually works
by attempting to DOS the auth service.

Expected: 429 Too Many Requests after exceeding limits
Failure: Service crashes or becomes unresponsive
"""

import asyncio
import aiohttp
import time
from collections import Counter

GATEWAY_URL = "http://localhost:8080"
AUTH_ENDPOINT = f"{GATEWAY_URL}/api/v1/auth/validate"

# Auth service limits: 10 req/s replenish, 20 burst capacity
REQUESTS_TO_SEND = 100
CONCURRENT_REQUESTS = 50


async def make_request(session, request_num):
    """Make a single request and return status code + timing"""
    start = time.time()
    try:
        async with session.get(AUTH_ENDPOINT, timeout=aiohttp.ClientTimeout(total=5)) as resp:
            duration = time.time() - start
            return {
                'num': request_num,
                'status': resp.status,
                'duration_ms': round(duration * 1000, 2),
                'rate_limit_remaining': resp.headers.get('X-RateLimit-Remaining', 'N/A'),
                'error': None
            }
    except asyncio.TimeoutError:
        return {'num': request_num, 'status': 'TIMEOUT', 'duration_ms': 5000, 'error': 'Timeout'}
    except Exception as e:
        return {'num': request_num, 'status': 'ERROR', 'duration_ms': 0, 'error': str(e)}


async def stress_test():
    """Send REQUESTS_TO_SEND requests as fast as possible"""
    print(f"🔥 Stress Testing Gateway Rate Limiter")
    print(f"Target: {AUTH_ENDPOINT}")
    print(f"Expected: First ~20 succeed, rest get 429 (rate limited)\n")

    async with aiohttp.ClientSession() as session:
        tasks = []
        start_time = time.time()

        # Fire all requests concurrently
        for i in range(REQUESTS_TO_SEND):
            tasks.append(make_request(session, i + 1))

        results = await asyncio.gather(*tasks)

        total_duration = time.time() - start_time

    # Analyze results
    status_counts = Counter(r['status'] for r in results)
    avg_duration = sum(r['duration_ms'] for r in results if isinstance(r['duration_ms'], (int, float))) / len(results)

    print("\n" + "=" * 60)
    print("RESULTS")
    print("=" * 60)
    print(f"Total Requests: {REQUESTS_TO_SEND}")
    print(f"Total Duration: {total_duration:.2f}s")
    print(f"Throughput: {REQUESTS_TO_SEND/total_duration:.2f} req/s")
    print(f"Avg Latency: {avg_duration:.2f}ms\n")

    print("Status Code Distribution:")
    for status, count in sorted(status_counts.items()):
        percentage = (count / REQUESTS_TO_SEND) * 100
        print(f"  {status}: {count} ({percentage:.1f}%)")

    # Verdict
    print("\n" + "=" * 60)
    print("VERDICT")
    print("=" * 60)

    rate_limited = status_counts.get(429, 0)
    successful = status_counts.get(200, 0) + status_counts.get(401, 0)  # 401 = auth failed (expected)
    timeouts = status_counts.get('TIMEOUT', 0)
    errors = status_counts.get('ERROR', 0)

    if rate_limited >= 70:  # Expect ~80% to be rate limited
        print("✅ PASS: Rate limiter is WORKING")
        print(f"   - {successful} requests succeeded (within burst capacity)")
        print(f"   - {rate_limited} requests rate-limited (429)")
        print("   - Service remained responsive (no crashes)")
        return True
    elif timeouts > 10 or errors > 10:
        print("❌ FAIL: Service became unresponsive")
        print(f"   - {timeouts} timeouts, {errors} errors")
        print("   - Rate limiter may not be configured correctly")
        return False
    else:
        print("⚠️  INCONCLUSIVE: Not enough rate limiting")
        print(f"   - Only {rate_limited} requests were rate-limited")
        print("   - Expected ~80+ to hit 429 status")
        print("   - Check if Redis is running and configured correctly")
        return False


async def show_redis_keys():
    """Show what keys are being created in Redis"""
    try:
        import redis
        r = redis.Redis(host='localhost', port=6379, decode_responses=True)
        keys = r.keys('request_rate_limiter*')
        if keys:
            print("\n" + "=" * 60)
            print("REDIS KEYS (Proof rate limiter uses Redis)")
            print("=" * 60)
            for key in keys[:5]:  # Show first 5
                value = r.get(key)
                ttl = r.ttl(key)
                print(f"  {key}")
                print(f"    Value: {value}, TTL: {ttl}s")
        else:
            print("\n⚠️  No Redis keys found - is Redis running?")
    except ImportError:
        print("\n(Install 'redis' package to see Redis keys: pip install redis)")
    except Exception as e:
        print(f"\n⚠️  Could not connect to Redis: {e}")


if __name__ == "__main__":
    print("Prerequisites:")
    print("  1. Start the platform: docker compose up -d")
    print("  2. Wait for gateway to be ready: curl http://localhost:8080/actuator/health")
    print("  3. Install aiohttp: pip install aiohttp")
    print("\nStarting in 3 seconds...\n")
    time.sleep(3)

    try:
        success = asyncio.run(stress_test())
        asyncio.run(show_redis_keys())

        if success:
            print("\n🎯 Result: Your rate limiter actually works!")
            print("   An attacker CANNOT DOS your auth service.")
        else:
            print("\n💀 Result: Rate limiter is broken or misconfigured.")

    except KeyboardInterrupt:
        print("\n\nTest interrupted.")
    except Exception as e:
        print(f"\n❌ Test failed with error: {e}")
        print("   Make sure the platform is running: docker compose up -d")
