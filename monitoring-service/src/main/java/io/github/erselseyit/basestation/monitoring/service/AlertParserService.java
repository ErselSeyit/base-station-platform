package io.github.erselseyit.basestation.monitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw alert text to extract structured information.
 * Supports common syslog, SNMP trap, and vendor-specific formats.
 *
 * This is critical for BYOA (Bring Your Own Alert) functionality.
 */
@Service
public class AlertParserService {

    private static final Logger log = LoggerFactory.getLogger(AlertParserService.class);

    // Common patterns for metric extraction
    private static final Pattern RSSI_PATTERN = Pattern.compile("RSSI[:\\s]+(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RSRP_PATTERN = Pattern.compile("RSRP[:\\s]+(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RSRQ_PATTERN = Pattern.compile("RSRQ[:\\s]+(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINR_PATTERN = Pattern.compile("SINR[:\\s]+(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CPU_PATTERN = Pattern.compile("CPU[:\\s]+([\\d.]+)%?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_PATTERN = Pattern.compile("(?:MEM(?:ORY)?|RAM)[:\\s]+([\\d.]+)%?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("TEMP(?:ERATURE)?[:\\s]+([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDOVER_PATTERN = Pattern.compile("(?:HANDOVER|HO)[:\\s]*(?:FAIL(?:URE)?|SUCCESS)?[:\\s]*(?:RATE)?[:\\s]+([\\d.]+)%?", Pattern.CASE_INSENSITIVE);
    private static final Pattern LATENCY_PATTERN = Pattern.compile("(?:LATENCY|RTT|DELAY)[:\\s]+([\\d.]+)\\s*(?:ms)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKET_LOSS_PATTERN = Pattern.compile("(?:PACKET[\\s_]?LOSS|PKT[\\s_]?LOSS)[:\\s]+([\\d.]+)%?", Pattern.CASE_INSENSITIVE);
    private static final Pattern THROUGHPUT_PATTERN = Pattern.compile("(?:THROUGHPUT|THRU|BW)[:\\s]+([\\d.]+)\\s*(?:Mbps|Gbps|kbps)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern VSWR_PATTERN = Pattern.compile("VSWR[:\\s]+([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POWER_PATTERN = Pattern.compile("(?:POWER|PWR)[:\\s]+([\\d.]+)\\s*(?:dBm|W|kW)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLER_PATTERN = Pattern.compile("(?:BLER|BLOCK[\\s_]?ERROR)[:\\s]+([\\d.]+)%?", Pattern.CASE_INSENSITIVE);

    // Station ID patterns
    private static final Pattern STATION_ID_PATTERN = Pattern.compile(
            "(?:BS|BTS|ENB|GNB|STATION|CELL|SITE)[-_]?(\\d+|[A-Z0-9]+[-_]\\d+)",
            Pattern.CASE_INSENSITIVE);

    // Severity patterns
    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
            "\\b(CRITICAL|MAJOR|MINOR|WARNING|INFO|ERROR|ALERT|EMERGENCY|NOTICE)\\b",
            Pattern.CASE_INSENSITIVE);

    // Problem type constants (S1192 - avoid duplicated literals)
    private static final String SIGNAL_DEGRADATION = "SIGNAL_DEGRADATION";
    private static final String HANDOVER_FAILURE = "HANDOVER_FAILURE";
    private static final String CPU_OVERHEAT = "CPU_OVERHEAT";
    private static final String MEMORY_PRESSURE = "MEMORY_PRESSURE";
    private static final String TEMPERATURE_ALARM = "TEMPERATURE_ALARM";

    // Severity constants
    private static final String SEVERITY_CRITICAL = "critical";
    private static final String SEVERITY_MAJOR = "major";
    private static final String SEVERITY_WARNING = "warning";

    // Problem type patterns
    private static final Map<Pattern, String> PROBLEM_PATTERNS = new LinkedHashMap<>();

    static {
        // Signal/RF issues
        PROBLEM_PATTERNS.put(Pattern.compile("RSSI.*(?:drop|low|degraded|weak)", Pattern.CASE_INSENSITIVE), SIGNAL_DEGRADATION);
        PROBLEM_PATTERNS.put(Pattern.compile("(?:signal|RF).*(?:degraded|weak|poor|low)", Pattern.CASE_INSENSITIVE), SIGNAL_DEGRADATION);
        PROBLEM_PATTERNS.put(Pattern.compile("(?:interference|jamming)", Pattern.CASE_INSENSITIVE), "HIGH_INTERFERENCE");

        // Handover issues
        PROBLEM_PATTERNS.put(Pattern.compile("(?:handover|HO).*(?:fail|failure|reject)", Pattern.CASE_INSENSITIVE), HANDOVER_FAILURE);

        // Cell/Availability issues
        PROBLEM_PATTERNS.put(Pattern.compile("cell.*(?:unavailable|down|offline|outage)", Pattern.CASE_INSENSITIVE), "CELL_UNAVAILABLE");
        PROBLEM_PATTERNS.put(Pattern.compile("(?:site|station).*(?:down|offline|unreachable)", Pattern.CASE_INSENSITIVE), "SITE_DOWN");

        // Hardware issues
        PROBLEM_PATTERNS.put(Pattern.compile("(?:CPU|processor).*(?:high|overload|100%)", Pattern.CASE_INSENSITIVE), CPU_OVERHEAT);
        PROBLEM_PATTERNS.put(Pattern.compile("(?:memory|RAM).*(?:high|full|exhausted)", Pattern.CASE_INSENSITIVE), MEMORY_PRESSURE);
        PROBLEM_PATTERNS.put(Pattern.compile("(?:temperature|temp).*(?:high|critical|overheat)", Pattern.CASE_INSENSITIVE), TEMPERATURE_ALARM);
        PROBLEM_PATTERNS.put(Pattern.compile("(?:fan|cooling).*(?:fail|fault)", Pattern.CASE_INSENSITIVE), "COOLING_FAILURE");
        PROBLEM_PATTERNS.put(Pattern.compile("(?:disk|storage).*(?:full|fail|error)", Pattern.CASE_INSENSITIVE), "STORAGE_ISSUE");

        // Power issues
        PROBLEM_PATTERNS.put(Pattern.compile("(?:power|voltage).*(?:fail|loss|low|high)", Pattern.CASE_INSENSITIVE), "POWER_ISSUE");
        PROBLEM_PATTERNS.put(Pattern.compile("battery.*(?:low|critical|fail)", Pattern.CASE_INSENSITIVE), "LOW_BATTERY");

        // Antenna/Feeder issues
        PROBLEM_PATTERNS.put(Pattern.compile("(?:VSWR|antenna|feeder).*(?:high|fail|alarm)", Pattern.CASE_INSENSITIVE), "ANTENNA_FEEDER_ISSUE");

        // Network/Connectivity issues
        PROBLEM_PATTERNS.put(Pattern.compile("(?:latency|delay|RTT).*(?:high|increased)", Pattern.CASE_INSENSITIVE), "HIGH_LATENCY");
        PROBLEM_PATTERNS.put(Pattern.compile("(?:packet.*loss|loss.*packet)", Pattern.CASE_INSENSITIVE), "PACKET_LOSS");
        PROBLEM_PATTERNS.put(Pattern.compile("(?:link|backhaul|transport).*(?:down|fail|loss)", Pattern.CASE_INSENSITIVE), "BACKHAUL_FAILURE");
        PROBLEM_PATTERNS.put(Pattern.compile("(?:throughput|bandwidth).*(?:low|degraded)", Pattern.CASE_INSENSITIVE), "LOW_THROUGHPUT");

        // Software issues
        PROBLEM_PATTERNS.put(Pattern.compile("(?:process|service).*(?:crash|died|restart)", Pattern.CASE_INSENSITIVE), "PROCESS_CRASH");
        PROBLEM_PATTERNS.put(Pattern.compile("(?:sync|synchronization).*(?:lost|fail)", Pattern.CASE_INSENSITIVE), "SYNC_FAILURE");
    }

    /**
     * Parse raw alert text and extract structured information.
     */
    public ParsedAlert parse(String rawAlert) {
        log.debug("Parsing raw alert text ({} chars)", rawAlert.length());

        String stationId = extractStationId(rawAlert);
        String severity = extractSeverity(rawAlert);
        String problemCode = detectProblemCode(rawAlert);
        String category = determineCategory(problemCode);
        Map<String, Object> metrics = extractMetrics(rawAlert);
        List<String> patterns = detectPatterns(rawAlert);
        String message = extractMessage(rawAlert);

        log.info("Parsed alert: station={}, severity={}, code={}, metrics={}",
                stationId, severity, problemCode, metrics.size());

        return new ParsedAlert(
                stationId,
                severity,
                problemCode,
                category,
                message,
                metrics,
                metrics,  // extractedMetrics same as metrics for now
                patterns
        );
    }

    @Nullable
    private String extractStationId(String text) {
        Matcher matcher = STATION_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private String extractSeverity(String text) {
        Matcher matcher = SEVERITY_PATTERN.matcher(text);
        if (matcher.find()) {
            String severity = matcher.group(1).toUpperCase();
            return switch (severity) {
                case "EMERGENCY", "CRITICAL", "ALERT" -> SEVERITY_CRITICAL;
                case "ERROR", "MAJOR" -> SEVERITY_MAJOR;
                case "WARNING", "MINOR" -> SEVERITY_WARNING;
                default -> "info";
            };
        }
        // Default based on keywords
        String lower = text.toLowerCase();
        if (lower.contains(SEVERITY_CRITICAL) || lower.contains("emergency")) return SEVERITY_CRITICAL;
        if (lower.contains("error") || lower.contains(SEVERITY_MAJOR)) return SEVERITY_MAJOR;
        if (lower.contains(SEVERITY_WARNING) || lower.contains("minor")) return SEVERITY_WARNING;
        return "info";
    }

    private String detectProblemCode(String text) {
        for (Map.Entry<Pattern, String> entry : PROBLEM_PATTERNS.entrySet()) {
            if (Objects.requireNonNull(entry.getKey()).matcher(text).find()) {
                return Objects.requireNonNull(entry.getValue());
            }
        }

        // Fallback: try to construct from metrics
        String lower = text.toLowerCase();
        if (lower.contains("rssi") || lower.contains("rsrp") || lower.contains("signal")) {
            return SIGNAL_DEGRADATION;
        }
        if (lower.contains("cpu")) return CPU_OVERHEAT;
        if (lower.contains("memory") || lower.contains("ram")) return MEMORY_PRESSURE;
        if (lower.contains("temperature") || lower.contains("temp")) return TEMPERATURE_ALARM;
        if (lower.contains("handover") || lower.contains("ho ")) return HANDOVER_FAILURE;

        return "UNKNOWN_ALERT";
    }

    private String determineCategory(String problemCode) {
        // Using if-else instead of switch to leverage constants (S1192)
        if (SIGNAL_DEGRADATION.equals(problemCode) || "HIGH_INTERFERENCE".equals(problemCode)
                || HANDOVER_FAILURE.equals(problemCode) || "CELL_UNAVAILABLE".equals(problemCode)
                || "ANTENNA_FEEDER_ISSUE".equals(problemCode)) {
            return "network";
        }
        if (CPU_OVERHEAT.equals(problemCode) || MEMORY_PRESSURE.equals(problemCode)
                || TEMPERATURE_ALARM.equals(problemCode) || "COOLING_FAILURE".equals(problemCode)
                || "STORAGE_ISSUE".equals(problemCode)) {
            return "hardware";
        }
        if ("POWER_ISSUE".equals(problemCode) || "LOW_BATTERY".equals(problemCode)) {
            return "power";
        }
        if ("HIGH_LATENCY".equals(problemCode) || "PACKET_LOSS".equals(problemCode)
                || "BACKHAUL_FAILURE".equals(problemCode) || "LOW_THROUGHPUT".equals(problemCode)) {
            return "connectivity";
        }
        if ("PROCESS_CRASH".equals(problemCode) || "SYNC_FAILURE".equals(problemCode)) {
            return "software";
        }
        return "general";
    }

    @SuppressWarnings("null") // Patterns are static final, never null
    private Map<String, Object> extractMetrics(String text) {
        Map<String, Object> metrics = new HashMap<>();

        extractMetric(text, RSSI_PATTERN, "rssi_dbm", metrics);
        extractMetric(text, RSRP_PATTERN, "rsrp_dbm", metrics);
        extractMetric(text, RSRQ_PATTERN, "rsrq_db", metrics);
        extractMetric(text, SINR_PATTERN, "sinr_db", metrics);
        extractMetric(text, CPU_PATTERN, "cpu_percent", metrics);
        extractMetric(text, MEMORY_PATTERN, "memory_percent", metrics);
        extractMetric(text, TEMPERATURE_PATTERN, "temperature_c", metrics);
        extractMetric(text, HANDOVER_PATTERN, "handover_fail_rate", metrics);
        extractMetric(text, LATENCY_PATTERN, "latency_ms", metrics);
        extractMetric(text, PACKET_LOSS_PATTERN, "packet_loss_percent", metrics);
        extractMetric(text, THROUGHPUT_PATTERN, "throughput_mbps", metrics);
        extractMetric(text, VSWR_PATTERN, "vswr", metrics);
        extractMetric(text, POWER_PATTERN, "power_dbm", metrics);
        extractMetric(text, BLER_PATTERN, "bler_percent", metrics);

        return metrics;
    }

    private void extractMetric(String text, Pattern pattern, String key, Map<String, Object> metrics) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1));
                metrics.put(key, value);
            } catch (NumberFormatException e) {
                log.debug("Failed to parse metric {}: {}", key, matcher.group(1));
            }
        }
    }

    private List<String> detectPatterns(String text) {
        List<String> patterns = new ArrayList<>();

        for (Map.Entry<Pattern, String> entry : PROBLEM_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(text).find()) {
                patterns.add(entry.getValue());
            }
        }

        return patterns;
    }

    private String extractMessage(String text) {
        // Take first meaningful line as the message
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 10) {
                // Remove timestamp prefix if present
                String cleaned = trimmed.replaceFirst("^[A-Za-z]{3}\\s+\\d+\\s+[\\d:]+\\s+", "");
                if (cleaned.length() > 200) {
                    cleaned = cleaned.substring(0, 200) + "...";
                }
                return cleaned;
            }
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /**
     * Parsed alert result with all extracted information.
     */
    public record ParsedAlert(
            @Nullable String stationId,
            String severity,
            String problemCode,
            String category,
            String message,
            Map<String, Object> metrics,
            Map<String, Object> extractedMetrics,
            List<String> detectedPatterns
    ) {}
}
