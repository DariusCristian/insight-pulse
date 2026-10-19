package com.insightpulse.ingestion.analytics;

/**
 * Outcome of evaluating one sentiment score against its asset's rolling window.
 *
 * <p>{@code mean} and {@code std} describe the window <em>before</em> the evaluated score was added
 * (sample standard deviation, n-1). During warm-up, or when the window has zero variance,
 * {@code zScore} is {@code 0.0} and {@code isAnomaly} is {@code false}.
 *
 * @param zScore    standard score of the evaluated value against the window
 * @param mean      window mean before the value was added (0.0 if the window was empty)
 * @param std       window sample standard deviation before the value was added
 * @param isAnomaly {@code true} if {@code |zScore| >= threshold}
 */
public record AnomalyResult(double zScore, double mean, double std, boolean isAnomaly) {}
