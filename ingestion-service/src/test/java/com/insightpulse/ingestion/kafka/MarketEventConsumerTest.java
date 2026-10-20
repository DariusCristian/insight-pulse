package com.insightpulse.ingestion.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.service.MarketEventPipeline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketEventConsumerTest {

    private static final long KAFKA_TS = 1_750_000_000_000L;

    private MarketEventPipeline pipeline;
    private Acknowledgment ack;
    private MarketEventConsumer consumer;

    @BeforeEach
    void setUp() {
        pipeline = mock(MarketEventPipeline.class);
        ack = mock(Acknowledgment.class);
        // Mirror Spring Boot's ObjectMapper defaults (unknown properties are ignored).
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        consumer = new MarketEventConsumer(mapper, pipeline);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("market-events", 0, 42L, KAFKA_TS, TimestampType.CREATE_TIME,
                -1, -1, null, value, new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
    }

    @Test
    void parsesProcessesAndAcknowledgesValidEvent() {
        consumer.consume(record("""
                {"eventId":"e1","assetSymbol":"AAPL","type":"NEWS","rawText":"Apple beats",
                 "price":190.5,"timestampMs":1700000000000,"extra":"ignored"}"""), ack);

        verify(pipeline).process(new MarketEvent("e1", "AAPL", "NEWS", "Apple beats", 190.5, 1_700_000_000_000L));
        verify(ack).acknowledge();
    }

    @Test
    void missingTimestampFallsBackToKafkaRecordTimestamp() {
        consumer.consume(record("""
                {"eventId":"e1","assetSymbol":"AAPL","type":"NEWS","rawText":"Apple beats"}"""), ack);

        verify(pipeline).process(new MarketEvent("e1", "AAPL", "NEWS", "Apple beats", 0.0, KAFKA_TS));
        verify(ack).acknowledge();
    }

    @Test
    void malformedJsonIsRejectedWithoutAckOrProcessing() {
        assertThatThrownBy(() -> consumer.consume(record("{not json"), ack))
                .isInstanceOf(InvalidMarketEventException.class)
                .hasMessageContaining("market-events-0@42");

        verifyNoInteractions(pipeline, ack);
    }

    @Test
    void missingRequiredFieldIsRejected() {
        assertThatThrownBy(() -> consumer.consume(record("""
                {"eventId":"e1","assetSymbol":"AAPL","type":"NEWS","rawText":"  "}"""), ack))
                .isInstanceOf(InvalidMarketEventException.class)
                .hasMessageContaining("rawText");

        verifyNoInteractions(pipeline, ack);
    }

    @Test
    void emptyRecordIsRejected() {
        assertThatThrownBy(() -> consumer.consume(record(null), ack))
                .isInstanceOf(InvalidMarketEventException.class);
        verifyNoInteractions(pipeline, ack);
    }

    @Test
    void pipelineFailureIsNotAcknowledgedSoErrorHandlerCanRetry() {
        when(pipeline.process(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> consumer.consume(record("""
                {"eventId":"e1","assetSymbol":"AAPL","type":"NEWS","rawText":"x","timestampMs":1}"""), ack))
                .isInstanceOf(StatusRuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void duplicateEventIsStillAcknowledged() {
        when(pipeline.process(any())).thenReturn(false);

        consumer.consume(record("""
                {"eventId":"e1","assetSymbol":"AAPL","type":"NEWS","rawText":"x","timestampMs":1}"""), ack);

        verify(ack).acknowledge();
    }
}
