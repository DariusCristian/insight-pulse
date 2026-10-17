package com.insightpulse.ingestion.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA Entity representing a sentiment analysis result with anomaly detection.
 * Stores the AI-generated sentiment scores and flags unusual patterns.
 */
@Entity
@Table(name = "sentiment_alerts")
public class SentimentAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String assetSymbol;

    @Column(nullable = false)
    private Double sentimentScore;

    @Column(nullable = false)
    private String primaryLabel;

    @Column(nullable = false)
    private Boolean isAnomaly;

    @Column(nullable = false)
    private Instant processedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor
    public SentimentAlertEntity() {
        this.createdAt = Instant.now();
        this.isAnomaly = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAssetSymbol() {
        return assetSymbol;
    }

    public void setAssetSymbol(String assetSymbol) {
        this.assetSymbol = assetSymbol;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(Double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public String getPrimaryLabel() {
        return primaryLabel;
    }

    public void setPrimaryLabel(String primaryLabel) {
        this.primaryLabel = primaryLabel;
    }

    public Boolean getIsAnomaly() {
        return isAnomaly;
    }

    public void setIsAnomaly(Boolean isAnomaly) {
        this.isAnomaly = isAnomaly;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
