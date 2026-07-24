"""Dependency-free concurrent load generator for a quick latency baseline."""
import sys, time, threading, urllib.request
from collections import deque

url = sys.argv[1]
concurrency = int(sys.argv[2]) if len(sys.argv) > 2 else 20
duration = float(sys.argv[3]) if len(sys.argv) > 3 else 10.0

latencies = deque()
errors = [0]
lock = threading.Lock()
stop_at = time.monotonic() + duration


def worker():
    while time.monotonic() < stop_at:
        t0 = time.perf_counter()
        try:
            with urllib.request.urlopen(url, timeout=5) as r:
                r.read()
                ok = r.status == 200
        except Exception:
            ok = False
        dt = (time.perf_counter() - t0) * 1000.0
        with lock:
            if ok:
                latencies.append(dt)
            else:
                errors[0] += 1


threads = [threading.Thread(target=worker) for _ in range(concurrency)]
start = time.monotonic()
for t in threads:
    t.start()
for t in threads:
    t.join()
elapsed = time.monotonic() - start

lat = sorted(latencies)
n = len(lat)


def pct(p):
    if not lat:
        return float("nan")
    return lat[min(n - 1, int(p / 100.0 * n))]


print(f"URL:          {url}")
print(f"Concurrency:  {concurrency}   Duration: {elapsed:.1f}s")
print(f"Requests OK:  {n}   Errors: {errors[0]}")
print(f"Throughput:   {n / elapsed:.0f} req/s")
if lat:
    print(f"Latency ms:   min={lat[0]:.1f}  p50={pct(50):.1f}  p95={pct(95):.1f}  "
          f"p99={pct(99):.1f}  max={lat[-1]:.1f}  mean={sum(lat)/n:.1f}")
