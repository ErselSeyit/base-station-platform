// Package cloud provides REST API client for the Java backend.
package cloud

import "time"

// LoginRequest represents the authentication request.
type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// LoginResponse represents the authentication response.
type LoginResponse struct {
	Token     string `json:"token"`
	Type      string `json:"type"`
	ExpiresIn int64  `json:"expiresIn"` // seconds
}

// MetricData represents a single metric for upload.
type MetricData struct {
	Type      string    `json:"type"`
	Value     float64   `json:"value"`
	Timestamp time.Time `json:"timestamp"`
}

// MetricsBatchRequest represents a batch metrics upload request.
type MetricsBatchRequest struct {
	StationID string       `json:"stationId"`
	Metrics   []MetricData `json:"metrics"`
}

// MetricsBatchResponse represents the batch upload response.
type MetricsBatchResponse struct {
	Received int    `json:"received"`
	Status   string `json:"status"`
}

// StatusUpdateRequest represents a device status update.
type StatusUpdateRequest struct {
	Status   string `json:"status"`
	Uptime   int64  `json:"uptime"`
	Errors   int    `json:"errors"`
	Warnings int    `json:"warnings"`
}

// StatusUpdateResponse represents the status update response.
type StatusUpdateResponse struct {
	Status  string `json:"status"`
	Message string `json:"message,omitempty"`
}

// PendingCommand represents a command waiting to be executed.
type PendingCommand struct {
	ID        string                 `json:"id"`
	Type      string                 `json:"type"`
	Params    map[string]interface{} `json:"params,omitempty"`
	CreatedAt time.Time              `json:"createdAt,omitempty"`
}

// CommandResultRequest represents the result of command execution.
type CommandResultRequest struct {
	Success    bool   `json:"success"`
	Output     string `json:"output,omitempty"`
	ReturnCode int    `json:"returnCode,omitempty"`
	Error      string `json:"error,omitempty"`
}

// CommandResultResponse represents the command result acknowledgment.
type CommandResultResponse struct {
	Status  string `json:"status"`
	Message string `json:"message,omitempty"`
}

// BaseStation represents a base station from the API.
type BaseStation struct {
	ID       string  `json:"id"`
	Name     string  `json:"name"`
	Location string  `json:"location"`
	Status   string  `json:"status"`
	Lat      float64 `json:"lat,omitempty"`
	Lng      float64 `json:"lng,omitempty"`
}

// AlertEvent represents an alert to send to the cloud.
type AlertEvent struct {
	StationID   string    `json:"stationId"`
	Type        string    `json:"type"`
	Severity    string    `json:"severity"`
	Message     string    `json:"message"`
	MetricType  string    `json:"metricType,omitempty"`
	MetricValue float64   `json:"metricValue,omitempty"`
	Threshold   float64   `json:"threshold,omitempty"`
	Timestamp   time.Time `json:"timestamp"`
}

// AlertResponse represents the alert acknowledgment.
type AlertResponse struct {
	AlertID string `json:"alertId"`
	Status  string `json:"status"`
}

// DiagnosticRequest represents a diagnostic request.
type DiagnosticRequest struct {
	StationID   string            `json:"station_id"`
	Description string            `json:"description"`
	Metrics     map[string]string `json:"metrics,omitempty"`
}

// DiagnosticResponse represents a diagnostic analysis result.
type DiagnosticResponse struct {
	Diagnosis      string   `json:"diagnosis"`
	Severity       string   `json:"severity"`
	Recommendations []string `json:"recommendations"`
}

// ErrorResponse represents an API error response.
type ErrorResponse struct {
	Status    int    `json:"status"`
	Error     string `json:"error"`
	Message   string `json:"message"`
	Path      string `json:"path,omitempty"`
	Timestamp string `json:"timestamp,omitempty"`
}
