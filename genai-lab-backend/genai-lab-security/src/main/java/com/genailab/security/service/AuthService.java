package com.genailab.security.service;

import com.genailab.security.domain.RefreshToken;
import com.genailab.security.domain.User;
import com.genailab.security.dto.AuthResponse;
import com.genailab.security.dto.LoginRequest;
import com.genailab.security.dto.RefreshRequest;
import com.genailab.security.dto.RegisterRequest;
import com.genailab.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication business logic — register, login, refresh, logout.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Value("${genailab.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request, String deviceInfo) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {}", savedUser.getEmail());
        return buildAuthResponse(savedUser, deviceInfo);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        log.info("User logged in: {} from {}", user.getEmail(), deviceInfo);
        return buildAuthResponse(user, deviceInfo);
    }

    /**
     * Exchange a refresh token for a new access token + new refresh token.
     *
     * <p>The old refresh token is rotated (revoked) and a new one is issued.
     * If reuse of an already-rotated token is detected, ALL sessions
     * for the user are revoked immediately.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request, String deviceInfo) {
        // Find the user from the refresh token
        RefreshToken existing = refreshTokenService.findValid(request.getRefreshToken());

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));

        // Rotate: revoke old token, issue new one
        RefreshToken newRefreshToken = refreshTokenService.rotate(
                request.getRefreshToken(), user, deviceInfo);

        String accessToken = jwtService.generateToken(user, user.getId());

        log.debug("Token refreshed for user: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Logout — revoke the refresh token so no new access tokens can be issued.
     *
     * <p>The current access token remains valid until it expires (15 min max).
     * This is acceptable — the short lifetime limits the exposure window.
     * If immediate invalidation is needed, maintain a token blocklist.
     */
    @Transactional
    public void logout(String refreshToken) {
        boolean revoked = refreshTokenService.revoke(refreshToken);
        if (!revoked) {
            log.debug("Logout called with already-revoked or unknown token — ignoring");
        }
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private AuthResponse buildAuthResponse(User user, String deviceInfo) {
        String accessToken = jwtService.generateToken(user, user.getId());
        RefreshToken refreshToken = refreshTokenService.issue(user, deviceInfo);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }
}