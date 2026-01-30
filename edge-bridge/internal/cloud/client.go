package cloud

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// ClientConfig holds cloud client configuration.
type ClientConfig struct {
	BaseURL       string
	Timeout       time.Duration
	RetryAttempts int
	RetryDelay    time.Duration
}

// DefaultClientConfig returns default client configuration.
func DefaultClientConfig() *ClientConfig {
	return &ClientConfig{
		Timeout:       30 * time.Second,
		RetryAttempts: 3,
		RetryDelay:    1 * time.Second,
	}
}

// Client provides access to the Java backend REST APIs.
type Client struct {
	config *ClientConfig
	auth   *Authenticator
	http   *http.Client
}

// NewClient creates a new cloud client.
func NewClient(config *ClientConfig, auth *Authenticator) *Client {
	if config == nil {
		config = DefaultClientConfig()
	}
	return &Client{
		config: config,
		auth:   auth,
		http: &http.Client{
			Timeout: config.Timeout,
		},
	}
}

// doRequest performs an authenticated HTTP request with retries.
func (c *Client) doRequest(method, url string, body interface{}, result interface{}) error {
	var lastErr error

	for attempt := 0; attempt <= c.config.RetryAttempts; attempt++ {
		if attempt > 0 {
			time.Sleep(c.config.RetryDelay)
		}

		err := c.doRequestOnce(method, url, body, result)
		if err == nil {
			return nil
		}

		lastErr = err

		// Don't retry on auth errors
		if err == ErrAuthFailed || err == ErrNoToken {
			return err
		}
	}

	return fmt.Errorf("request failed after %d attempts: %w", c.config.RetryAttempts+1, lastErr)
}

// marshalBody converts body to JSON reader, returns nil reader if body is nil.
func marshalBody(body interface{}) (io.Reader, error) {
	if body == nil {
		return nil, nil
	}
	jsonBody, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request body: %w", err)
	}
	return bytes.NewReader(jsonBody), nil
}

// handleErrorResponse parses error response and returns appropriate error.
func handleErrorResponse(statusCode int, respBody []byte) error {
	var errResp ErrorResponse
	if json.Unmarshal(respBody, &errResp) == nil && errResp.Message != "" {
		return fmt.Errorf("API error %d: %s", statusCode, errResp.Message)
	}
	return fmt.Errorf("API error: status %d", statusCode)
}

// parseResponse unmarshals response body into result if provided.
func parseResponse(respBody []byte, result interface{}) error {
	if result == nil || len(respBody) == 0 {
		return nil
	}
	if err := json.Unmarshal(respBody, result); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}
	return nil
}

func (c *Client) doRequestOnce(method, url string, body interface{}, result interface{}) error {
	bodyReader, err := marshalBody(body)
	if err != nil {
		return err
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	authHeader, err := c.auth.GetAuthHeader()
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", authHeader)

	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode == http.StatusUnauthorized {
		if loginErr := c.auth.Login(); loginErr != nil {
			return ErrAuthFailed
		}
		return fmt.Errorf("authentication required, please retry")
	}

	if resp.StatusCode >= 400 {
		return handleErrorResponse(resp.StatusCode, respBody)
	}

	return parseResponse(respBody, result)
}

// UploadMetrics uploads a batch of metrics to the monitoring service.
func (c *Client) UploadMetrics(stationID string, metrics []MetricData) (*MetricsBatchResponse, error) {
	url := fmt.Sprintf("%s/api/v1/metrics/batch", c.config.BaseURL)

	req := MetricsBatchRequest{
		StationID: stationID,
		Metrics:   metrics,
	}

	var resp MetricsBatchResponse
	if err := c.doRequest("POST", url, req, &resp); err != nil {
		return nil, err
	}

	return &resp, nil
}

// UpdateStatus updates the device status.
func (c *Client) UpdateStatus(stationID string, status *StatusUpdateRequest) (*StatusUpdateResponse, error) {
	url := fmt.Sprintf("%s/api/base-stations/%s/status", c.config.BaseURL, stationID)

	var resp StatusUpdateResponse
	if err := c.doRequest("PUT", url, status, &resp); err != nil {
		return nil, err
	}

	return &resp, nil
}

// GetPendingCommands retrieves pending commands for execution.
func (c *Client) GetPendingCommands(stationID string) ([]PendingCommand, error) {
	url := fmt.Sprintf("%s/api/base-stations/%s/commands/pending", c.config.BaseURL, stationID)

	var commands []PendingCommand
	if err := c.doRequest("GET", url, nil, &commands); err != nil {
		return nil, err
	}

	return commands, nil
}

// ReportCommandResult reports the result of a command execution.
func (c *Client) ReportCommandResult(stationID, commandID string, result *CommandResultRequest) (*CommandResultResponse, error) {
	url := fmt.Sprintf("%s/api/base-stations/%s/commands/%s/result", c.config.BaseURL, stationID, commandID)

	var resp CommandResultResponse
	if err := c.doRequest("POST", url, result, &resp); err != nil {
		return nil, err
	}

	return &resp, nil
}

// SendAlert sends an alert event to the cloud.
func (c *Client) SendAlert(alert *AlertEvent) (*AlertResponse, error) {
	url := fmt.Sprintf("%s/api/alerts", c.config.BaseURL)

	var resp AlertResponse
	if err := c.doRequest("POST", url, alert, &resp); err != nil {
		return nil, err
	}

	return &resp, nil
}

// GetBaseStation retrieves base station information.
func (c *Client) GetBaseStation(stationID string) (*BaseStation, error) {
	url := fmt.Sprintf("%s/api/v1/stations/%s", c.config.BaseURL, stationID)

	var station BaseStation
	if err := c.doRequest("GET", url, nil, &station); err != nil {
		return nil, err
	}

	return &station, nil
}

// GetBaseStationByName retrieves a base station by name.
func (c *Client) GetBaseStationByName(stationName string) (*BaseStation, error) {
	// List all stations and find by name (API doesn't support name filter)
	url := fmt.Sprintf("%s/api/v1/stations", c.config.BaseURL)

	var stations []BaseStation
	if err := c.doRequest("GET", url, nil, &stations); err != nil {
		return nil, err
	}

	for i := range stations {
		if stations[i].StationName == stationName {
			return &stations[i], nil
		}
	}

	return nil, fmt.Errorf("station not found: %s", stationName)
}

// RegisterStation registers a new base station or returns existing one.
func (c *Client) RegisterStation(req *CreateStationRequest) (*BaseStation, error) {
	url := fmt.Sprintf("%s/api/v1/stations", c.config.BaseURL)

	var station BaseStation
	if err := c.doRequest("POST", url, req, &station); err != nil {
		return nil, err
	}

	return &station, nil
}

// RequestDiagnostic requests AI diagnostic analysis.
func (c *Client) RequestDiagnostic(req *DiagnosticRequest) (*DiagnosticResponse, error) {
	url := fmt.Sprintf("%s/api/ai/diagnose", c.config.BaseURL)

	var resp DiagnosticResponse
	if err := c.doRequest("POST", url, req, &resp); err != nil {
		return nil, err
	}

	return &resp, nil
}

// Ping tests connectivity to the cloud.
func (c *Client) Ping() error {
	url := fmt.Sprintf("%s/actuator/health", c.config.BaseURL)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	// Drain and close body to allow connection reuse
	defer func() {
		_, _ = io.Copy(io.Discard, resp.Body)
		resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("health check failed: status %d", resp.StatusCode)
	}

	return nil
}

// IsConnected returns true if the client can reach the cloud.
func (c *Client) IsConnected() bool {
	return c.Ping() == nil
}
