package com.genailab.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT token creation and validation service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Generate signed JWT access tokens</li>
 *   <li>Validate token signature and expiry</li>
 *   <li>Extract claims (userId, email) from tokens</li>
 * </ul>
 *
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${genailab.security.jwt.secret}") String secret,
                      @Value("${genailab.security.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a JWT access token for an authenticated user.
     *
     * <p>The subject (sub) is the user's UUID as a string.
     * We use UUID not email because:
     * <ul>
     *   <li>Users can change their email — UUID never changes</li>
     *   <li>UUID is the DB primary key — direct lookup, no secondary index needed</li>
     * </ul>
     *
     * <p>Email is included as an additional claim for convenience
     * so the frontend can display it without a separate API call.
     */
    public String generateToken(UserDetails userDetails, UUID userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("email", userDetails.getUsername());
        return buildToken(extraClaims, userId.toString(), expirationMs);
    }

    /**
     * Validate a token against a UserDetails instance.
     *
     * <p>Checks two things:
     * <ol>
     *   <li>The token's subject (userId) matches the provided UserDetails</li>
     *   <li>The token is not expired</li>
     * </ol>
     *
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String subject = extractEmail(token);
            return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract the subject (userId) from a token.
     * Returns null if the token is invalid or expired.
     */
    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract the email claim from a token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration))
                .signWith(signingKey)
                .compact();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parse and validate the token signature, returning all claims.
     *
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}