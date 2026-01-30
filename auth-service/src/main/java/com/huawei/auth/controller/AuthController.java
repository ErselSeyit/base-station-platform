package com.huawei.auth.controller;

import com.huawei.auth.dto.LoginRequest;
import com.huawei.auth.dto.LoginResponse;
import com.huawei.auth.service.LoginAttemptService;
import com.huawei.auth.service.SecurityAuditService;
import com.huawei.auth.service.UserService;
import com.huawei.auth.util.JwtUtil;
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

    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final UserService userService;
    private final SecurityAuditService auditService;

    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${jwt.cookie.max-age:86400}")
    private int cookieMaxAge;

    public AuthController(JwtUtil jwtUtil, LoginAttemptService loginAttemptService,
                          UserService userService, SecurityAuditService auditService) {
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
        this.userService = userService;
        this.auditService = auditService;
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
        String clientIp = getClientIp(httpRequest);
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
                            "error", "Account temporarily locked due to too many failed attempts",
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
            Cookie authCookie = new Cookie("auth_token", token);
            authCookie.setHttpOnly(true);
            authCookie.setSecure(secureCookie);
            authCookie.setPath("/");
            authCookie.setMaxAge(cookieMaxAge);
            authCookie.setAttribute("SameSite", "Strict");
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
                            "error", "Invalid credentials",
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
        Cookie authCookie = new Cookie("auth_token", "");
        authCookie.setHttpOnly(true);
        authCookie.setSecure(secureCookie);
        authCookie.setPath("/");
        authCookie.setMaxAge(0); // Delete the cookie
        authCookie.setAttribute("SameSite", "Strict");
        httpResponse.addCookie(authCookie);

        // Log the logout for audit purposes
        String username = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(token);
                log.info("User logged out: {}", username);
                auditService.logLogout(username, "unknown");
            } catch (Exception e) {
                log.debug("Could not extract username from token during logout");
            }
        }

        return ResponseEntity.ok().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String username = jwtUtil.extractUsername(token);
                boolean isValid = jwtUtil.validateToken(token, username);
                if (isValid) {
                    return ResponseEntity.ok().build();
                }
            } catch (Exception e) {
                return ResponseEntity.status(401).build();
            }
        }
        return ResponseEntity.status(401).build();
    }
}
