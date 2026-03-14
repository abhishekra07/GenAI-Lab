package com.genailab.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the thread pool used by all @Async methods.
 */
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Value("${genailab.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${genailab.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${genailab.async.queue-capacity:50}")
    private int queueCapacity;

    @Value("${genailab.async.thread-name-prefix:genailab-async-}")
    private String threadNamePrefix;

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);

        // When queue is full and max threads are busy, the calling thread
        // runs the task itself rather than throwing RejectedExecutionException.
        // This prevents task loss under heavy load.
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("Async executor configured: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }
}