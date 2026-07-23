package protocol

import (
	"testing"
)

// Every codec pair must round-trip: whatever Encode writes, Decode must read
// back unchanged. Asymmetry here corrupts data silently rather than failing.

func TestEncodeDecodeMetricsRoundTrip(t *testing.T) {
	in := []Metric{
		{Type: MetricDLThroughput, Band: BandN28, Value: 42.5},
		{Type: MetricDLThroughput, Band: BandN78, Value: -1.25},
	}

	out, err := DecodeMetrics(EncodeMetrics(in))
	if err != nil {
		t.Fatalf("DecodeMetrics returned an error: %v", err)
	}
	if len(out) != len(in) {
		t.Fatalf("got %d metrics, want %d", len(out), len(in))
	}
	for i := range in {
		if out[i].Type != in[i].Type || out[i].Band != in[i].Band || out[i].Value != in[i].Value {
			t.Errorf("metric %d = {%v, %v, %v}, want {%v, %v, %v}",
				i, out[i].Type, out[i].Band, out[i].Value, in[i].Type, in[i].Band, in[i].Value)
		}
	}
}

func TestDecodeMetricsRejectsTruncatedPayload(t *testing.T) {
	// One byte short of a whole number of entries.
	if _, err := DecodeMetrics(make([]byte, MetricEntrySize+1)); err == nil {
		t.Fatal("expected an error for a payload that is not a multiple of the entry size")
	}
}

func TestDecodeMetricsAcceptsEmptyPayload(t *testing.T) {
	metrics, err := DecodeMetrics(nil)
	if err != nil {
		t.Fatalf("empty payload should not be an error, got %v", err)
	}
	if len(metrics) != 0 {
		t.Fatalf("expected no metrics, got %d", len(metrics))
	}
}

func TestEncodeDecodeStatusRoundTrip(t *testing.T) {
	in := &StatusPayload{Status: StatusCode(2), Uptime: 123456, Errors: 7, Warnings: 9}

	out, err := DecodeStatus(EncodeStatus(in))
	if err != nil {
		t.Fatalf("DecodeStatus returned an error: %v", err)
	}
	if *out != *in {
		t.Errorf("round-trip gave %+v, want %+v", *out, *in)
	}
}

func TestDecodeStatusRejectsShortPayload(t *testing.T) {
	if _, err := DecodeStatus(make([]byte, 8)); err == nil {
		t.Fatal("expected an error for a payload shorter than the 9-byte status record")
	}
}

func TestEncodeDecodeCommandResultRoundTrip(t *testing.T) {
	in := &CommandResultPayload{Success: true, ReturnCode: 3, Output: "restart ok"}

	out, err := DecodeCommandResult(EncodeCommandResult(in))
	if err != nil {
		t.Fatalf("DecodeCommandResult returned an error: %v", err)
	}
	if out.Success != in.Success {
		t.Errorf("Success = %v, want %v", out.Success, in.Success)
	}
	if out.ReturnCode != in.ReturnCode {
		t.Errorf("ReturnCode = %v, want %v", out.ReturnCode, in.ReturnCode)
	}
	// The wire format has no length prefix: the frame header already carries
	// the payload length, and the device sends payload_len = 2 + len.
	if out.Output != in.Output {
		t.Errorf("Output = %q, want %q", out.Output, in.Output)
	}
}

func TestEncodeDecodeCommandResultEmptyOutput(t *testing.T) {
	in := &CommandResultPayload{Success: false, ReturnCode: 1, Output: ""}

	out, err := DecodeCommandResult(EncodeCommandResult(in))
	if err != nil {
		t.Fatalf("DecodeCommandResult returned an error: %v", err)
	}
	if out.Output != "" {
		t.Errorf("Output = %q, want empty", out.Output)
	}
	if out.Success != in.Success || out.ReturnCode != in.ReturnCode {
		t.Errorf("got %+v, want Success=%v ReturnCode=%v", out, in.Success, in.ReturnCode)
	}
}

func TestDecodeCommandResultRejectsShortPayload(t *testing.T) {
	if _, err := DecodeCommandResult(make([]byte, 1)); err == nil {
		t.Fatal("expected an error for a payload shorter than the fixed header")
	}
}

func TestVerifyCRC16RejectsCorruptedFrame(t *testing.T) {
	data := []byte{0xAA, 0x55, 0x00, 0x00, 0x01, 0x02}
	crc := CalculateCRC16(data)
	framed := append(append([]byte{}, data...), byte(crc>>8), byte(crc))

	if !VerifyCRC16(framed) {
		t.Fatal("a correctly framed message should verify")
	}

	framed[2] ^= 0xFF
	if VerifyCRC16(framed) {
		t.Error("a corrupted message must not verify")
	}
}


func TestDecodeMetricsKeepsBandsDistinct(t *testing.T) {
	// Same measurement on two carriers must stay separate — the whole point of
	// carrying the band as a dimension.
	in := []Metric{
		{Type: MetricRSRP, Band: BandN28, Value: -82},
		{Type: MetricRSRP, Band: BandN78, Value: -78},
	}

	out, err := DecodeMetrics(EncodeMetrics(in))
	if err != nil {
		t.Fatalf("DecodeMetrics returned an error: %v", err)
	}
	if len(out) != 2 {
		t.Fatalf("got %d metrics, want 2", len(out))
	}
	if out[0].Band != BandN28 || out[1].Band != BandN78 {
		t.Errorf("bands crossed: got %v, %v", out[0].Band, out[1].Band)
	}
	if out[0].Value != -82 || out[1].Value != -78 {
		t.Errorf("values crossed: got %v, %v", out[0].Value, out[1].Value)
	}
}
