package com.genailab.api.auth;

import com.genailab.security.dto.AuthResponse;
import com.genailab.security.dto.LoginRequest;
import com.genailab.security.dto.RefreshRequest;
import com.genailab.security.dto.RegisterRequest;
import com.genailab.security.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     * Create a new account. Returns access + refresh tokens.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.register(request, getDeviceInfo(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Authenticate with email + password. Returns access + refresh tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.login(request, getDeviceInfo(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * <p>Exchange a refresh token for a new access token + new refresh token.
     *
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.refresh(request, getDeviceInfo(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody Map<String, String> body) {

        String refreshToken = body.get("refreshToken");
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Extract device info from the User-Agent header.
     * Used to label active sessions (e.g. "Chrome on Mac").
     */
    private String getDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) return "Unknown device";
        // Truncate to 500 chars max (DB column limit)
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}