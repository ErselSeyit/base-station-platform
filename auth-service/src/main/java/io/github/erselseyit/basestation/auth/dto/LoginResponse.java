package io.github.erselseyit.basestation.auth.dto;

/**
 * Immutable response DTO for successful login.
 */
public record LoginResponse(
        String token,
        String username,
        String role
) {}
