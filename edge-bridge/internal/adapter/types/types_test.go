package types

import (
	"testing"
	"time"
)

func TestApplyTransformScalesThenOffsets(t *testing.T) {
	m := &MetricMapping{Scale: 2.0, Offset: 1.5}
	if got := m.ApplyTransform(10); got != 21.5 {
		t.Errorf("ApplyTransform(10) = %v, want 21.5", got)
	}
}

func TestApplyTransformIdentity(t *testing.T) {
	m := &MetricMapping{Scale: 1.0, Offset: 0.0}
	if got := m.ApplyTransform(-3.25); got != -3.25 {
		t.Errorf("ApplyTransform(-3.25) = %v, want -3.25", got)
	}
}

func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()
	if cfg.Enabled {
		t.Error("DefaultConfig should be disabled by default")
	}
	if cfg.Timeout != 5*time.Second {
		t.Errorf("Timeout = %v, want 5s", cfg.Timeout)
	}
	if cfg.PollInterval != 30*time.Second {
		t.Errorf("PollInterval = %v, want 30s", cfg.PollInterval)
	}
	if cfg.RetryAttempts != 3 {
		t.Errorf("RetryAttempts = %d, want 3", cfg.RetryAttempts)
	}
	if cfg.RetryDelay != 1*time.Second {
		t.Errorf("RetryDelay = %v, want 1s", cfg.RetryDelay)
	}
}
