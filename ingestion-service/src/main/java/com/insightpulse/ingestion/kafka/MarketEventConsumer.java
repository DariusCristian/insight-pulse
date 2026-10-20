package com.insightpulse.ingestion.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.service.MarketEventPipeline;
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

    private final ObjectMapper objectMapper;
    private final MarketEventPipeline pipeline;

    /**
     * Listens to the {@code market-events} Kafka topic, parses and validates each record,
     * runs it through the {@link MarketEventPipeline} and acknowledges it on success.
     *
     * <p>On failure the record is <em>not</em> acknowledged here; the exception goes to the
     * container's error handler ({@code KafkaConfig}), which retries transient failures and
     * then publishes the record to the dead-letter topic and commits its offset.
     *
     * @param record the raw Kafka consumer record
     * @param ack    manual acknowledgment handle
     */
    @KafkaListener(
            topics = "${app.kafka.topics.market-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        MarketEvent event = parse(record);
        pipeline.process(event);
        ack.acknowledge();
    }

    /** Deserialises and validates a record; a missing {@code timestampMs} falls back to the Kafka timestamp. */
    MarketEvent parse(ConsumerRecord<String, String> record) {
        if (record.value() == null || record.value().isBlank()) {
            throw new InvalidMarketEventException("Empty record at " + coordinates(record));
        }
        MarketEvent event;
        try {
            event = objectMapper.readValue(record.value(), MarketEvent.class);
        } catch (JsonProcessingException e) {
            throw new InvalidMarketEventException(
                    "Malformed MarketEvent JSON at " + coordinates(record) + ": " + e.getOriginalMessage(), e);
        }
        requireText(event.eventId(), "eventId", record);
        requireText(event.assetSymbol(), "assetSymbol", record);
        requireText(event.type(), "type", record);
        requireText(event.rawText(), "rawText", record);

        if (event.timestampMs() <= 0) {
            event = new MarketEvent(event.eventId(), event.assetSymbol(), event.type(),
                    event.rawText(), event.price(), record.timestamp());
        }
        return event;
    }

    private static void requireText(String value, String field, ConsumerRecord<?, ?> record) {
        if (value == null || value.isBlank()) {
            throw new InvalidMarketEventException(
                    "MarketEvent at " + coordinates(record) + " is missing required field '" + field + "'");
        }
    }

    private static String coordinates(ConsumerRecord<?, ?> record) {
        return record.topic() + "-" + record.partition() + "@" + record.offset();
    }
}
