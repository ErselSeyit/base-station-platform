package cloud

import (
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

// loginServer stands in for the auth service. It records how many login calls
// it received so tests can assert on refresh behaviour.
func loginServer(t *testing.T, handler func(w http.ResponseWriter, r *http.Request)) (*httptest.Server, *int) {
	t.Helper()
	calls := 0
	var mu sync.Mutex
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		calls++
		mu.Unlock()
		handler(w, r)
	}))
	t.Cleanup(srv.Close)
	return srv, &calls
}

func okLogin(token string, expiresIn int64) func(http.ResponseWriter, *http.Request) {
	return func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(LoginResponse{
			Token:     token,
			Type:      "Bearer",
			ExpiresIn: expiresIn,
		})
	}
}

func authConfig() *AuthConfig {
	return &AuthConfig{Username: "edge", Password: "secret", RefreshAhead: time.Minute}
}

func TestLoginStoresTheToken(t *testing.T) {
	srv, _ := loginServer(t, okLogin("token-1", 3600))
	a := NewAuthenticator(srv.URL, authConfig())

	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}
	if !a.IsAuthenticated() {
		t.Error("expected the authenticator to report an authenticated state")
	}

	header, err := a.GetAuthHeader()
	if err != nil {
		t.Fatalf("GetAuthHeader returned an error: %v", err)
	}
	if header != "Bearer token-1" {
		t.Errorf("Authorization header = %q, want %q", header, "Bearer token-1")
	}
}

func TestLoginPostsTheConfiguredCredentials(t *testing.T) {
	var got LoginRequest
	srv, _ := loginServer(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("method = %s, want POST", r.Method)
		}
		if !strings.HasSuffix(r.URL.Path, "/api/v1/auth/login") {
			t.Errorf("path = %s, want the auth login endpoint", r.URL.Path)
		}
		_ = json.NewDecoder(r.Body).Decode(&got)
		okLogin("t", 3600)(w, r)
	})

	if err := NewAuthenticator(srv.URL, authConfig()).Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}
	if got.Username != "edge" || got.Password != "secret" {
		t.Errorf("credentials sent = %q/%q, want edge/secret", got.Username, got.Password)
	}
}

func TestLoginReportsRejectedCredentials(t *testing.T) {
	srv, _ := loginServer(t, func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_ = json.NewEncoder(w).Encode(ErrorResponse{Message: "bad credentials"})
	})
	a := NewAuthenticator(srv.URL, authConfig())

	err := a.Login()
	if err == nil {
		t.Fatal("expected an error for rejected credentials")
	}
	// Callers branch on this sentinel, so it must survive wrapping.
	if !errors.Is(err, ErrAuthFailed) {
		t.Errorf("error %v does not wrap ErrAuthFailed", err)
	}
	if a.IsAuthenticated() {
		t.Error("a failed login must not leave the authenticator authenticated")
	}
}

func TestLoginDefaultsExpiryWhenTheServerOmitsIt(t *testing.T) {
	srv, _ := loginServer(t, okLogin("token-1", 0))
	a := NewAuthenticator(srv.URL, authConfig())

	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}
	// Without a fallback the token would be treated as already expired and
	// every request would trigger a fresh login.
	if !a.IsAuthenticated() {
		t.Fatal("expected a default expiry to keep the token usable")
	}
	if a.ExpiresIn() <= 0 {
		t.Errorf("ExpiresIn = %v, want a positive duration", a.ExpiresIn())
	}
}

func TestLoginDefaultsTokenTypeToBearer(t *testing.T) {
	srv, _ := loginServer(t, func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(LoginResponse{Token: "token-1", ExpiresIn: 3600})
	})
	a := NewAuthenticator(srv.URL, authConfig())

	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}
	header, err := a.GetAuthHeader()
	if err != nil {
		t.Fatalf("GetAuthHeader returned an error: %v", err)
	}
	if header != "Bearer token-1" {
		t.Errorf("Authorization header = %q, want a Bearer prefix", header)
	}
}

