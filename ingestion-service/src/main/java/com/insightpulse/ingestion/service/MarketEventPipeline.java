package com.insightpulse.ingestion.service;

import com.insightpulse.ingestion.analytics.AnomalyDetector;
import com.insightpulse.ingestion.analytics.AnomalyResult;
import com.insightpulse.ingestion.entity.MarketEventEntity;
import com.insightpulse.ingestion.entity.SentimentAlertEntity;
import com.insightpulse.ingestion.grpc.SentimentGrpcClient;
import com.insightpulse.ingestion.model.AnomalyAlert;
import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.ingestion.repository.MarketEventRepository;
import com.insightpulse.ingestion.repository.SentimentAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The per-event processing pipeline (SPECIFICATION.md §8):
 * persist raw event → gRPC sentiment → rolling anomaly check → persist alert → WebSocket broadcast.
 *
 * <p>No database transaction spans the gRPC call; each save commits on its own so a slow
 * inference call never holds a DB connection.
 *
 * <p>Kafka delivers at-least-once. An event whose alert is already stored is skipped, so a
 * re-delivered record is not scored, broadcast or added to the anomaly window twice.
 * Any exception propagates to the Kafka error handler (retry, then dead-letter topic).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketEventPipeline {

    private final MarketEventRepository marketEventRepository;
    private final SentimentAlertRepository sentimentAlertRepository;
    private final SentimentGrpcClient sentimentGrpcClient;
    private final AnomalyDetector anomalyDetector;
    private final AlertDispatchService alertDispatchService;
    private final Clock clock;

    /**
     * Processes one validated event end to end.
     *
     * @return {@code true} if processed, {@code false} if skipped as a duplicate
     */
    public boolean process(MarketEvent event) {
        if (sentimentAlertRepository.existsByEventId(event.eventId())) {
            log.info("Skipping already-processed event {}", event.eventId());
            return false;
        }

        marketEventRepository.save(toEntity(event));

        SentimentAlert alert = sentimentGrpcClient.analyzeSentiment(event);
        AnomalyResult anomaly = anomalyDetector.evaluate(event.assetSymbol(), alert.score());

        sentimentAlertRepository.save(toEntity(alert, anomaly.isAnomaly()));

        alertDispatchService.dispatch(alert);
        if (anomaly.isAnomaly()) {
            AnomalyAlert anomalyAlert = toAnomalyAlert(alert, anomaly);
            log.info("Anomaly {} for {}: z={} severity={}", anomalyAlert.alertId(),
                    event.assetSymbol(), String.format(Locale.ROOT, "%.2f", anomaly.zScore()),
                    anomalyAlert.severity());
            alertDispatchService.dispatchAnomaly(anomalyAlert);
        }
        return true;
    }

    private static MarketEventEntity toEntity(MarketEvent event) {
        MarketEventEntity entity = new MarketEventEntity();
        entity.setEventId(event.eventId());
        entity.setAssetSymbol(event.assetSymbol());
        entity.setType(event.type());
        entity.setRawText(event.rawText());
        entity.setPrice(event.price());
        entity.setTimestamp(Instant.ofEpochMilli(event.timestampMs()));
        return entity;
    }

    private static SentimentAlertEntity toEntity(SentimentAlert alert, boolean isAnomaly) {
        SentimentAlertEntity entity = new SentimentAlertEntity();
        entity.setEventId(alert.eventId());
        entity.setAssetSymbol(alert.assetSymbol());
        entity.setSentimentScore(alert.score());
        entity.setPrimaryLabel(alert.label());
        entity.setIsAnomaly(isAnomaly);
        entity.setProcessedAt(Instant.ofEpochMilli(alert.processedAtMs()));
        return entity;
    }

    private AnomalyAlert toAnomalyAlert(SentimentAlert alert, AnomalyResult anomaly) {
        String direction = anomaly.zScore() > 0 ? AnomalyAlert.BULLISH_SPIKE : AnomalyAlert.BEARISH_SPIKE;
        String description = String.format(Locale.ROOT,
                "%s sentiment %s: score %.2f vs window mean %.2f (z = %.2f)",
                alert.assetSymbol(),
                anomaly.zScore() > 0 ? "spiked up" : "dropped sharply",
                alert.score(), anomaly.mean(), anomaly.zScore());
        return new AnomalyAlert(
                UUID.randomUUID().toString(),
                alert.assetSymbol(),
                AnomalyAlert.severityFor(anomaly.zScore()),
                direction,
                anomaly.zScore(),
                alert.score(),
                anomaly.mean(),
                anomaly.std(),
                alert.eventId(),
                clock.millis(),
                description);
    }
}
