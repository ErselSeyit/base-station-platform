package netconf

import (
	"testing"
)

func TestGetLastElement(t *testing.T) {
	cases := map[string]string{
		"/ietf-interfaces:interfaces/interface/statistics/in-octets": "in-octets",
		"/hardware/component[name='board']/temperature":              "temperature",
		"/data/rsrp/":                                                "rsrp",
		"": "",
	}
	for xpath, want := range cases {
		if got := getLastElement(xpath); got != want {
			t.Errorf("getLastElement(%q) = %q, want %q", xpath, got, want)
		}
	}
}

func TestGetSubtreePathReturnsFirstTwoLevels(t *testing.T) {
	got := getSubtreePath("/ietf-interfaces:interfaces/interface/statistics/in-octets")
	want := "/ietf-interfaces:interfaces/interface"
	if got != want {
		t.Errorf("getSubtreePath = %q, want %q", got, want)
	}
}

func TestGetSubtreePathShortPathReturnedVerbatim(t *testing.T) {
	if got := getSubtreePath("/single"); got != "/single" {
		t.Errorf("getSubtreePath(/single) = %q, want /single", got)
	}
}

func TestExtractValueParsesElementText(t *testing.T) {
	xml := `<data><temperature>42.5</temperature></data>`
	got, err := extractValue(xml, "/data/temperature")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != 42.5 {
		t.Errorf("extractValue = %v, want 42.5", got)
	}
}

func TestExtractValueTrimsWhitespace(t *testing.T) {
	xml := "<data><rsrp>  -95.0 </rsrp></data>"
	got, err := extractValue(xml, "/data/rsrp")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != -95.0 {
		t.Errorf("extractValue = %v, want -95.0", got)
	}
}

func TestExtractValueErrorsWhenElementMissing(t *testing.T) {
	if _, err := extractValue(`<data></data>`, "/data/temperature"); err == nil {
		t.Error("expected error when element is absent")
	}
}

func TestExtractValueErrorsOnNonNumericText(t *testing.T) {
	xml := `<data><temperature>hot</temperature></data>`
	if _, err := extractValue(xml, "/data/temperature"); err == nil {
		t.Error("expected error when element text is not numeric")
	}
}

func TestParseMetricTypeKnownAndUnknown(t *testing.T) {
	if _, ok := parseMetricType("RSRP"); !ok {
		t.Error("RSRP should be a known metric type")
	}
	if _, ok := parseMetricType("NOT_A_METRIC"); ok {
		t.Error("unknown metric name should return ok=false")
	}
}
