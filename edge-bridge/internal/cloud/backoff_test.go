package cloud

import (
	"testing"
	"time"
)

func TestBackoffWithJitterStaysWithinCappedExponential(t *testing.T) {
	base := 1 * time.Second
	// For each attempt the wait must be in [0, min(base*2^(attempt-1), maxBackoff)].
	for attempt := 1; attempt <= 12; attempt++ {
		exp := base << (attempt - 1)
		if exp <= 0 || exp > maxBackoff {
			exp = maxBackoff
		}
		for i := 0; i < 100; i++ {
			got := backoffWithJitter(base, attempt)
			if got < 0 || got > exp {
				t.Fatalf("attempt %d: backoff %v out of [0,%v]", attempt, got, exp)
			}
		}
	}
}

func TestBackoffNeverExceedsMax(t *testing.T) {
	// A large attempt would overflow the shift; it must clamp to maxBackoff.
	for i := 0; i < 100; i++ {
		if got := backoffWithJitter(time.Second, 40); got > maxBackoff {
			t.Fatalf("backoff %v exceeds cap %v", got, maxBackoff)
		}
	}
}
