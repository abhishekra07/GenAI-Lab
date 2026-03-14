package com.genailab.security.service;

import com.genailab.security.domain.RefreshToken;
import com.genailab.security.domain.User;
import com.genailab.security.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 48; // 64 chars base64url

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${genailab.security.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    /**
     * Issue a new refresh token for a user.
     * Called on login and after successful token rotation.
     */
    @Transactional
    public RefreshToken issue(User user, String deviceInfo) {
        RefreshToken token = RefreshToken.builder()
                .userId(user.getId())
                .token(generateToken())
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .deviceInfo(deviceInfo)
                .build();

        return refreshTokenRepository.save(token);
    }

    /**
     * Find and validate a refresh token without rotating it.
     * Used by AuthService.refresh() to look up the user before rotating.
     */
    public RefreshToken findValid(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired refresh token"));

        if (token.isExpired()) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new IllegalArgumentException("Refresh token has expired. Please log in again.");
        }

        return token;
    }

    /**
     * Validate and rotate a refresh token.
     *
     * <p>Rotation means:
     * 1. Validate the incoming token
     * 2. Revoke it immediately
     * 3. Issue a brand new token
     * 4. Return the new token
     *
     * <p>If the token is already revoked (reuse detected), revoke ALL
     * tokens for that user — this indicates a stolen token scenario.
     *
     * @throws IllegalArgumentException if token is invalid, expired, or revoked
     */
    @Transactional
    public RefreshToken rotate(String tokenValue, User user, String deviceInfo) {
        RefreshToken existing = refreshTokenRepository
                .findByTokenAndRevokedFalse(tokenValue)
                .orElseThrow(() -> {
                    // Token not found in non-revoked set — either invalid or already revoked.
                    // Check if it was previously valid (revoked) — if so, this is a reuse attack.
                    boolean wasRevoked = refreshTokenRepository
                            .findAll().stream()
                            .anyMatch(t -> t.getToken().equals(tokenValue) && t.isRevoked());

                    if (wasRevoked) {
                        // Reuse detected — revoke all tokens for this user immediately
                        log.warn("SECURITY: Refresh token reuse detected for user: {}. " +
                                "Revoking all sessions.", user.getId());
                        refreshTokenRepository.revokeAllForUser(user.getId());
                    }

                    return new IllegalArgumentException("Invalid or expired refresh token");
                });

        // Verify it belongs to this user
        if (!existing.getUserId().equals(user.getId())) {
            log.warn("SECURITY: Refresh token user mismatch. Token user: {}, Request user: {}",
                    existing.getUserId(), user.getId());
            throw new IllegalArgumentException("Invalid refresh token");
        }

        // Check expiry
        if (existing.isExpired()) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new IllegalArgumentException("Refresh token has expired. Please log in again.");
        }

        // Revoke the used token
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        // Issue a new token (rotation)
        return issue(user, deviceInfo);
    }

    /**
     * Revoke a specific refresh token — used on logout.
     * Returns false if token was not found or already revoked.
     */
    @Transactional
    public boolean revoke(String tokenValue) {
        return refreshTokenRepository.findByTokenAndRevokedFalse(tokenValue)
                .map(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.debug("Refresh token revoked for user: {}", token.getUserId());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Revoke all refresh tokens for a user.
     * Used on password change or suspicious activity.
     */
    @Transactional
    public int revokeAll(UUID userId) {
        int count = refreshTokenRepository.revokeAllForUser(userId);
        log.info("Revoked {} refresh tokens for user: {}", count, userId);
        return count;
    }

    /**
     * Get all active sessions for a user.
     * Used for "manage sessions" UI feature.
     */
    public List<RefreshToken> getActiveSessions(UUID userId) {
        return refreshTokenRepository
                .findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * Cleanup job — runs daily at 3am to delete expired/revoked tokens.
     * Keeps the refresh_tokens table from growing indefinitely.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minusSeconds(30L * 24 * 60 * 60); // 30 days ago
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked refresh tokens", deleted);
        }
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}