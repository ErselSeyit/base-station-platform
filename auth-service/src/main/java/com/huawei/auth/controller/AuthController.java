package com.huawei.auth.controller;

import com.huawei.auth.dto.LoginRequest;
import com.huawei.auth.dto.LoginResponse;
import com.huawei.auth.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // Simple hardcoded authentication for demo
        // In production, validate against database
        if ("admin".equals(request.getUsername()) && "admin".equals(request.getPassword())) {
            String token = jwtUtil.generateToken(request.getUsername(), "ROLE_ADMIN");
            return ResponseEntity.ok(new LoginResponse(token, request.getUsername(), "ROLE_ADMIN"));
        } else if ("user".equals(request.getUsername()) && "user".equals(request.getPassword())) {
            String token = jwtUtil.generateToken(request.getUsername(), "ROLE_USER");
            return ResponseEntity.ok(new LoginResponse(token, request.getUsername(), "ROLE_USER"));
        }

        return ResponseEntity.status(401).build();
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
