// Package adapter provides a unified interface for collecting metrics from various protocols.
package adapter

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"edge-bridge/internal/protocol"
)

// Manager orchestrates multiple protocol adapters for metric collection.
type Manager struct {
	adapters       map[string]Adapter
	mu             sync.RWMutex
	metrics        chan protocol.Metric
	collectResults chan collectResult
	done           chan struct{}
	stopOnce       sync.Once // Ensure Stop only closes done channel once
	config         ManagerConfig
}

// ManagerConfig holds configuration for the adapter manager.
type ManagerConfig struct {
	// CollectInterval is the interval between metric collection cycles.
	CollectInterval time.Duration

	// MetricsBufferSize is the size of the metrics channel buffer.
	MetricsBufferSize int

	// MaxConcurrentCollections limits parallel adapter collections.
	MaxConcurrentCollections int

	// RetryOnFailure enables automatic reconnection on adapter failures.
	RetryOnFailure bool

	// RetryInterval is the interval between reconnection attempts.
	RetryInterval time.Duration
}

// DefaultManagerConfig returns sensible defaults for the manager.
func DefaultManagerConfig() ManagerConfig {
	return ManagerConfig{
		CollectInterval:          30 * time.Second,
		MetricsBufferSize:        10000,
		MaxConcurrentCollections: 10,
		RetryOnFailure:           true,
		RetryInterval:            30 * time.Second,
	}
}

// collectResult holds the result of a collection from an adapter.
type collectResult struct {
	adapterName string
	metrics     []protocol.Metric
	err         error
}

// NewManager creates a new adapter manager.
func NewManager(cfg ManagerConfig) *Manager {
	if cfg.MetricsBufferSize <= 0 {
		cfg.MetricsBufferSize = 10000
	}
	if cfg.MaxConcurrentCollections <= 0 {
		cfg.MaxConcurrentCollections = 10
	}

	return &Manager{
		adapters:       make(map[string]Adapter),
		metrics:        make(chan protocol.Metric, cfg.MetricsBufferSize),
		collectResults: make(chan collectResult, cfg.MaxConcurrentCollections),
		done:           make(chan struct{}),
		config:         cfg,
	}
}

// Register adds an adapter to the manager.
func (m *Manager) Register(adapter Adapter) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	name := adapter.Name()
	if _, exists := m.adapters[name]; exists {
		return fmt.Errorf("adapter %s already registered", name)
	}

	m.adapters[name] = adapter
	log.Printf("[Manager] Registered adapter: %s", name)
	return nil
}

// Unregister removes an adapter from the manager.
func (m *Manager) Unregister(name string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	adapter, exists := m.adapters[name]
	if !exists {
		return fmt.Errorf("adapter %s not found", name)
	}

	if err := adapter.Close(); err != nil {
		log.Printf("[Manager] Error closing adapter %s: %v", name, err)
	}

	delete(m.adapters, name)
	log.Printf("[Manager] Unregistered adapter: %s", name)
	return nil
}

// ConnectAll connects all registered adapters.
func (m *Manager) ConnectAll(ctx context.Context) error {
	m.mu.RLock()
	adapters := make([]Adapter, 0, len(m.adapters))
	for _, a := range m.adapters {
		adapters = append(adapters, a)
	}
	m.mu.RUnlock()

	var wg sync.WaitGroup
	errChan := make(chan error, len(adapters))

	for _, adapter := range adapters {
		wg.Add(1)
		go func(a Adapter) {
			defer wg.Done()
			if err := a.Connect(ctx); err != nil {
				errChan <- fmt.Errorf("adapter %s: %w", a.Name(), err)
			} else {
				log.Printf("[Manager] Connected adapter: %s", a.Name())
			}
		}(adapter)
	}

	wg.Wait()
	close(errChan)

	// Collect all errors
	var errors []error
	for err := range errChan {
		errors = append(errors, err)
	}

	if len(errors) > 0 {
		return fmt.Errorf("failed to connect %d adapters: %v", len(errors), errors)
	}

	return nil
}

// CloseAll closes all registered adapters.
func (m *Manager) CloseAll() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	var errors []error
	for name, adapter := range m.adapters {
		if err := adapter.Close(); err != nil {
			errors = append(errors, fmt.Errorf("%s: %w", name, err))
		}
	}

	if len(errors) > 0 {
		return fmt.Errorf("errors closing adapters: %v", errors)
	}

	return nil
}

// Start begins the metric collection loop.
func (m *Manager) Start(ctx context.Context) error {
	if err := m.ConnectAll(ctx); err != nil {
		log.Printf("[Manager] Warning: some adapters failed to connect: %v", err)
	}

	go m.collectionLoop(ctx)
	go m.resultProcessor(ctx)

	if m.config.RetryOnFailure {
		go m.reconnectionLoop(ctx)
	}

	log.Printf("[Manager] Started with %d adapters", len(m.adapters))
	return nil
}

// Stop stops the metric collection loop.
// Safe to call multiple times - subsequent calls are no-ops.
func (m *Manager) Stop() error {
	m.stopOnce.Do(func() {
		close(m.done)
	})
	return m.CloseAll()
}

