package bridge

import (
	"log"
	"sync"
	"time"

	"edge-bridge/internal/cloud"
)

// MetricBatch represents a batch of metrics collected at a specific time.
type MetricBatch struct {
	StationID string
	Metrics   []cloud.MetricData
	Collected time.Time
}

// MetricBuffer is a thread-safe ring buffer for metric batches.
// When the cloud is unreachable, collected metrics are buffered here
// and uploaded in bulk when connectivity returns.
type MetricBuffer struct {
	mu       sync.Mutex
	batches  []MetricBatch
	maxSize  int
	dropped  int64 // total batches dropped due to overflow
	buffered int64 // total batches ever buffered
}

// NewMetricBuffer creates a metric buffer with the given max capacity.
// When full, the oldest batch is dropped to make room for new ones.
func NewMetricBuffer(maxSize int) *MetricBuffer {
	if maxSize <= 0 {
		maxSize = 100 // ~50 min at 30s intervals
	}
	return &MetricBuffer{
		batches: make([]MetricBatch, 0, maxSize),
		maxSize: maxSize,
	}
}

// Push adds a metric batch to the buffer. If full, drops the oldest.
func (b *MetricBuffer) Push(batch MetricBatch) {
	b.mu.Lock()
	defer b.mu.Unlock()

	if len(b.batches) >= b.maxSize {
		// Drop oldest
		b.batches = b.batches[1:]
		b.dropped++
	}
	b.batches = append(b.batches, batch)
	b.buffered++
}

// Drain returns all buffered batches and clears the buffer.
// Returns nil if the buffer is empty.
func (b *MetricBuffer) Drain() []MetricBatch {
	b.mu.Lock()
	defer b.mu.Unlock()

	if len(b.batches) == 0 {
		return nil
	}

	result := b.batches
	b.batches = make([]MetricBatch, 0, b.maxSize)
	return result
}

// Len returns the current number of buffered batches.
func (b *MetricBuffer) Len() int {
	b.mu.Lock()
	defer b.mu.Unlock()
	return len(b.batches)
}

// Stats returns buffer statistics.
func (b *MetricBuffer) Stats() (current int, totalBuffered int64, totalDropped int64) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return len(b.batches), b.buffered, b.dropped
}

// Backoff implements exponential backoff for cloud reconnection.
type Backoff struct {
	mu          sync.Mutex
	current     time.Duration
	min         time.Duration
	max         time.Duration
	factor      float64
	consecutive int // consecutive failures
}

// NewBackoff creates a new exponential backoff.
func NewBackoff(min, max time.Duration) *Backoff {
	return &Backoff{
		current: min,
		min:     min,
		max:     max,
		factor:  2.0,
	}
}

// Next returns the current backoff duration and increases it.
func (b *Backoff) Next() time.Duration {
	b.mu.Lock()
	defer b.mu.Unlock()

	d := b.current
	b.consecutive++
	b.current = time.Duration(float64(b.current) * b.factor)
	if b.current > b.max {
		b.current = b.max
	}
	return d
}

// Reset resets the backoff to its minimum value.
func (b *Backoff) Reset() {
	b.mu.Lock()
	defer b.mu.Unlock()

	b.current = b.min
	b.consecutive = 0
}

// ConsecutiveFailures returns the number of consecutive failures.
func (b *Backoff) ConsecutiveFailures() int {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.consecutive
}

// uploadBufferedMetrics drains the buffer and uploads all batches to the cloud.
// Returns the number of successfully uploaded batches.
func uploadBufferedMetrics(buffer *MetricBuffer, client *cloud.Client) int {
	batches := buffer.Drain()
	if len(batches) == 0 {
		return 0
	}

	log.Printf("Uploading %d buffered metric batches...", len(batches))
	uploaded := 0

	for _, batch := range batches {
		if len(batch.Metrics) == 0 {
			continue
		}

		resp, err := client.UploadMetrics(batch.StationID, batch.Metrics)
		if err != nil {
			// Re-buffer remaining batches on failure
			remaining := batches[uploaded:]
			for _, r := range remaining {
				buffer.Push(r)
			}
			log.Printf("Buffered upload failed after %d/%d batches: %v",
				uploaded, len(batches), err)
			return uploaded
		}

		uploaded++
		log.Printf("Buffered batch uploaded (%d metrics, collected %s ago, status: %s)",
			resp.Received,
			time.Since(batch.Collected).Round(time.Second),
			resp.Status)
	}

	log.Printf("All %d buffered batches uploaded successfully", uploaded)
	return uploaded
}
