package com.genailab.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "genailab.cors")
@Data
public class CorsProperties {

    /**
     * Comma-separated or list of allowed frontend origins.
     */
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private List<String> allowedHeaders = List.of("*");

    private boolean allowCredentials = true;

    private long maxAge = 3600;
}