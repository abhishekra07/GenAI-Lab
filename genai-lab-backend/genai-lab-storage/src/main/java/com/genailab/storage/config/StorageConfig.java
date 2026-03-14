package com.genailab.storage.config;

import com.genailab.storage.service.StorageService;
import com.genailab.storage.local.LocalStorageService;
import com.genailab.storage.minio.MinioStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects and creates the active StorageService implementation.
 *
 * <p>Uses Spring's @ConditionalOnProperty to create exactly one
 * StorageService bean based on the genailab.storage.provider value.
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class StorageConfig {

    /**
     * Create the MinIO storage implementation when provider=minio.
     * matchIfMissing=false means MinIO is NOT the default.
     */
    @Bean
    @ConditionalOnProperty(
            name = "genailab.storage.provider",
            havingValue = "minio",
            matchIfMissing = false)
    public StorageService minioStorageService(StorageProperties properties) {
        log.info("Storage provider: MinIO ({})", properties.getMinio().getEndpoint());
        return new MinioStorageService(properties);
    }

    /**
     * Create the local filesystem implementation when provider=local.
     * matchIfMissing=true means local IS the default if property is not set.
     */
    @Bean
    @ConditionalOnProperty(
            name = "genailab.storage.provider",
            havingValue = "local",
            matchIfMissing = true)
    public StorageService localStorageService(StorageProperties properties) {
        log.info("Storage provider: Local ({})", properties.getLocal().getBasePath());
        return new LocalStorageService(properties);
    }
}