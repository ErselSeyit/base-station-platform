package com.huawei.auth.controller;

import static com.huawei.common.constants.JsonResponseKeys.KEY_ERROR;
import static com.huawei.common.constants.JsonResponseKeys.KEY_MESSAGE;

import com.huawei.auth.dto.LoginRequest;
import com.huawei.auth.dto.LoginResponse;
import com.huawei.auth.dto.RefreshTokenRequest;
import com.huawei.auth.dto.TokenResponse;
import com.huawei.auth.model.RefreshToken;
import com.huawei.auth.service.LoginAttemptService;
import com.huawei.auth.service.RefreshTokenService;
import com.huawei.auth.service.SecurityAuditService;
import com.huawei.auth.service.UserService;
import com.huawei.auth.util.JwtUtil;
import com.huawei.common.constants.TimeConstants;
import com.huawei.common.security.AuthConstants;
import com.huawei.common.security.Roles;
import com.huawei.common.util.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication and session management")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Constants for cookie attributes
    private static final String SAME_SITE_ATTR = "SameSite";
    private static final String SAME_SITE_STRICT = "Strict";

    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final UserService userService;
    private final SecurityAuditService auditService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${jwt.cookie.max-age:86400}")
    private int cookieMaxAge;

    @Value("${jwt.expiration:86400000}")
    private long accessTokenExpirationMs;

    public AuthController(JwtUtil jwtUtil, LoginAttemptService loginAttemptService,
                          UserService userService, SecurityAuditService auditService,
                          RefreshTokenService refreshTokenService) {
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
        this.userService = userService;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
    }

    @Operation(
            summary = "Authenticate user",
            description = "Validates credentials and returns a JWT token. Sets HttpOnly cookie for session management.")
    @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "429", description = "Account locked due to too many failed attempts")
    @PostMapping("/login")
    public ResponseEntity<Object> login(
            @Parameter(description = "Login credentials") @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = RequestUtils.getClientIp(httpRequest);
        String username = request.getUsername();
        String lockKey = username + ":" + clientIp;

        // Check if account/IP is blocked due to too many failed attempts
        if (loginAttemptService.isBlocked(lockKey)) {
            long remainingSeconds = loginAttemptService.getRemainingLockoutSeconds(lockKey);
            log.warn("Blocked login attempt for user '{}' from IP '{}'. Locked for {} more seconds",
                    username, clientIp, remainingSeconds);
            auditService.logAccountLocked(username, clientIp, remainingSeconds);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            KEY_ERROR, "Account temporarily locked due to too many failed attempts",
                            "retryAfterSeconds", remainingSeconds
                    ));
        }

        // Authenticate user against database
        var userOpt = userService.authenticate(username, request.getPassword());
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            loginAttemptService.recordSuccessfulLogin(lockKey);
            log.info("Successful login for user '{}' from IP '{}'", username, clientIp);
            auditService.logLoginSuccess(username, clientIp);
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            // Set HttpOnly cookie for secure token storage
            Cookie authCookie = new Cookie(AuthConstants.AUTH_COOKIE_NAME, token);
            authCookie.setHttpOnly(true);
            authCookie.setSecure(secureCookie);
            authCookie.setPath("/");
            authCookie.setMaxAge(cookieMaxAge);
            authCookie.setAttribute(SAME_SITE_ATTR, SAME_SITE_STRICT);
            httpResponse.addCookie(authCookie);

            // Still return token in response for backward compatibility with localStorage
            // but frontend should migrate to cookie-based auth
            return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole()));
        } else {
            loginAttemptService.recordFailedAttempt(lockKey);
            int remaining = loginAttemptService.getRemainingAttempts(lockKey);
            log.warn("Failed login attempt for user '{}' from IP '{}'. {} attempts remaining",
                    username, clientIp, remaining);
            auditService.logLoginFailure(username, clientIp, "Invalid credentials", remaining);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            KEY_ERROR, "Invalid credentials",
                            "remainingAttempts", remaining
                    ));
        }
    }

    @Operation(
            summary = "Logout user",
            description = "Clears the authentication cookie and invalidates the session.")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "JWT token (optional)") @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletResponse httpResponse) {
        // Clear the auth cookie
        Cookie authCookie = new Cookie(AuthConstants.AUTH_COOKIE_NAME, "");
        authCookie.setHttpOnly(true);
        authCookie.setSecure(secureCookie);
        authCookie.setPath("/");
        authCookie.setMaxAge(0); // Delete the cookie
        authCookie.setAttribute(SAME_SITE_ATTR, SAME_SITE_STRICT);
        httpResponse.addCookie(authCookie);

        // Log the logout for audit purposes
        String username = null;
        if (authHeader != null && authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            String token = authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH);
            try {
                username = jwtUtil.extractUsername(token);
                log.info("User logged out: {}", username);
                auditService.logLogout(username, RequestUtils.UNKNOWN_IP);
            } catch (Exception e) {
                log.debug("Could not extract username from token during logout: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Validate token",
            description = "Checks if the provided JWT token is valid and not expired.")
    @ApiResponse(responseCode = "200", description = "Token is valid")
    @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(
            @Parameter(description = "Bearer token", required = true) @RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            String token = authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH);
            try {
                String username = jwtUtil.extractUsername(token);
                boolean isValid = jwtUtil.validateToken(token, username);
                if (isValid) {
                    return ResponseEntity.ok().build();
                }
            } catch (Exception e) {
                log.debug("Token validation failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @Operation(
            summary = "Refresh access token",
            description = "Uses a valid refresh token to obtain a new access token. " +
                    "The refresh token is rotated for security (old token invalidated, new one issued).")
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(
            @Parameter(description = "Refresh token request") @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = RequestUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // Verify the refresh token
        var refreshTokenOpt = refreshTokenService.verifyRefreshToken(request.getRefreshToken());
        if (refreshTokenOpt.isEmpty()) {
            log.warn("Invalid refresh token from IP '{}'", clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, "Invalid or expired refresh token"));
        }

        RefreshToken oldRefreshToken = refreshTokenOpt.get();
        var user = oldRefreshToken.getUser();

        // Check if user account is still active
        if (!user.canLogin()) {
            log.warn("Refresh token used for disabled account: {}", user.getUsername());
            refreshTokenService.revokeAllUserTokens(user, "Account disabled");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, "Account is disabled"));
        }

        // Generate new access token
        String newAccessToken = jwtUtil.generateToken(user.getUsername(), user.getRole());

        // Rotate the refresh token (revoke old, create new)
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(
                oldRefreshToken, clientIp, userAgent);

        // Set new access token cookie
        Cookie authCookie = new Cookie(AuthConstants.AUTH_COOKIE_NAME, newAccessToken);
        authCookie.setHttpOnly(true);
        authCookie.setSecure(secureCookie);
        authCookie.setPath("/");
        authCookie.setMaxAge(cookieMaxAge);
        authCookie.setAttribute(SAME_SITE_ATTR, SAME_SITE_STRICT);
        httpResponse.addCookie(authCookie);

        log.info("Token refreshed for user '{}' from IP '{}'", user.getUsername(), clientIp);
        auditService.logRefreshTokenUsed(user.getUsername(), clientIp);

        TokenResponse response = TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .expiresIn(accessTokenExpirationMs / TimeConstants.MILLIS_PER_SECOND)
                .refreshExpiresIn(newRefreshToken.getRemainingSeconds())
                .username(user.getUsername())
                .role(user.getRole())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Revoke refresh token",
            description = "Revokes a specific refresh token, preventing it from being used to obtain new access tokens.")
    @ApiResponse(responseCode = "200", description = "Token revoked successfully")
    @ApiResponse(responseCode = "401", description = "Invalid token")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/revoke")
    public ResponseEntity<Object> revokeRefreshToken(
            @Parameter(description = "Refresh token to revoke") @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String clientIp = RequestUtils.getClientIp(httpRequest);

        // Get username from auth header for audit logging
        String username = Roles.ANONYMOUS_USER;
        if (authHeader != null && authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            try {
                username = jwtUtil.extractUsername(authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH));
            } catch (Exception e) {
                log.debug("Could not extract username from token for audit: {}", e.getMessage());
            }
        }

        boolean revoked = refreshTokenService.revokeToken(request.getRefreshToken(), "User requested revocation");

        if (revoked) {
            log.info("Refresh token revoked by user '{}' from IP '{}'", username, clientIp);
            auditService.logRefreshTokenRevoked(username, clientIp, "User requested");
            return ResponseEntity.ok(Map.of(KEY_MESSAGE, "Token revoked successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(KEY_ERROR, "Token not found or already revoked"));
        }
    }
}
