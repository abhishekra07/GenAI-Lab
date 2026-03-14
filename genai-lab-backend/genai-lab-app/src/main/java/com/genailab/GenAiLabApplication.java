package com.genailab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GenAI Lab Application Entry Point
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GenAiLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAiLabApplication.class, args);
    }
}