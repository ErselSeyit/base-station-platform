package com.huawei.auth.controller;

import com.huawei.auth.dto.LoginRequest;
import com.huawei.auth.dto.LoginResponse;
import com.huawei.auth.entity.User;
import com.huawei.auth.service.CustomUserDetailsService;
import com.huawei.auth.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.debug("Login attempt for username: {}", request.getUsername());
        try {
            // Load user from database
            User user = userDetailsService.findByUsername(request.getUsername());
            log.debug("User found: {}, enabled: {}", user.getUsername(), user.isEnabled());

            // Verify password
            boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
            log.debug("Password matches: {}", passwordMatches);

            if (!passwordMatches) {
                log.warn("Password mismatch for user: {}", request.getUsername());
                return ResponseEntity.status(401).build();
            }

            // Check if user is enabled
            if (!user.isEnabled()) {
                log.warn("User is disabled: {}", request.getUsername());
                return ResponseEntity.status(401).build();
            }

            // Generate JWT token
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
            log.info("Login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole()));

        } catch (UsernameNotFoundException e) {
            log.warn("User not found: {}", request.getUsername());
            return ResponseEntity.status(401).build();
        }
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
