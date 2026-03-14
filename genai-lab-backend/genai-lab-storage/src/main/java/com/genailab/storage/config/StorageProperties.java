package com.genailab.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for all storage configuration.
 *
 * <p>Bound from the genailab.storage.* properties in application.yml.
 *
 * <p>Example yaml:
 * <pre>
 * genailab:
 *   storage:
 *     provider: minio
 *     minio:
 *       endpoint: http://localhost:9000
 *       access-key: minioadmin
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "genailab.storage")
public class StorageProperties {

    /**
     * Which storage implementation to use.
     * Accepted values: "local", "minio"
     */
    private String provider = "local";

    private Local local = new Local();
    private Minio minio = new Minio();

    @Data
    public static class Local {
        /** Base directory for local file storage. */
        private String basePath = "./uploads";
    }

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "genailab-documents";
        private boolean secure = false;
    }
}