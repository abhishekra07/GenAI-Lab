package com.genailab.security.config;

import com.genailab.security.filter.JwtAuthFilter;
import com.genailab.security.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration — JWT stateless auth + CORS.
 *
 * <p><b>CORS:</b> Configured via genailab.cors.* in application.yml.
 * Allowed origins are environment-specific — never hardcoded.
 * Spring Security's CORS filter runs BEFORE the JWT filter, which
 * is required so preflight OPTIONS requests are handled correctly
 * (OPTIONS requests don't carry Authorization headers).
 *
 * <p><b>Why CORS must be in Spring Security, not just Spring MVC:</b>
 * Spring Security intercepts requests before Spring MVC.
 * If CORS is only configured in Spring MVC (@CrossOrigin or WebMvcConfigurer),
 * Spring Security blocks the preflight OPTIONS request before it reaches MVC.
 * Configuring CORS in the security filter chain ensures OPTIONS requests
 * pass through correctly.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Apply CORS config — must come before CSRF and auth filters
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF disabled — we use JWT in Authorization header, not cookies
                // Browsers never auto-send Authorization headers cross-origin
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no JWT required
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/models").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // Everything else requires a valid JWT
                        .anyRequest().authenticated()
                )

                .authenticationProvider(authenticationProvider())

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration source.
     *
     * <p>Applied to all routes ("/**"). Values come from genailab.cors.*
     * in application.yml — different per environment.
     *
     * <p>allowCredentials(true) is required for the browser to send
     * the Authorization header in cross-origin requests. When true,
     * allowedOrigins must be explicit URLs (not "*").
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        // Expose these headers so the frontend can read them
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Type");

        log.info("CORS configured. Allowed origins: {}", corsProperties.getAllowedOrigins());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}