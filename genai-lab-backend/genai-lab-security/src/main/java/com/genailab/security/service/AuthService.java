package com.genailab.security.service;

import com.genailab.security.domain.User;
import com.genailab.security.dto.AuthResponse;
import com.genailab.security.dto.LoginRequest;
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

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authentication business logic — register, login, token refresh.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${genailab.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Register a new user account.
     *
     * <p>Process:
     * <ol>
     *   <li>Check email is not already taken</li>
     *   <li>Hash the password with BCrypt</li>
     *   <li>Save the user</li>
     *   <li>Issue and return JWT + refresh token</li>
     * </ol>
     *
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

        return buildAuthResponse(savedUser);
    }

    /**
     * Authenticate an existing user.
     *
     * <p>We delegate credential verification to Spring Security's
     * {@link AuthenticationManager}. This means
     * Spring Security handles the password comparison, account locked
     * checks, account disabled checks, etc. We don't re-implement that logic here.
     *
     * <p>If authentication fails, AuthenticationManager throws
     * {@link org.springframework.security.core.AuthenticationException}
     * which we let propagate — the global exception handler will
     * catch it and return a 401 response.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // This call validates credentials and throws if invalid.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // =================
    // Private helpers
    // =================

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user, user.getId());
        String refreshToken = generateRefreshToken();

        // TODO: Persist refresh token in Step 4 extension
        // For now we issue it but don't store it — refresh endpoint
        // will be added once the refresh_tokens table entity is built.

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Generate a cryptographically random refresh token.
     *
     * <p>We use a random opaque string (not a JWT) for refresh tokens.
     * WHY not JWT? Refresh tokens must be revocable — we store them in
     * the DB so we can delete them on logout. Since we're hitting the DB
     * anyway, there's no benefit to using a self-contained JWT.
     * A random string is shorter, simpler, and equally secure.
     *
     * <p>SecureRandom is thread-safe and cryptographically strong.
     * 32 bytes = 256 bits of entropy, encoded as a URL-safe Base64 string.
     */
    private String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}