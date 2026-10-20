package com.insightpulse.ingestion.model;

/**
 * Anomaly alert pushed on {@code /ws/stream} as {@code {type: "ANOMALY", payload: ...}}.
 *
 * <p>Field names match the frontend {@code AnomalyAlert} type
 * (dashboard-frontend/src/types/market.ts); the remaining fields carry the extra detail
 * described in SPECIFICATION.md §3.3.
 *
 * @param alertId           unique id for this alert (UUID v4)
 * @param assetSymbol       asset whose sentiment spiked
 * @param severity          {@code LOW} (|z| &lt; 3), {@code MEDIUM} (|z| &lt; 4) or {@code HIGH}
 * @param direction         {@code BULLISH_SPIKE} (z &gt; 0) or {@code BEARISH_SPIKE} (z &lt; 0)
 * @param zScore            Z-score of the triggering score against the rolling window
 * @param triggeringScore   the sentiment score that triggered the alert
 * @param windowMean        rolling-window mean before the score was added
 * @param windowStddev      rolling-window sample standard deviation before the score was added
 * @param triggeringEventId eventId of the MarketEvent that triggered the alert
 * @param triggeredAtMs     Unix epoch milliseconds when the anomaly was detected
 * @param description       human-readable summary for the dashboard
 */
public record AnomalyAlert(
        String alertId,
        String assetSymbol,
        String severity,
        String direction,
        double zScore,
        double triggeringScore,
        double windowMean,
        double windowStddev,
        String triggeringEventId,
        long triggeredAtMs,
        String description
) {

    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_HIGH = "HIGH";

    public static final String BULLISH_SPIKE = "BULLISH_SPIKE";
    public static final String BEARISH_SPIKE = "BEARISH_SPIKE";

    /** Severity bands from SPECIFICATION.md §3.3, applied to |z|. */
    public static String severityFor(double zScore) {
        double magnitude = Math.abs(zScore);
        if (magnitude >= 4.0) {
            return SEVERITY_HIGH;
        }
        if (magnitude >= 3.0) {
            return SEVERITY_MEDIUM;
        }
        return SEVERITY_LOW;
    }
}
