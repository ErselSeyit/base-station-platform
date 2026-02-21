package bridge

import (
	"testing"
	"time"

	"edge-bridge/internal/cloud"
)

func TestMetricBufferPushAndDrain(t *testing.T) {
	buf := NewMetricBuffer(3)

	// Push 2 batches
	buf.Push(MetricBatch{StationID: "1", Collected: time.Now()})
	buf.Push(MetricBatch{StationID: "2", Collected: time.Now()})

	if buf.Len() != 2 {
		t.Fatalf("expected Len()=2, got %d", buf.Len())
	}

	// Drain
	batches := buf.Drain()
	if len(batches) != 2 {
		t.Fatalf("expected 2 batches, got %d", len(batches))
	}
	if batches[0].StationID != "1" {
		t.Fatalf("expected station '1', got '%s'", batches[0].StationID)
	}

	// Buffer should be empty after drain
	if buf.Len() != 0 {
		t.Fatalf("expected empty buffer after drain, got %d", buf.Len())
	}
	if buf.Drain() != nil {
		t.Fatal("expected nil from empty drain")
	}
}

func TestMetricBufferOverflow(t *testing.T) {
	buf := NewMetricBuffer(2)

	buf.Push(MetricBatch{StationID: "1", Collected: time.Now()})
	buf.Push(MetricBatch{StationID: "2", Collected: time.Now()})
	buf.Push(MetricBatch{StationID: "3", Collected: time.Now()}) // drops "1"

	if buf.Len() != 2 {
		t.Fatalf("expected Len()=2, got %d", buf.Len())
	}

	current, buffered, dropped := buf.Stats()
	if current != 2 || buffered != 3 || dropped != 1 {
		t.Fatalf("stats: current=%d buffered=%d dropped=%d", current, buffered, dropped)
	}

	batches := buf.Drain()
	if batches[0].StationID != "2" {
		t.Fatalf("oldest should be '2' after drop, got '%s'", batches[0].StationID)
	}
	if batches[1].StationID != "3" {
		t.Fatalf("newest should be '3', got '%s'", batches[1].StationID)
	}
}

func TestMetricBufferEmptyDrain(t *testing.T) {
	buf := NewMetricBuffer(10)

	if buf.Drain() != nil {
		t.Fatal("expected nil from empty drain")
	}
}

func TestBackoffExponentialGrowth(t *testing.T) {
	b := NewBackoff(1*time.Second, 30*time.Second)

	d1 := b.Next()
	if d1 != 1*time.Second {
		t.Fatalf("expected 1s, got %v", d1)
	}

	d2 := b.Next()
	if d2 != 2*time.Second {
		t.Fatalf("expected 2s, got %v", d2)
	}

	d3 := b.Next()
	if d3 != 4*time.Second {
		t.Fatalf("expected 4s, got %v", d3)
	}

	if b.ConsecutiveFailures() != 3 {
		t.Fatalf("expected 3 failures, got %d", b.ConsecutiveFailures())
	}
}

func TestBackoffCapsAtMax(t *testing.T) {
	b := NewBackoff(1*time.Second, 10*time.Second)

	// Grow: 1, 2, 4, 8, 16 → capped to 10
	for i := 0; i < 4; i++ {
		b.Next()
	}
	d := b.Next() // 5th call: current should be capped
	if d > 10*time.Second {
		t.Fatalf("expected <= 10s, got %v", d)
	}
}

func TestBackoffReset(t *testing.T) {
	b := NewBackoff(1*time.Second, 60*time.Second)
	b.Next()
	b.Next()
	b.Reset()

	if b.ConsecutiveFailures() != 0 {
		t.Fatal("expected 0 failures after reset")
	}
	d := b.Next()
	if d != 1*time.Second {
		t.Fatalf("expected 1s after reset, got %v", d)
	}
}

func TestMetricBatchWithMetrics(t *testing.T) {
	now := time.Now()
	batch := MetricBatch{
		StationID: "42",
		Metrics: []cloud.MetricData{
			{Type: "CPU_USAGE", Value: 75.0, Timestamp: now},
			{Type: "TEMPERATURE", Value: 65.0, Timestamp: now},
		},
		Collected: now,
	}

	if len(batch.Metrics) != 2 {
		t.Fatalf("expected 2 metrics, got %d", len(batch.Metrics))
	}
	if batch.StationID != "42" {
		t.Fatalf("expected station '42', got '%s'", batch.StationID)
	}
	if batch.Collected != now {
		t.Fatalf("expected collected time to match")
	}
}
