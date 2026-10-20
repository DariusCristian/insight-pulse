package com.insightpulse.ingestion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class KafkaConfig {
    // Kafka consumer/producer beans are auto-configured via application.yml.
    // Add custom ConcurrentKafkaListenerContainerFactory or ErrorHandler beans here as needed.

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
