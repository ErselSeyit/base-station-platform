package bridge

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"sync"
	"time"

	"github.com/huawei/edge-bridge/internal/cloud"
	"github.com/huawei/edge-bridge/internal/config"
	"github.com/huawei/edge-bridge/internal/device"
	"github.com/huawei/edge-bridge/internal/protocol"
	"github.com/huawei/edge-bridge/internal/transport"
)

// Bridge orchestrates communication between device and cloud.
type Bridge struct {
	config      *config.Config
	transport   transport.Transport
	deviceMgr   *device.Manager
	cloudAuth   *cloud.Authenticator
	cloudClient *cloud.Client
	cmdExecutor *CommandExecutor

	stationDBID int64 // Database ID of registered station
	metrics     []protocol.Metric
	metricsLock sync.RWMutex

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
}

// New creates a new bridge instance.
func New(cfg *config.Config) (*Bridge, error) {
	// Create transport based on config
	var t transport.Transport
	switch cfg.Device.Transport {
	case "serial":
		t = transport.NewSerialTransport(cfg.Device.Serial.Port, cfg.Device.Serial.Baud)
	case "tcp":
		t = transport.NewTCPTransport(cfg.Device.TCP.Host, cfg.Device.TCP.Port)
	default:
		return nil, fmt.Errorf("unknown transport type: %s", cfg.Device.Transport)
	}

	// Create device manager
	deviceMgr := device.NewManager(t, nil)

	// Create cloud authenticator
	authConfig := &cloud.AuthConfig{
		Username: cfg.Cloud.Auth.Username,
		Password: cfg.Cloud.Auth.Password,
	}
	cloudAuth := cloud.NewAuthenticator(cfg.Cloud.BaseURL, authConfig)

	// Create cloud client
	clientConfig := &cloud.ClientConfig{
		BaseURL:       cfg.Cloud.BaseURL,
		Timeout:       cfg.Cloud.Timeout,
		RetryAttempts: cfg.Cloud.RetryAttempts,
	}
	cloudClient := cloud.NewClient(clientConfig, cloudAuth)

	// Create command executor
	cmdExecutor := NewCommandExecutor(deviceMgr, cloudClient, cfg.Bridge.StationID)

	return &Bridge{
		config:      cfg,
		transport:   t,
		deviceMgr:   deviceMgr,
		cloudAuth:   cloudAuth,
		cloudClient: cloudClient,
		cmdExecutor: cmdExecutor,
	}, nil
}

// Start starts the bridge.
func (b *Bridge) Start(ctx context.Context) error {
	b.ctx, b.cancel = context.WithCancel(ctx)

	// Setup metric handler
	b.deviceMgr.Handler().OnMetrics(func(metrics []protocol.Metric) {
		b.metricsLock.Lock()
		b.metrics = metrics
		b.metricsLock.Unlock()
	})

	// Setup alert handler
	b.deviceMgr.Handler().OnAlert(func(msgType protocol.MessageType, payload []byte) {
		b.handleAlert(msgType, payload)
	})

	// Connect to device
	log.Printf("Connecting to device via %s...", b.config.Device.Transport)
	if err := b.deviceMgr.Start(b.ctx); err != nil {
		return fmt.Errorf("failed to start device manager: %w", err)
	}

	// Authenticate with cloud
	log.Printf("Authenticating with cloud...")
	if err := b.cloudAuth.Login(); err != nil {
		log.Printf("Warning: Cloud authentication failed: %v", err)
		// Continue without cloud - will retry later
	} else {
		log.Printf("Cloud authentication successful")
		// Register this station with the cloud
		b.registerStation()
	}

	// Start metrics collection loop
	b.wg.Add(1)
	go b.metricsLoop()

	// Start command polling loop
	b.wg.Add(1)
	go b.commandLoop()

	log.Printf("Bridge started for station %s", b.config.Bridge.StationID)
	return nil
}

// shutdownTimeout is the maximum time to wait for graceful shutdown.
const shutdownTimeout = 10 * time.Second

// Stop stops the bridge gracefully with timeout.
func (b *Bridge) Stop() error {
	log.Printf("Stopping bridge...")

	if b.cancel != nil {
		b.cancel()
	}

	// Wait for goroutines with timeout
	done := make(chan struct{})
	go func() {
		b.wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Clean shutdown completed
	case <-time.After(shutdownTimeout):
		log.Printf("Warning: graceful shutdown timeout exceeded (%v)", shutdownTimeout)
	}

	if err := b.deviceMgr.Stop(); err != nil {
		log.Printf("Error stopping device manager: %v", err)
	}

	log.Printf("Bridge stopped")
	return nil
}

func (b *Bridge) metricsLoop() {
	defer b.wg.Done()

	ticker := time.NewTicker(b.config.Bridge.MetricsInterval)
	defer ticker.Stop()

	for {
		select {
		case <-b.ctx.Done():
			return
		case <-ticker.C:
			b.collectAndUploadMetrics()
		}
	}
}

