package com.insightpulse.ingestion.kafka;

import com.insightpulse.ingestion.model.MarketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketEventConsumer {

    /**
     * Listens to the {@code market-events} Kafka topic and processes each record.
     * Acknowledgment is manual — call {@link Acknowledgment#acknowledge()} after
     * successful downstream processing to prevent re-delivery.
     *
     * @param record  the raw Kafka consumer record
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = "${app.kafka.topics.market-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        // TODO: deserialize record.value() into MarketEvent, validate, enrich,
        //       call SentimentGrpcClient, persist result, dispatch alert.
    }
}
