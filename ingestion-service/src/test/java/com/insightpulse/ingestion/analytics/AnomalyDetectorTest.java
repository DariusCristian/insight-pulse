package com.insightpulse.ingestion.analytics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyDetectorTest {

    /** Feeds an alternating 0.0 / 0.2 series: mean 0.1, sample std sqrt(0.1 / 9) for n = 10. */
    private static void feedBaseline(AnomalyDetector detector, String symbol, int count) {
        for (int i = 0; i < count; i++) {
            detector.evaluate(symbol, i % 2 == 0 ? 0.0 : 0.2);
        }
    }

    @Test
    void computesZScoreAgainstWindowBeforeAddingTheValue() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);
        feedBaseline(detector, "AAPL", 10);

        AnomalyResult result = detector.evaluate("AAPL", 1.0);

        double expectedStd = Math.sqrt(0.1 / 9);
        assertThat(result.mean()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(result.std()).isCloseTo(expectedStd, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(result.zScore()).isCloseTo(0.9 / expectedStd, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(result.isAnomaly()).isTrue();
    }

    @Test
    void doesNotFlagValueWithinNormalRange() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);
        feedBaseline(detector, "AAPL", 10);

        AnomalyResult result = detector.evaluate("AAPL", 0.15);

        assertThat(Math.abs(result.zScore())).isLessThan(2.5);
        assertThat(result.isAnomaly()).isFalse();
    }

    @Test
    void flagsNegativeSpikesToo() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);
        feedBaseline(detector, "AAPL", 10);

        AnomalyResult result = detector.evaluate("AAPL", -1.0);

        assertThat(result.zScore()).isLessThan(-2.5);
        assertThat(result.isAnomaly()).isTrue();
    }

    @Test
    void neverFlagsDuringWarmUp() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);

        // Alternating extremes: the first 10 evaluations have fewer than 10 samples in the window.
        for (int i = 0; i < 10; i++) {
            AnomalyResult result = detector.evaluate("AAPL", i % 2 == 0 ? -1.0 : 1.0);
            assertThat(result.isAnomaly()).as("evaluation %d", i).isFalse();
            assertThat(result.zScore()).isZero();
        }
    }

    @Test
    void zeroVarianceWindowIsNotAnomalous() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);
        for (int i = 0; i < 10; i++) {
            detector.evaluate("AAPL", 0.5);
        }

        AnomalyResult result = detector.evaluate("AAPL", 0.9);

        assertThat(result.std()).isZero();
        assertThat(result.zScore()).isZero();
        assertThat(result.isAnomaly()).isFalse();
    }

    @Test
    void tracksSymbolsIndependently() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);
        feedBaseline(detector, "AAPL", 10);

        // BTC has no history, so it is still warming up regardless of AAPL's state.
        AnomalyResult btc = detector.evaluate("BTC-USD", 1.0);
        assertThat(btc.isAnomaly()).isFalse();
        assertThat(detector.sampleCount("BTC-USD")).isEqualTo(1);

        // And AAPL's window is unaffected by BTC.
        assertThat(detector.sampleCount("AAPL")).isEqualTo(10);
        assertThat(detector.evaluate("AAPL", 1.0).isAnomaly()).isTrue();
    }

    @Test
    void oldValuesAgeOutOfTheWindow() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 5, 2);
        feedBaseline(detector, "AAPL", 5);
        for (int i = 0; i < 5; i++) {
            detector.evaluate("AAPL", 1.0); // pushes the baseline out completely
        }

        AnomalyResult result = detector.evaluate("AAPL", 1.0);

        assertThat(detector.sampleCount("AAPL")).isEqualTo(5); // capped at window size
        assertThat(result.mean()).isEqualTo(1.0);
        assertThat(result.std()).isZero();
    }

    @Test
    void isThreadSafeUnderConcurrentVirtualThreads() throws Exception {
        int threads = 32;
        int perThread = 500;
        // Window larger than the total so that every evaluation must be retained.
        AnomalyDetector detector = new AnomalyDetector(2.5, 100_000, 10);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                Callable<Void> task = () -> {
                    for (int i = 0; i < perThread; i++) {
                        detector.evaluate("SHARED", Math.sin(id * 31 + i));
                        detector.evaluate("SYM-" + (id % 4), Math.cos(i));
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }
            for (Future<Void> future : futures) {
                future.get(); // propagates any exception thrown inside a task
            }
        }

        assertThat(detector.sampleCount("SHARED")).isEqualTo(threads * perThread);
        int perSymbol = (threads / 4) * perThread;
        for (int s = 0; s < 4; s++) {
            assertThat(detector.sampleCount("SYM-" + s)).isEqualTo(perSymbol);
        }
    }

    @Test
    void rejectsInvalidInput() {
        AnomalyDetector detector = new AnomalyDetector(2.5, 100, 10);

        assertThatThrownBy(() -> detector.evaluate(null, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detector.evaluate(" ", 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detector.evaluate("AAPL", Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detector.evaluate("AAPL", Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new AnomalyDetector(0, 100, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnomalyDetector(2.5, 100, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnomalyDetector(2.5, 5, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
