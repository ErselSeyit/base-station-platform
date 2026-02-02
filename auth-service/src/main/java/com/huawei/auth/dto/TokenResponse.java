package com.huawei.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO containing access and refresh tokens.
 */
@Schema(description = "Token response containing access and refresh tokens")
public class TokenResponse {

    @Schema(description = "JWT access token for API authentication")
    private String accessToken;

    @Schema(description = "Refresh token for obtaining new access tokens")
    private String refreshToken;

    @Schema(description = "Token type (always 'Bearer')")
    private String tokenType = "Bearer";

    @Schema(description = "Access token expiration time in seconds")
    private long expiresIn;

    @Schema(description = "Refresh token expiration time in seconds")
    private long refreshExpiresIn;

    @Schema(description = "Username of the authenticated user")
    private String username;

    @Schema(description = "Role of the authenticated user")
    private String role;

    public TokenResponse() {}

    public TokenResponse(String accessToken, String refreshToken, long expiresIn,
                         long refreshExpiresIn, String username, String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
        this.username = username;
        this.role = role;
    }

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private long refreshExpiresIn;
        private String username;
        private String role;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder refreshExpiresIn(long refreshExpiresIn) {
            this.refreshExpiresIn = refreshExpiresIn;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public TokenResponse build() {
            return new TokenResponse(accessToken, refreshToken, expiresIn,
                                     refreshExpiresIn, username, role);
        }
    }

    // Getters and setters

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    public void setRefreshExpiresIn(long refreshExpiresIn) {
        this.refreshExpiresIn = refreshExpiresIn;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
