package com.genailab.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response body for register and login endpoints.
 *
 * <p>Returns both an access token and a refresh token
 * (long-lived opaque token stored in DB) so the client can:
 * <ul>
 *   <li>Use the access token for all API calls (Authorization: Bearer ...)</li>
 *   <li>Use the refresh token to get a new access token when it expires</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;      // access token TTL in seconds

    // Basic user info — so the frontend doesn't need a separate call
    private UUID userId;
    private String email;
    private String fullName;
}