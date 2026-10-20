package com.insightpulse.ingestion.service;

import com.insightpulse.ingestion.analytics.AnomalyDetector;
import com.insightpulse.ingestion.entity.MarketEventEntity;
import com.insightpulse.ingestion.entity.SentimentAlertEntity;
import com.insightpulse.ingestion.grpc.SentimentGrpcClient;
import com.insightpulse.ingestion.model.AnomalyAlert;
import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.ingestion.repository.MarketEventRepository;
import com.insightpulse.ingestion.repository.SentimentAlertRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketEventPipelineTest {

    private static final long NOW_MS = 1_800_000_000_000L;

    private MarketEventRepository eventRepo;
    private SentimentAlertRepository alertRepo;
    private SentimentGrpcClient grpc;
    private AlertDispatchService dispatch;
    // Real detector with small windows so anomalies are easy to trigger.
    private AnomalyDetector detector;
    private MarketEventPipeline pipeline;

    @BeforeEach
    void setUp() {
        eventRepo = mock(MarketEventRepository.class);
        alertRepo = mock(SentimentAlertRepository.class);
        grpc = mock(SentimentGrpcClient.class);
        dispatch = mock(AlertDispatchService.class);
        detector = new AnomalyDetector(2.5, 20, 3);
        Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW_MS), ZoneOffset.UTC);
        pipeline = new MarketEventPipeline(eventRepo, alertRepo, grpc, detector, dispatch, clock);
    }

    private static MarketEvent event(String id) {
        return new MarketEvent(id, "AAPL", "NEWS", "Apple beats earnings", 190.5, 1_700_000_000_000L);
    }

    private void stubScore(String id, double score) {
        when(grpc.analyzeSentiment(event(id)))
                .thenReturn(new SentimentAlert(id, "AAPL", score, "POSITIVE", 1_700_000_000_500L));
    }

    @Test
    void processesEventInSpecifiedOrderAndPersistsBothRows() {
        stubScore("e1", 0.8);

        assertThat(pipeline.process(event("e1"))).isTrue();

        InOrder order = inOrder(eventRepo, grpc, alertRepo, dispatch);
        ArgumentCaptor<MarketEventEntity> eventRow = ArgumentCaptor.forClass(MarketEventEntity.class);
        order.verify(eventRepo).save(eventRow.capture());
        order.verify(grpc).analyzeSentiment(event("e1"));
        ArgumentCaptor<SentimentAlertEntity> alertRow = ArgumentCaptor.forClass(SentimentAlertEntity.class);
        order.verify(alertRepo).save(alertRow.capture());
        order.verify(dispatch).dispatch(any(SentimentAlert.class));

        assertThat(eventRow.getValue().getEventId()).isEqualTo("e1");
        assertThat(eventRow.getValue().getPrice()).isEqualTo(190.5);
        assertThat(eventRow.getValue().getTimestamp()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));

        SentimentAlertEntity row = alertRow.getValue();
        assertThat(row.getEventId()).isEqualTo("e1");
        assertThat(row.getSentimentScore()).isEqualTo(0.8);
        assertThat(row.getPrimaryLabel()).isEqualTo("POSITIVE");
        assertThat(row.getIsAnomaly()).isFalse();
        assertThat(row.getProcessedAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_500L));

        verify(dispatch, never()).dispatchAnomaly(any());
    }

    @Test
    void skipsDuplicateEventWithoutCallingInferenceOrBroadcasting() {
        when(alertRepo.existsByEventId("e1")).thenReturn(true);

        assertThat(pipeline.process(event("e1"))).isFalse();

        verifyNoInteractions(grpc, dispatch, eventRepo);
        verify(alertRepo, never()).save(any());
    }

    @Test
    void flagsAndBroadcastsAnomalyWhenScoreSpikes() {
        // Warm-up: a stable window around 0.1 (non-zero variance).
        double[] warmUp = {0.10, 0.12, 0.08, 0.11, 0.09};
        for (int i = 0; i < warmUp.length; i++) {
            stubScore("w" + i, warmUp[i]);
            pipeline.process(event("w" + i));
        }
        verify(dispatch, never()).dispatchAnomaly(any());

        stubScore("spike", -0.95);
        pipeline.process(event("spike"));

        ArgumentCaptor<SentimentAlertEntity> rows = ArgumentCaptor.forClass(SentimentAlertEntity.class);
        verify(alertRepo, times(6)).save(rows.capture());
        assertThat(rows.getValue().getIsAnomaly()).isTrue();

        ArgumentCaptor<AnomalyAlert> anomaly = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(dispatch).dispatchAnomaly(anomaly.capture());
        AnomalyAlert a = anomaly.getValue();
        assertThat(a.alertId()).isNotBlank();
        assertThat(a.assetSymbol()).isEqualTo("AAPL");
        assertThat(a.direction()).isEqualTo(AnomalyAlert.BEARISH_SPIKE);
        assertThat(a.zScore()).isLessThan(-2.5);
        assertThat(a.severity()).isEqualTo(AnomalyAlert.SEVERITY_HIGH);
        assertThat(a.triggeringScore()).isEqualTo(-0.95);
        assertThat(a.triggeringEventId()).isEqualTo("spike");
        assertThat(a.windowMean()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(a.triggeredAtMs()).isEqualTo(NOW_MS);
        assertThat(a.description()).contains("AAPL");
        // The sentiment alert is broadcast as well as the anomaly.
        verify(dispatch, times(6)).dispatch(any(SentimentAlert.class));
    }

    @Test
    void grpcFailurePropagatesAndNothingIsStoredOrBroadcast() {
        when(grpc.analyzeSentiment(event("e1"))).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> pipeline.process(event("e1"))).isInstanceOf(StatusRuntimeException.class);

        verify(eventRepo).save(any());          // raw event is kept for the retry / audit
        verify(alertRepo, never()).save(any());
        verifyNoInteractions(dispatch);
    }

    @Test
    void severityBandsFollowSpecification() {
        assertThat(AnomalyAlert.severityFor(2.5)).isEqualTo(AnomalyAlert.SEVERITY_LOW);
        assertThat(AnomalyAlert.severityFor(-3.0)).isEqualTo(AnomalyAlert.SEVERITY_MEDIUM);
        assertThat(AnomalyAlert.severityFor(3.99)).isEqualTo(AnomalyAlert.SEVERITY_MEDIUM);
        assertThat(AnomalyAlert.severityFor(-4.0)).isEqualTo(AnomalyAlert.SEVERITY_HIGH);
    }
}
