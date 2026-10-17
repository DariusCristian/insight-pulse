package com.insightpulse.ingestion.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA Entity representing a market event stored in the database.
 * This table captures all raw events ingested from the Kafka stream.
 */
@Entity
@Table(name = "market_events")
public class MarketEventEntity {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String assetSymbol;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String rawText;

    private Double price;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor
    public MarketEventEntity() {
        this.createdAt = Instant.now();
    }

    // Getters and Setters
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
