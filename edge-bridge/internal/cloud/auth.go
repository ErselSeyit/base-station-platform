package cloud

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
)

// Authentication errors
var (
	ErrAuthFailed     = errors.New("authentication failed")
	ErrTokenExpired   = errors.New("token expired")
	ErrNoToken        = errors.New("no authentication token")
	ErrRefreshFailed  = errors.New("token refresh failed")
)

// AuthConfig holds authentication configuration.
type AuthConfig struct {
	Username     string
	Password     string
	RefreshAhead time.Duration // Refresh token this much before expiry
}

// DefaultAuthConfig returns default authentication configuration.
func DefaultAuthConfig() *AuthConfig {
	return &AuthConfig{
		RefreshAhead: 5 * time.Minute,
	}
}

// Authenticator handles JWT authentication with the auth service.
type Authenticator struct {
	baseURL   string
	config    *AuthConfig
	client    *http.Client
	token     string
	tokenType string
	expiresAt time.Time
	mu        sync.RWMutex
}

// NewAuthenticator creates a new authenticator.
func NewAuthenticator(baseURL string, config *AuthConfig) *Authenticator {
	if config == nil {
		config = DefaultAuthConfig()
	}
	return &Authenticator{
		baseURL: baseURL,
		config:  config,
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// Login authenticates with the auth service and obtains a JWT token.
func (a *Authenticator) Login() error {
	url := fmt.Sprintf("%s/api/v1/auth/login", a.baseURL)

	reqBody := LoginRequest{
		Username: a.config.Username,
		Password: a.config.Password,
	}

	jsonBody, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("failed to marshal login request: %w", err)
	}

	req, err := http.NewRequest("POST", url, bytes.NewReader(jsonBody))
	if err != nil {
		return fmt.Errorf("failed to create login request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := a.client.Do(req)
	if err != nil {
		return fmt.Errorf("login request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read login response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if json.Unmarshal(body, &errResp) == nil {
			return fmt.Errorf("%w: %s", ErrAuthFailed, errResp.Message)
		}
		return fmt.Errorf("%w: status %d", ErrAuthFailed, resp.StatusCode)
	}

	var loginResp LoginResponse
	if err := json.Unmarshal(body, &loginResp); err != nil {
		return fmt.Errorf("failed to parse login response: %w", err)
	}

	a.mu.Lock()
	a.token = loginResp.Token
	a.tokenType = loginResp.Type
	if a.tokenType == "" {
		a.tokenType = "Bearer"
	}
	a.expiresAt = time.Now().Add(time.Duration(loginResp.ExpiresIn) * time.Second)
	a.mu.Unlock()

	return nil
}

// GetToken returns the current token, refreshing if necessary.
func (a *Authenticator) GetToken() (string, error) {
	a.mu.RLock()
	token := a.token
	expiresAt := a.expiresAt
	a.mu.RUnlock()

	if token == "" {
		return "", ErrNoToken
	}

	// Check if token needs refresh
	if time.Now().Add(a.config.RefreshAhead).After(expiresAt) {
		if err := a.Login(); err != nil {
			return "", fmt.Errorf("%w: %v", ErrRefreshFailed, err)
		}
		a.mu.RLock()
		token = a.token
		a.mu.RUnlock()
	}

	return token, nil
}

// GetAuthHeader returns the Authorization header value.
func (a *Authenticator) GetAuthHeader() (string, error) {
	token, err := a.GetToken()
	if err != nil {
		return "", err
	}

	a.mu.RLock()
	tokenType := a.tokenType
	a.mu.RUnlock()

	return fmt.Sprintf("%s %s", tokenType, token), nil
}

// IsAuthenticated returns true if we have a valid token.
func (a *Authenticator) IsAuthenticated() bool {
	a.mu.RLock()
	defer a.mu.RUnlock()

	if a.token == "" {
		return false
	}
	return time.Now().Before(a.expiresAt)
}

// Logout clears the authentication state.
func (a *Authenticator) Logout() {
	a.mu.Lock()
	a.token = ""
	a.tokenType = ""
	a.expiresAt = time.Time{}
	a.mu.Unlock()
}

// ExpiresIn returns the time until the token expires.
func (a *Authenticator) ExpiresIn() time.Duration {
	a.mu.RLock()
	defer a.mu.RUnlock()

	if a.token == "" {
		return 0
	}
	return time.Until(a.expiresAt)
}
