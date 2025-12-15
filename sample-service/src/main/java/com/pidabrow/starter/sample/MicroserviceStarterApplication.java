package com.pidabrow.starter.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the microservice starter.
 */
@SpringBootApplication(scanBasePackages = "com.pidabrow.starter")
public class MicroserviceStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroserviceStarterApplication.class, args);
    }
}
