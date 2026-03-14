package com.genailab.security.config;

import com.genailab.security.filter.JwtAuthFilter;
import com.genailab.security.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
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

/**
 * Spring Security configuration — JWT stateless authentication.
 *
 * <p>Key design decisions:
 *
 * <p><b>Stateless sessions:</b> We never create server-side sessions.
 * Every request must carry a valid JWT. This makes the application
 * horizontally scalable — any server instance can validate any request
 * without shared session state.
 *
 * <p><b>CSRF disabled:</b> CSRF attacks exploit cookie-based authentication
 * by tricking a browser into making authenticated requests. Since we use
 * JWT in the Authorization header (not cookies), CSRF is not applicable.
 * Browsers never automatically send the Authorization header cross-origin.
 *
 * <p><b>Filter ordering:</b> JwtAuthFilter is added BEFORE
 * UsernamePasswordAuthenticationFilter so our JWT validation runs first
 * and populates the SecurityContext before Spring Security's default
 * authentication mechanism runs.
 *
 * <p><b>@EnableMethodSecurity:</b> Enables @PreAuthorize on service methods
 * for fine-grained access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     *
     * <p>Public endpoints — no JWT required:
     * <ul>
     *   <li>/api/v1/auth/** — register and login</li>
     *   <li>/actuator/** — health and metrics</li>
     * </ul>
     *
     * <p>Everything else requires a valid JWT.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/models").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder — cost factor 10 (~100ms per hash).
     * Salt is embedded in the hash — no separate salt column needed.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wires UserDetailsService and PasswordEncoder into Spring Security's
     * authentication mechanism.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes AuthenticationManager as a bean so AuthService can inject it.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}