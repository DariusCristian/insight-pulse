package com.insightpulse.ingestion.config;

import com.insightpulse.ingestion.kafka.InvalidMarketEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;

@Slf4j
@Configuration
public class KafkaConfig {

    /**
     * Error handler picked up by Spring Boot's auto-configured {@code kafkaListenerContainerFactory}.
     *
     * <p>Transient failures (gRPC UNAVAILABLE / DEADLINE_EXCEEDED, DB errors) are retried
     * {@code retry-attempts} times, {@code retry-interval-ms} apart. After that — or immediately for
     * {@link InvalidMarketEventException} — the record is published to the dead-letter topic
     * and its offset is committed, so one bad record never blocks the partition.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaOperations<Object, Object> kafkaTemplate,
            @Value("${app.kafka.topics.market-events-dlq}") String dlqTopic,
            @Value("${app.kafka.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${app.kafka.retry-attempts:2}") long retryAttempts) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                // Negative partition: let the producer choose (the DLQ may have fewer partitions).
                (record, ex) -> {
                    log.error("Sending {}-{}@{} to {}: {}", record.topic(), record.partition(),
                            record.offset(), dlqTopic, rootMessage(ex));
                    return new TopicPartition(dlqTopic, -1);
                });

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
        handler.addNotRetryableExceptions(InvalidMarketEventException.class);
        // With MANUAL_IMMEDIATE ack mode the container only commits a recovered (dead-lettered)
        // record's offset if this is set; otherwise it is re-read and re-dead-lettered after a restart.
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
