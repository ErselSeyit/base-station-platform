package com.huawei.monitoring.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

/**
 * Jackson configuration for consistent date/time handling.
 *
 * <p>This configuration ensures that:
 * <ul>
 *   <li>Dates are serialized as ISO-8601 strings (not arrays or timestamps)</li>
 *   <li>RFC3339 timestamps with 'Z' suffix can be parsed into LocalDateTime</li>
 *   <li>Standard ISO LocalDateTime format is also supported</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    // Include 'Z' suffix so JavaScript interprets as UTC
    private static final DateTimeFormatter ISO_DATE_TIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
            );

            // Register custom serializer/deserializer for consistent ISO date format
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(ISO_DATE_TIME_UTC));
            javaTimeModule.addDeserializer(LocalDateTime.class,
                    new FlexibleLocalDateTimeDeserializer());

            // Use modulesToInstall to add to existing modules rather than replace
            builder.modulesToInstall(javaTimeModule);
        };
    }

    /**
     * Custom deserializer that handles multiple timestamp formats:
     * - RFC3339 with Z suffix: "2026-02-04T10:30:00Z"
     * - RFC3339 with offset: "2026-02-04T10:30:00+00:00"
     * - ISO LocalDateTime: "2026-02-04T10:30:00"
     */
    private static class FlexibleLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

        private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        public FlexibleLocalDateTimeDeserializer() {
            super(ISO_LOCAL);
        }

        @Override
        protected LocalDateTime _fromString(com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext ctxt, String string)
                throws java.io.IOException {

            if (string == null || string.isEmpty()) {
                return null;
            }

            String trimmed = string.trim();

            // Handle RFC3339 format with Z (UTC) suffix
            if (trimmed.endsWith("Z")) {
                try {
                    Instant instant = Instant.parse(trimmed);
                    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                } catch (DateTimeParseException e) {
                    // Fall through to other parsers
                }
            }

            // Handle RFC3339 format with timezone offset (+00:00, -05:00, etc.)
            if (trimmed.contains("+") || (trimmed.lastIndexOf('-') > trimmed.indexOf('T'))) {
                try {
                    Instant instant = Instant.parse(trimmed);
                    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                } catch (DateTimeParseException e) {
                    // Fall through to standard parser
                }
            }

            // Standard ISO LocalDateTime format (no timezone)
            return super._fromString(p, ctxt, trimmed);
        }
    }
}
