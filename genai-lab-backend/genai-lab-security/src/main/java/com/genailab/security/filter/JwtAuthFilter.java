package com.genailab.security.filter;

import com.genailab.security.service.JwtService;
import com.genailab.security.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs once per HTTP request.
 *
 * <p>Extends {@link OncePerRequestFilter} which guarantees this filter
 * runs exactly once per request, even in complex forward/include scenarios.
 *
 * <p>Filter flow for each request:
 * <pre>
 * 1. Extract "Authorization" header
 * 2. If no "Bearer " token → skip (let Spring Security handle as unauthenticated)
 * 3. Extract subject (userId) from token
 * 4. If SecurityContext already has auth → skip (already authenticated)
 * 5. Load UserDetails from DB by email extracted from token
 * 6. Validate token against UserDetails
 * 7. If valid → set authentication in SecurityContext
 * 8. Continue filter chain
 * </pre>
 *
 * <p>WHY load UserDetails from DB on every request?
 * JWTs are stateless but we still load the user to:
 * <ul>
 *   <li>Verify the user still exists (they may have been deleted)</li>
 *   <li>Check {@code isActive} — a deactivated user should be rejected
 *       even if their JWT hasn't expired yet</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        /*
        * No Authorization header or not a Bearer token — pass through.
        * Spring Security will handle this as an unauthenticated request.
        * Public endpoints (register, login) will succeed.
        * Protected endpoints will return 401.
        * */
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            final String userEmail = jwtService.extractEmail(jwt);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                          // credentials — null after auth
                                    userDetails.getAuthorities()
                            );

                    // Attach request details (IP address, session id) to the auth token.
                    // Used by Spring Security for audit logging and session management.
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    /*
                    * Setting auth in SecurityContext means downstream code
                    * can call SecurityContextHolder.getContext().getAuthentication()
                    * and get the authenticated user — including in @Service methods
                    * via @AuthenticationPrincipal or SecurityContextHolder directly.
                    *
                    * */
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("Authenticated user: {} for request: {} {}",
                            userEmail, request.getMethod(), request.getRequestURI());
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed for request {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}