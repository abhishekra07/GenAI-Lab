package com.genailab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * GenAI Lab Application Entry Point
 *
 */
@SpringBootApplication
@EnableAsync
public class GenAiLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAiLabApplication.class, args);
    }
}
