package adapter

import (
	"context"
	"errors"
	"math"
	"sync"
	"testing"

	"edge-bridge/internal/protocol"
)

// fakeAdapter is a stand-in for a real device adapter. A fake rather than a
// mock: the tests assert on the manager's observable behaviour, not on which
// calls it made.
type fakeAdapter struct {
	name      string
	connected bool
	closeErr  error
	closed    int
	mu        sync.Mutex
}

func (f *fakeAdapter) Name() string { return f.name }

func (f *fakeAdapter) Connect(context.Context) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.connected = true
	return nil
}

func (f *fakeAdapter) Close() error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.closed++
	f.connected = false
	return f.closeErr
}

func (f *fakeAdapter) IsConnected() bool {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.connected
}

func (f *fakeAdapter) CollectMetrics(context.Context) ([]protocol.Metric, error) {
	return nil, nil
}

func (f *fakeAdapter) CollectMetric(context.Context, protocol.MetricType) (*protocol.Metric, error) {
	return nil, nil
}

func (f *fakeAdapter) closeCount() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.closed
}

// ---- MetricMapping.ApplyTransform ----

func TestApplyTransformScalesAndOffsets(t *testing.T) {
	tests := []struct {
		name  string
		scale float32
		offse float32
		raw   float32
		want  float32
	}{
		{name: "identity", scale: 1, offse: 0, raw: 42, want: 42},
		{name: "scale only", scale: 0.1, offse: 0, raw: 250, want: 25},
		{name: "offset only", scale: 1, offse: -273.15, raw: 300, want: 26.85},
		{name: "scale then offset", scale: 0.5, offse: 10, raw: 8, want: 14},
		{name: "negative raw value", scale: 2, offse: 0, raw: -3, want: -6},
		{name: "zero scale collapses to the offset", scale: 0, offse: 5, raw: 999, want: 5},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m := &MetricMapping{Scale: tt.scale, Offset: tt.offse}

			got := m.ApplyTransform(tt.raw)

			// Float maths, so compare within tolerance rather than exactly.
			if math.Abs(float64(got-tt.want)) > 1e-4 {
				t.Errorf("ApplyTransform(%v) = %v, want %v", tt.raw, got, tt.want)
			}
		})
	}
}

// ---- Manager registry ----

func TestRegisterMakesAnAdapterRetrievable(t *testing.T) {
	m := NewManager(DefaultManagerConfig())
	a := &fakeAdapter{name: "modbus"}

	if err := m.Register(a); err != nil {
		t.Fatalf("Register returned an error: %v", err)
	}

	got, ok := m.GetAdapter("modbus")
	if !ok {
		t.Fatal("registered adapter could not be retrieved")
	}
	if got.Name() != "modbus" {
		t.Errorf("adapter name = %q, want modbus", got.Name())
	}
	if names := m.ListAdapters(); len(names) != 1 || names[0] != "modbus" {
		t.Errorf("ListAdapters = %v, want [modbus]", names)
	}
}

func TestRegisterRejectsADuplicateName(t *testing.T) {
	m := NewManager(DefaultManagerConfig())
	if err := m.Register(&fakeAdapter{name: "snmp"}); err != nil {
		t.Fatalf("first Register returned an error: %v", err)
	}

	// Silently replacing would orphan the first adapter's connection.
	if err := m.Register(&fakeAdapter{name: "snmp"}); err == nil {
		t.Error("expected the second registration of the same name to be rejected")
	}
	if names := m.ListAdapters(); len(names) != 1 {
		t.Errorf("ListAdapters = %v, want a single entry", names)
	}
}

func TestUnregisterClosesTheAdapter(t *testing.T) {
	m := NewManager(DefaultManagerConfig())
	a := &fakeAdapter{name: "mqtt"}
	if err := m.Register(a); err != nil {
		t.Fatalf("Register returned an error: %v", err)
	}

	if err := m.Unregister("mqtt"); err != nil {
		t.Fatalf("Unregister returned an error: %v", err)
	}

	// Dropping the reference without closing would leak the connection.
	if a.closeCount() != 1 {
		t.Errorf("Close called %d times, want 1", a.closeCount())
	}
	if _, ok := m.GetAdapter("mqtt"); ok {
		t.Error("adapter is still retrievable after Unregister")
	}
}

func TestUnregisterReportsAnUnknownName(t *testing.T) {
	m := NewManager(DefaultManagerConfig())

	if err := m.Unregister("never-registered"); err == nil {
		t.Error("expected an error when unregistering an unknown adapter")
	}
}

func TestUnregisterStillRemovesAnAdapterThatFailsToClose(t *testing.T) {
	m := NewManager(DefaultManagerConfig())
	a := &fakeAdapter{name: "netconf", closeErr: errors.New("device went away")}
	if err := m.Register(a); err != nil {
		t.Fatalf("Register returned an error: %v", err)
	}

	// A device that has already gone away must not be able to pin its adapter
	// in the registry forever.
	_ = m.Unregister("netconf")

	if _, ok := m.GetAdapter("netconf"); ok {
		t.Error("adapter remained registered after a failing Close")
	}
}

func TestGetAdapterReportsAnUnknownName(t *testing.T) {
	m := NewManager(DefaultManagerConfig())

	if _, ok := m.GetAdapter("nope"); ok {
		t.Error("GetAdapter reported success for an unregistered name")
	}
}

func TestListAdaptersIsEmptyForAFreshManager(t *testing.T) {
	if names := NewManager(DefaultManagerConfig()).ListAdapters(); len(names) != 0 {
		t.Errorf("ListAdapters = %v, want empty", names)
	}
}

func TestRegistryIsSafeUnderConcurrentUse(t *testing.T) {
	m := NewManager(DefaultManagerConfig())

	var wg sync.WaitGroup
	for i := 0; i < 32; i++ {
		name := string(rune('a' + i%26))
		wg.Add(1)
		go func() {
			defer wg.Done()
			_ = m.Register(&fakeAdapter{name: name})
			m.GetAdapter(name)
			m.ListAdapters()
			_ = m.Unregister(name)
		}()
	}
	wg.Wait()
}