func (b *Bridge) collectAndUploadMetrics() {
	// Request metrics from device
	metrics, err := b.deviceMgr.RequestMetrics(nil)
	if err != nil {
		log.Printf("Failed to collect metrics: %v", err)
		return
	}

	log.Printf("Collected %d metrics from device", len(metrics))

	// Store locally
	b.metricsLock.Lock()
	b.metrics = metrics
	b.metricsLock.Unlock()

	// Upload to cloud
	if !b.cloudAuth.IsAuthenticated() {
		if err := b.cloudAuth.Login(); err != nil {
			log.Printf("Cloud authentication failed, skipping upload")
			return
		}
	}

	// Filter and convert metrics to cloud format
	cloudMetrics := make([]cloud.MetricData, 0, len(metrics))
	for _, m := range metrics {
		typeStr := protocol.MetricTypeString(m.Type)
		if typeStr == "" {
			continue // Skip unsupported metric types
		}
		cloudMetrics = append(cloudMetrics, cloud.MetricData{
			Type:      typeStr,
			Value:     float64(m.Value),
			Timestamp: time.Now(),
		})
	}

	if len(cloudMetrics) == 0 {
		log.Printf("No supported metrics to upload")
		return
	}

	// Use database ID if available, otherwise fall back to config station ID
	stationID := b.config.Bridge.StationID
	if b.stationDBID > 0 {
		stationID = strconv.FormatInt(b.stationDBID, 10)
	}

	resp, err := b.cloudClient.UploadMetrics(stationID, cloudMetrics)
	if err != nil {
		log.Printf("Failed to upload metrics: %v", err)
		return
	}

	log.Printf("Uploaded %d metrics to cloud (status: %s)", resp.Received, resp.Status)
}

func (b *Bridge) commandLoop() {
	defer b.wg.Done()

	ticker := time.NewTicker(b.config.Bridge.PollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-b.ctx.Done():
			return
		case <-ticker.C:
			if !b.cloudAuth.IsAuthenticated() {
				continue
			}
			if err := b.cmdExecutor.ProcessPendingCommands(); err != nil {
				log.Printf("Error processing commands: %v", err)
			}
		}
	}
}

func (b *Bridge) handleAlert(msgType protocol.MessageType, payload []byte) {
	alertType := "UNKNOWN"
	severity := "INFO"

	switch msgType {
	case protocol.MsgThresholdExceeded:
		alertType = "THRESHOLD_EXCEEDED"
		severity = "WARNING"
	case protocol.MsgDeviceStateChange:
		alertType = "STATE_CHANGE"
		severity = "INFO"
	case protocol.MsgError:
		alertType = "ERROR"
		severity = "ERROR"
	}

	alert := &cloud.AlertEvent{
		StationID: b.config.Bridge.StationID,
		Type:      alertType,
		Severity:  severity,
		Message:   string(payload),
		Timestamp: time.Now(),
	}

	if b.cloudAuth.IsAuthenticated() {
		if _, err := b.cloudClient.SendAlert(alert); err != nil {
			log.Printf("Failed to send alert: %v", err)
		}
	}
}

// GetMetrics returns the latest collected metrics.
func (b *Bridge) GetMetrics() []protocol.Metric {
	b.metricsLock.RLock()
	defer b.metricsLock.RUnlock()
	result := make([]protocol.Metric, len(b.metrics))
	copy(result, b.metrics)
	return result
}

// GetDeviceStatus returns the current device status.
func (b *Bridge) GetDeviceStatus() (*protocol.StatusPayload, error) {
	return b.deviceMgr.RequestStatus()
}

// IsDeviceConnected returns true if connected to the device.
func (b *Bridge) IsDeviceConnected() bool {
	return b.deviceMgr.IsConnected()
}

// IsCloudConnected returns true if authenticated with the cloud.
func (b *Bridge) IsCloudConnected() bool {
	return b.cloudAuth.IsAuthenticated()
}

// StationID returns the configured station ID.
func (b *Bridge) StationID() string {
	return b.config.Bridge.StationID
}

// registerStation registers or updates this station in the cloud.
func (b *Bridge) registerStation() {
	cfg := b.config.Bridge

	// First try to find existing station by name
	existing, err := b.cloudClient.GetBaseStationByName(cfg.StationName)
	if err == nil && existing != nil {
		b.stationDBID = existing.ID
		log.Printf("Station already registered: %s (ID: %d)", existing.StationName, existing.ID)
		return
	}

	// Register new station
	req := &cloud.CreateStationRequest{
		StationName:  cfg.StationName,
		Location:     cfg.Location,
		Latitude:     cfg.Latitude,
		Longitude:    cfg.Longitude,
		StationType:  cfg.StationType,
		Status:       "ACTIVE",
		Description:  cfg.Description,
	}

	station, err := b.cloudClient.RegisterStation(req)
	if err != nil {
		// If registration failed (e.g., already exists), try to find the station again
		log.Printf("Registration attempt failed: %v, trying to find existing station", err)
		existing, findErr := b.cloudClient.GetBaseStationByName(cfg.StationName)
		if findErr == nil && existing != nil {
			b.stationDBID = existing.ID
			log.Printf("Found existing station: %s (ID: %d)", existing.StationName, existing.ID)
			return
		}
		log.Printf("Warning: Failed to register or find station: %v", err)
		return
	}

	b.stationDBID = station.ID
	log.Printf("Station registered: %s (ID: %d)", station.StationName, station.ID)
}
