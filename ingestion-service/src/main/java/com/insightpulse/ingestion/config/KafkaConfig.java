package com.insightpulse.ingestion.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    // Kafka consumer/producer beans are auto-configured via application.yml.
    // Add custom ConcurrentKafkaListenerContainerFactory or ErrorHandler beans here as needed.
}