func TestGetTokenRequiresAnExplicitLoginFirst(t *testing.T) {
	srv, calls := loginServer(t, okLogin("token-1", 3600))
	a := NewAuthenticator(srv.URL, authConfig())

	// GetToken refreshes an existing token but does not perform the initial
	// login; that is the caller's job.
	if _, err := a.GetToken(); !errors.Is(err, ErrNoToken) {
		t.Fatalf("GetToken before Login = %v, want ErrNoToken", err)
	}
	if *calls != 0 {
		t.Errorf("login calls = %d, want 0 — GetToken must not authenticate implicitly", *calls)
	}

	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}
	token, err := a.GetToken()
	if err != nil {
		t.Fatalf("GetToken returned an error: %v", err)
	}
	if token != "token-1" {
		t.Errorf("token = %q, want token-1", token)
	}
}

func TestGetTokenReusesAValidToken(t *testing.T) {
	srv, calls := loginServer(t, okLogin("token-1", 3600))
	a := NewAuthenticator(srv.URL, authConfig())
	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}

	for i := 0; i < 3; i++ {
		if _, err := a.GetToken(); err != nil {
			t.Fatalf("GetToken returned an error: %v", err)
		}
	}
	// Re-authenticating on every call would hammer the auth service.
	if *calls != 1 {
		t.Errorf("login calls = %d, want 1 — the cached token should be reused", *calls)
	}
}

func TestGetTokenRefreshesBeforeExpiry(t *testing.T) {
	// The token outlives RefreshAhead by only a moment, so the next GetToken
	// must treat it as due for refresh rather than hand back a token that is
	// about to stop working mid-request.
	srv, calls := loginServer(t, okLogin("token-1", 30))
	a := NewAuthenticator(srv.URL, &AuthConfig{
		Username:     "edge",
		Password:     "secret",
		RefreshAhead: time.Minute,
	})
	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}

	if _, err := a.GetToken(); err != nil {
		t.Fatalf("GetToken returned an error: %v", err)
	}
	if _, err := a.GetToken(); err != nil {
		t.Fatalf("GetToken returned an error: %v", err)
	}

	if *calls < 2 {
		t.Errorf("login calls = %d, want at least 2 — a token inside the refresh window should be renewed", *calls)
	}
}

func TestLogoutClearsTheSession(t *testing.T) {
	srv, _ := loginServer(t, okLogin("token-1", 3600))
	a := NewAuthenticator(srv.URL, authConfig())

	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}
	a.Logout()

	if a.IsAuthenticated() {
		t.Error("expected Logout to clear the authenticated state")
	}
	if a.ExpiresIn() != 0 {
		t.Errorf("ExpiresIn = %v, want 0 after logout", a.ExpiresIn())
	}
}

func TestIsAuthenticatedIsFalseBeforeLogin(t *testing.T) {
	if NewAuthenticator("http://example.invalid", authConfig()).IsAuthenticated() {
		t.Error("a fresh authenticator must not report an authenticated state")
	}
}

func TestNewAuthenticatorFallsBackToDefaultConfig(t *testing.T) {
	a := NewAuthenticator("http://example.invalid", nil)
	if a == nil {
		t.Fatal("expected an authenticator even without an explicit config")
	}
	if a.IsAuthenticated() {
		t.Error("a fresh authenticator must not report an authenticated state")
	}
}

func TestConcurrentGetTokenIsSafe(t *testing.T) {
	srv, _ := loginServer(t, okLogin("token-1", 3600))
	a := NewAuthenticator(srv.URL, authConfig())
	if err := a.Login(); err != nil {
		t.Fatalf("Login returned an error: %v", err)
	}

	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, err := a.GetToken(); err != nil {
				t.Errorf("GetToken returned an error: %v", err)
			}
		}()
	}
	wg.Wait()
}
