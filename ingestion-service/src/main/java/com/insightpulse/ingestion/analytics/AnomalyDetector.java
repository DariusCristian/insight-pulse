package com.insightpulse.ingestion.analytics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-asset rolling Z-score anomaly detector.
 *
 * <p>For each asset symbol the detector keeps the last {@code windowSize} sentiment scores.
 * A new score is first compared against that window (so it cannot dilute its own Z-score) and
 * then added to it. A score is anomalous when {@code |z| >= threshold}.
 *
 * <p>Nothing is flagged until the window holds at least {@code minSamples} scores, and a window with
 * zero variance yields {@code z = 0}. State is in memory only, so it is lost on restart.
 *
 * <p>Thread-safe: symbols are tracked in a {@link ConcurrentHashMap} and each symbol's window is
 * guarded by its own {@link ReentrantLock} (used instead of {@code synchronized} to avoid pinning
 * virtual threads). Different symbols never contend with each other.
 */
@Component
public class AnomalyDetector {

    private final double threshold;
    private final int windowSize;
    private final int minSamples;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AnomalyDetector(
            @Value("${app.anomaly.threshold:2.5}") double threshold,
            @Value("${app.anomaly.window-size:100}") int windowSize,
            @Value("${app.anomaly.min-samples:10}") int minSamples) {
        if (!(threshold > 0)) {
            throw new IllegalArgumentException("threshold must be > 0, was " + threshold);
        }
        if (minSamples < 2) {
            throw new IllegalArgumentException("minSamples must be >= 2, was " + minSamples);
        }
        if (windowSize < minSamples) {
            throw new IllegalArgumentException(
                    "windowSize (" + windowSize + ") must be >= minSamples (" + minSamples + ")");
        }
        this.threshold = threshold;
        this.windowSize = windowSize;
        this.minSamples = minSamples;
    }

    /**
     * Evaluates {@code score} against the rolling window for {@code assetSymbol}, then records it.
     *
     * @throws IllegalArgumentException if the symbol is null/blank or the score is NaN/infinite
     */
    public AnomalyResult evaluate(String assetSymbol, double score) {
        if (assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException("assetSymbol must not be blank");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite, was " + score);
        }
        return windows.computeIfAbsent(assetSymbol, k -> new Window(windowSize))
                .evaluateAndAdd(score, threshold, minSamples);
    }

    /** Number of scores currently held for a symbol (0 if unseen). Visible for testing. */
    int sampleCount(String assetSymbol) {
        Window window = windows.get(assetSymbol);
        return window == null ? 0 : window.count();
    }

    /** Fixed-size ring buffer of recent scores. */
    private static final class Window {
        private final double[] values;
        private final ReentrantLock lock = new ReentrantLock();
        private int size;
        private int next;

        Window(int capacity) {
            this.values = new double[capacity];
        }

        AnomalyResult evaluateAndAdd(double score, double threshold, int minSamples) {
            lock.lock();
            try {
                double mean = 0.0;
                double std = 0.0;
                if (size > 0) {
                    double sum = 0.0;
                    for (int i = 0; i < size; i++) {
                        sum += values[i];
                    }
                    mean = sum / size;
                }
                if (size > 1) {
                    // Two-pass computation: numerically stable and cheap for small windows.
                    double squaredDeviations = 0.0;
                    for (int i = 0; i < size; i++) {
                        double d = values[i] - mean;
                        squaredDeviations += d * d;
                    }
                    std = Math.sqrt(squaredDeviations / (size - 1));
                }

                double z = 0.0;
                boolean anomaly = false;
                if (size >= minSamples && std > 0.0) {
                    z = (score - mean) / std;
                    anomaly = Math.abs(z) >= threshold;
                }

                values[next] = score;
                next = (next + 1) % values.length;
                if (size < values.length) {
                    size++;
                }
                return new AnomalyResult(z, mean, std, anomaly);
            } finally {
                lock.unlock();
            }
        }

        int count() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }
    }
}
