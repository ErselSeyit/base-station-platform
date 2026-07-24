package device

import (
	"sync"
	"testing"
	"time"

	"edge-bridge/internal/protocol"
)

// ---- MessageHandler ----

func TestHandleMessageRoutesMetricsToTheMetricsCallback(t *testing.T) {
	h := NewMessageHandler()

	var got []protocol.Metric
	h.OnMetrics(func(m []protocol.Metric) { got = m })

	want := []protocol.Metric{{Type: protocol.MetricType(1), Value: 42.5}}
	h.HandleMessage(&protocol.Message{
		Type:    protocol.MsgMetricsResponse,
		Payload: protocol.EncodeMetrics(want),
	})

	if len(got) != len(want) {
		t.Fatalf("received %d metrics, want %d", len(got), len(want))
	}
	if got[0].Type != want[0].Type || got[0].Value != want[0].Value {
		t.Errorf("metric = {%v, %v}, want {%v, %v}", got[0].Type, got[0].Value, want[0].Type, want[0].Value)
	}
}

func TestHandleMessageRoutesStatusToTheStatusCallback(t *testing.T) {
	h := NewMessageHandler()

	var got *protocol.StatusPayload
	h.OnStatus(func(s *protocol.StatusPayload) { got = s })

	want := &protocol.StatusPayload{Status: protocol.StatusCode(2), Uptime: 4242, Errors: 1, Warnings: 3}
	h.HandleMessage(&protocol.Message{
		Type:    protocol.MsgStatusResponse,
		Payload: protocol.EncodeStatus(want),
	})

	if got == nil {
		t.Fatal("status callback was not invoked")
	}
	if *got != *want {
		t.Errorf("status = %+v, want %+v", *got, *want)
	}
}

func TestHandleMessageReportsAMalformedPayload(t *testing.T) {
	h := NewMessageHandler()

	var errs []error
	h.OnError(func(err error) { errs = append(errs, err) })
	h.OnMetrics(func([]protocol.Metric) { t.Error("metrics callback ran for a malformed payload") })

	// One byte short of a whole number of metric entries.
	h.HandleMessage(&protocol.Message{
		Type:    protocol.MsgMetricsResponse,
		Payload: make([]byte, protocol.MetricEntrySize+1),
	})

	if len(errs) != 1 {
		t.Fatalf("error callback ran %d times, want 1", len(errs))
	}
}

func TestHandleMessageSurvivesAMalformedPayloadWithNoErrorCallback(t *testing.T) {
	h := NewMessageHandler()
	// No OnError registered: a device sending rubbish must not panic the bridge.
	h.HandleMessage(&protocol.Message{
		Type:    protocol.MsgMetricsResponse,
		Payload: make([]byte, protocol.MetricEntrySize+1),
	})
}

func TestHandleMessageIgnoresAnUnknownType(t *testing.T) {
	h := NewMessageHandler()
	h.OnMetrics(func([]protocol.Metric) { t.Error("metrics callback ran for an unknown type") })
	h.OnStatus(func(*protocol.StatusPayload) { t.Error("status callback ran for an unknown type") })
	h.OnError(func(error) { t.Error("error callback ran for an unknown type") })

	// An unrecognised type is expected from a newer firmware, not a failure.
	h.HandleMessage(&protocol.Message{Type: protocol.MessageType(0xEE)})
}

func TestHandleMessageWithoutCallbacksDoesNotPanic(t *testing.T) {
	h := NewMessageHandler()
	for _, msgType := range []protocol.MessageType{
		protocol.MsgPong,
		protocol.MsgMetricsResponse,
		protocol.MsgStatusResponse,
		protocol.MsgCommandResult,
	} {
		h.HandleMessage(&protocol.Message{Type: msgType})
	}
}

// ---- RequestTracker ----

func TestTrackerDeliversAResponseToTheWaitingRequest(t *testing.T) {
	rt := NewRequestTracker(time.Second)
	req := rt.Track(7, protocol.MsgPing)

	response := &protocol.Message{Type: protocol.MsgPong, Sequence: 7}
	if !rt.Complete(response) {
		t.Fatal("Complete returned false for a tracked sequence")
	}

	select {
	case got := <-req.ResponseCh:
		if got != response {
			t.Errorf("delivered %v, want the response that was completed", got)
		}
	default:
		t.Fatal("nothing was delivered to the response channel")
	}
}

func TestTrackerIgnoresAResponseForAnUnknownSequence(t *testing.T) {
	rt := NewRequestTracker(time.Second)
	rt.Track(1, protocol.MsgPing)

	// A late reply to a request that already timed out must not be mistaken
	// for the answer to a different one.
	if rt.Complete(&protocol.Message{Sequence: 99}) {
		t.Error("Complete returned true for an untracked sequence")
	}
}

func TestTrackerCompletesEachSequenceOnlyOnce(t *testing.T) {
	rt := NewRequestTracker(time.Second)
	rt.Track(3, protocol.MsgPing)

	if !rt.Complete(&protocol.Message{Sequence: 3}) {
		t.Fatal("first Complete returned false")
	}
	if rt.Complete(&protocol.Message{Sequence: 3}) {
		t.Error("a duplicate response was accepted for an already-completed request")
	}
}

func TestCleanupSignalsTimeoutOnExpiredRequests(t *testing.T) {
	rt := NewRequestTracker(time.Millisecond)
	req := rt.Track(5, protocol.MsgPing)

	time.Sleep(5 * time.Millisecond)
	rt.Cleanup()

	select {
	case got := <-req.ResponseCh:
		// nil signals a timeout; the caller distinguishes it from a response.
		if got != nil {
			t.Errorf("expected nil to signal timeout, got %v", got)
		}
	default:
		t.Fatal("Cleanup did not signal the expired request")
	}

	// Having timed out, the request must no longer be completable.
	if rt.Complete(&protocol.Message{Sequence: 5}) {
		t.Error("an expired request was still completable")
	}
}

func TestCleanupLeavesLiveRequestsAlone(t *testing.T) {
	rt := NewRequestTracker(time.Hour)
	req := rt.Track(5, protocol.MsgPing)

	rt.Cleanup()

	select {
	case got := <-req.ResponseCh:
		t.Fatalf("Cleanup signalled a request that had not expired: %v", got)
	default:
	}
	if !rt.Complete(&protocol.Message{Sequence: 5}) {
		t.Error("a live request should still be completable after Cleanup")
	}
}

func TestRemoveStopsTrackingWithoutSignalling(t *testing.T) {
	rt := NewRequestTracker(time.Second)
	req := rt.Track(9, protocol.MsgPing)

	rt.Remove(9)

	if rt.Complete(&protocol.Message{Sequence: 9}) {
		t.Error("a removed request was still completable")
	}
	select {
	case got := <-req.ResponseCh:
		t.Fatalf("Remove should not deliver anything, got %v", got)
	default:
	}
}

func TestTrackerIsSafeUnderConcurrentUse(t *testing.T) {
	rt := NewRequestTracker(time.Second)

	var wg sync.WaitGroup
	for i := 0; i < 64; i++ {
		seq := byte(i)
		wg.Add(1)
		go func() {
			defer wg.Done()
			req := rt.Track(seq, protocol.MsgPing)
			rt.Complete(&protocol.Message{Sequence: seq})
			<-req.ResponseCh
		}()
	}
	wg.Wait()
}
