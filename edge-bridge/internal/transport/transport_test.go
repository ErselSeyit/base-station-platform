package transport

import (
	"errors"
	"io"
	"testing"
	"time"
)

// fakeTransport is a Transport whose Receive hands back a scripted sequence of
// (bytes, error) responses, so BufferedTransport's buffering logic can be
// exercised without any real I/O. Preferring a fake over a mock keeps the test
// coupled to observable behaviour rather than call sequences.
type fakeTransport struct {
	responses []fakeResponse
	callIndex int
	open      bool
}

type fakeResponse struct {
	data []byte
	err  error
}

func (f *fakeTransport) Open() error  { f.open = true; return nil }
func (f *fakeTransport) Close() error { f.open = false; return nil }
func (f *fakeTransport) IsOpen() bool { return f.open }
func (f *fakeTransport) Send(data []byte) (int, error) {
	return len(data), nil
}
func (f *fakeTransport) SetReadTimeout(time.Duration) {}
func (f *fakeTransport) Type() string                 { return "fake" }

func (f *fakeTransport) Receive(buffer []byte, _ time.Duration) (int, error) {
	if f.callIndex >= len(f.responses) {
		return 0, io.EOF
	}
	r := f.responses[f.callIndex]
	f.callIndex++
	if r.err != nil {
		return 0, r.err
	}
	n := copy(buffer, r.data)
	return n, nil
}

func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()
	if cfg.ReadTimeout != 5*time.Second {
		t.Errorf("ReadTimeout = %v, want 5s", cfg.ReadTimeout)
	}
	if cfg.WriteTimeout != 5*time.Second {
		t.Errorf("WriteTimeout = %v, want 5s", cfg.WriteTimeout)
	}
	if cfg.RetryCount != 3 {
		t.Errorf("RetryCount = %d, want 3", cfg.RetryCount)
	}
	if cfg.RetryDelay != 100*time.Millisecond {
		t.Errorf("RetryDelay = %v, want 100ms", cfg.RetryDelay)
	}
}

func TestReadByteWithTimeoutDrainsBufferBeforeRefilling(t *testing.T) {
	ft := &fakeTransport{responses: []fakeResponse{{data: []byte{0x01, 0x02}}}}
	bt := NewBufferedTransport(ft, 16)

	for _, want := range []byte{0x01, 0x02} {
		got, err := bt.ReadByteWithTimeout(time.Second)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if got != want {
			t.Errorf("got byte %#x, want %#x", got, want)
		}
	}

	// A single Receive of two bytes must serve both reads.
	if ft.callIndex != 1 {
		t.Errorf("Receive called %d times, want 1", ft.callIndex)
	}
}

func TestReadByteWithTimeoutReturnsEOFOnZeroBytes(t *testing.T) {
	ft := &fakeTransport{responses: []fakeResponse{{data: []byte{}}}}
	bt := NewBufferedTransport(ft, 16)

	if _, err := bt.ReadByteWithTimeout(time.Second); !errors.Is(err, io.EOF) {
		t.Errorf("error = %v, want io.EOF", err)
	}
}

func TestReadByteWithTimeoutPropagatesReceiveError(t *testing.T) {
	ft := &fakeTransport{responses: []fakeResponse{{err: ErrNotConnected}}}
	bt := NewBufferedTransport(ft, 16)

	if _, err := bt.ReadByteWithTimeout(time.Second); !errors.Is(err, ErrNotConnected) {
		t.Errorf("error = %v, want ErrNotConnected", err)
	}
}

func TestReadBytesReadsExactCountAcrossRefills(t *testing.T) {
	ft := &fakeTransport{responses: []fakeResponse{
		{data: []byte{0x01, 0x02}},
		{data: []byte{0x03, 0x04}},
	}}
	bt := NewBufferedTransport(ft, 16)

	got, err := bt.ReadBytes(4, time.Second)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := []byte{0x01, 0x02, 0x03, 0x04}
	if string(got) != string(want) {
		t.Errorf("got %v, want %v", got, want)
	}
}

func TestReadBytesReturnsPartialDataOnError(t *testing.T) {
	ft := &fakeTransport{responses: []fakeResponse{
		{data: []byte{0x01}},
		{err: ErrClosed},
	}}
	bt := NewBufferedTransport(ft, 16)

	got, err := bt.ReadBytes(4, time.Second)
	if !errors.Is(err, ErrClosed) {
		t.Errorf("error = %v, want ErrClosed", err)
	}
	if string(got) != string([]byte{0x01}) {
		t.Errorf("partial data = %v, want [1]", got)
	}
}

func TestReadBytesTimesOutWithNoData(t *testing.T) {
	ft := &fakeTransport{} // no responses; but timeout fires first
	bt := NewBufferedTransport(ft, 16)

	got, err := bt.ReadBytes(4, time.Nanosecond)
	if !errors.Is(err, ErrTimeout) {
		t.Errorf("error = %v, want ErrTimeout", err)
	}
	if got != nil {
		t.Errorf("data = %v, want nil", got)
	}
}

func TestUnderlyingReturnsWrappedTransport(t *testing.T) {
	ft := &fakeTransport{}
	bt := NewBufferedTransport(ft, 16)
	if bt.Underlying() != ft {
		t.Error("Underlying did not return the wrapped transport")
	}
}
