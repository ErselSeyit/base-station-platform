package com.huawei.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for refreshing access tokens.
 */
@Schema(description = "Refresh token request")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The refresh token obtained during login", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    public RefreshTokenRequest() {}

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
