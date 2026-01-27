package com.huawei.auth.controller;

import com.huawei.auth.dto.LoginRequest;
import com.huawei.auth.dto.LoginResponse;
import com.huawei.auth.service.LoginAttemptService;
import com.huawei.auth.service.UserService;
import com.huawei.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final UserService userService;

    public AuthController(JwtUtil jwtUtil, LoginAttemptService loginAttemptService, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String username = request.getUsername();
        String lockKey = username + ":" + clientIp;

        // Check if account/IP is blocked due to too many failed attempts
        if (loginAttemptService.isBlocked(lockKey)) {
            long remainingSeconds = loginAttemptService.getRemainingLockoutSeconds(lockKey);
            log.warn("Blocked login attempt for user '{}' from IP '{}'. Locked for {} more seconds",
                    username, clientIp, remainingSeconds);
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
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
            return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole()));
        } else {
            loginAttemptService.recordFailedAttempt(lockKey);
            int remaining = loginAttemptService.getRemainingAttempts(lockKey);
            log.warn("Failed login attempt for user '{}' from IP '{}'. {} attempts remaining",
                    username, clientIp, remaining);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Invalid credentials",
                            "remainingAttempts", remaining
                    ));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String authHeader) {
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