// Metrics returns the channel for receiving collected metrics.
func (m *Manager) Metrics() <-chan protocol.Metric {
	return m.metrics
}

// CollectNow triggers an immediate collection from all adapters.
func (m *Manager) CollectNow(ctx context.Context) ([]protocol.Metric, error) {
	m.mu.RLock()
	adapters := make([]Adapter, 0, len(m.adapters))
	for _, a := range m.adapters {
		if a.IsConnected() {
			adapters = append(adapters, a)
		}
	}
	m.mu.RUnlock()

	var allMetrics []protocol.Metric
	var mu sync.Mutex
	var wg sync.WaitGroup

	sem := make(chan struct{}, m.config.MaxConcurrentCollections)

	for _, adapter := range adapters {
		wg.Add(1)
		go func(a Adapter) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			metrics, err := a.CollectMetrics(ctx)
			if err != nil {
				log.Printf("[Manager] Error collecting from %s: %v", a.Name(), err)
				return
			}

			mu.Lock()
			allMetrics = append(allMetrics, metrics...)
			mu.Unlock()
		}(adapter)
	}

	wg.Wait()
	return allMetrics, nil
}

// collectionLoop runs the periodic metric collection.
func (m *Manager) collectionLoop(ctx context.Context) {
	ticker := time.NewTicker(m.config.CollectInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-m.done:
			return
		case <-ticker.C:
			m.triggerCollection(ctx)
		}
	}
}

// triggerCollection starts collection from all connected adapters.
// Uses WaitGroup to track goroutines for graceful shutdown.
func (m *Manager) triggerCollection(ctx context.Context) {
	m.mu.RLock()
	adapters := make([]Adapter, 0, len(m.adapters))
	for _, a := range m.adapters {
		if a.IsConnected() {
			adapters = append(adapters, a)
		}
	}
	m.mu.RUnlock()

	if len(adapters) == 0 {
		return
	}

	var wg sync.WaitGroup
	sem := make(chan struct{}, m.config.MaxConcurrentCollections)

	for _, adapter := range adapters {
		wg.Add(1)
		sem <- struct{}{}
		go func(a Adapter) {
			defer wg.Done()
			defer func() { <-sem }()

			metrics, err := a.CollectMetrics(ctx)
			result := collectResult{
				adapterName: a.Name(),
				metrics:     metrics,
				err:         err,
			}

			select {
			case m.collectResults <- result:
			case <-ctx.Done():
			case <-m.done:
			}
		}(adapter)
	}

	// Wait for all collections to complete (with timeout)
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// All collections completed
	case <-ctx.Done():
		// Context cancelled - goroutines will exit on their own
	case <-m.done:
		// Manager stopping - goroutines will exit on their own
	}
}

// resultProcessor processes collection results and sends metrics to the channel.
func (m *Manager) resultProcessor(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case <-m.done:
			return
		case result := <-m.collectResults:
			if result.err != nil {
				log.Printf("[Manager] Collection error from %s: %v", result.adapterName, result.err)
				continue
			}

			for _, metric := range result.metrics {
				select {
				case m.metrics <- metric:
				default:
					// Channel full, drop oldest and retry
					select {
					case <-m.metrics:
					default:
					}
					select {
					case m.metrics <- metric:
					default:
					}
				}
			}

			if len(result.metrics) > 0 {
				log.Printf("[Manager] Collected %d metrics from %s", len(result.metrics), result.adapterName)
			}
		}
	}
}

// reconnectionLoop attempts to reconnect failed adapters.
func (m *Manager) reconnectionLoop(ctx context.Context) {
	ticker := time.NewTicker(m.config.RetryInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-m.done:
			return
		case <-ticker.C:
			m.reconnectDisconnected(ctx)
		}
	}
}

// reconnectDisconnected attempts to reconnect disconnected adapters.
func (m *Manager) reconnectDisconnected(ctx context.Context) {
	m.mu.RLock()
	disconnected := make([]Adapter, 0)
	for _, a := range m.adapters {
		if !a.IsConnected() {
			disconnected = append(disconnected, a)
		}
	}
	m.mu.RUnlock()

	for _, adapter := range disconnected {
		log.Printf("[Manager] Attempting to reconnect %s...", adapter.Name())
		if err := adapter.Connect(ctx); err != nil {
			log.Printf("[Manager] Reconnection failed for %s: %v", adapter.Name(), err)
		} else {
			log.Printf("[Manager] Reconnected %s", adapter.Name())
		}
	}
}

// GetAdapter returns an adapter by name.
func (m *Manager) GetAdapter(name string) (Adapter, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	adapter, ok := m.adapters[name]
	return adapter, ok
}

// ListAdapters returns the names of all registered adapters.
func (m *Manager) ListAdapters() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()

	names := make([]string, 0, len(m.adapters))
	for name := range m.adapters {
		names = append(names, name)
	}
	return names
}

// Status returns the connection status of all adapters.
func (m *Manager) Status() map[string]bool {
	m.mu.RLock()
	defer m.mu.RUnlock()

	status := make(map[string]bool, len(m.adapters))
	for name, adapter := range m.adapters {
		status[name] = adapter.IsConnected()
	}
	return status
}
