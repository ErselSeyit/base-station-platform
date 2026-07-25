package cloud

import (
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestIsIdempotent(t *testing.T) {
	for _, m := range []string{http.MethodGet, http.MethodPut, http.MethodDelete, http.MethodHead} {
		if !isIdempotent(m) {
			t.Errorf("%s should be idempotent", m)
		}
	}
	for _, m := range []string{http.MethodPost, http.MethodPatch} {
		if isIdempotent(m) {
			t.Errorf("%s should not be idempotent", m)
		}
	}
}

// A server that always 500s (except login), counting target hits.
func retryServer(t *testing.T) (*httptest.Server, *int32) {
	t.Helper()
	var hits int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/v1/auth/login" {
			okLogin("tkn", 3600)(w, r)
			return
		}
		atomic.AddInt32(&hits, 1)
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"message":"boom"}`))
	}))
	t.Cleanup(srv.Close)
	return srv, &hits
}

func newRetryClient(srv *httptest.Server) *Client {
	auth := NewAuthenticator(srv.URL, authConfig())
	_ = auth.Login()
	return NewClient(&ClientConfig{BaseURL: srv.URL, Timeout: 2 * time.Second, RetryAttempts: 2, RetryDelay: time.Nanosecond}, auth)
}

func TestPostIsNotRetriedAfterAResponse(t *testing.T) {
	srv, hits := retryServer(t)
	c := newRetryClient(srv)

	// POST that gets a 500 must be attempted exactly once (retrying could
	// double-apply the write).
	_ = c.doRequest(http.MethodPost, srv.URL+"/api/v1/metrics/batch", map[string]string{"a": "b"}, nil)

	if got := atomic.LoadInt32(hits); got != 1 {
		t.Fatalf("POST hit the server %d times, want 1 (no retry after a response)", got)
	}
}

func TestGetIsRetried(t *testing.T) {
	srv, hits := retryServer(t)
	c := newRetryClient(srv)

	// GET is idempotent, so a 500 is retried: 1 initial + 2 retries.
	_ = c.doRequest(http.MethodGet, srv.URL+"/api/v1/metrics", nil, nil)

	if got := atomic.LoadInt32(hits); got != 3 {
		t.Fatalf("GET hit the server %d times, want 3 (1 + RetryAttempts)", got)
	}
}
