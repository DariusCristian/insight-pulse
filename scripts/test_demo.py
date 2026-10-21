"""Tests for demo.py — run with: python3 -m unittest discover -s scripts"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import demo  # noqa: E402


class DetectorReplicaTest(unittest.TestCase):
    """Mirrors the key cases of AnomalyDetectorTest.java."""

    def test_no_flag_during_warm_up(self):
        d = demo.Detector(threshold=2.5, window_size=100, min_samples=10)
        for i in range(9):
            self.assertEqual((0.0, False), d.evaluate("X", 0.1 * (i % 2)))
        z, anomaly = d.evaluate("X", -5.0)  # 10th value: window holds only 9
        self.assertEqual((0.0, False), (z, anomaly))

    def test_flags_spike_against_window_before_adding_it(self):
        d = demo.Detector(threshold=2.5, window_size=100, min_samples=3)
        for v in (0.9, 1.0, 0.9, 1.0):
            d.evaluate("X", v)
        z, anomaly = d.evaluate("X", -1.0)
        self.assertTrue(anomaly)
        self.assertLess(z, -2.5)
        self.assertEqual(5, len(d.windows["X"]))

    def test_zero_variance_window_never_flags(self):
        d = demo.Detector(min_samples=3)
        for _ in range(5):
            d.evaluate("X", 1.0)
        self.assertEqual((0.0, False), d.evaluate("X", -1.0))

    def test_window_is_capped(self):
        d = demo.Detector(window_size=10, min_samples=2)
        for i in range(25):
            d.evaluate("X", float(i))
        self.assertEqual(list(map(float, range(15, 25))), d.windows["X"])

    def test_symbols_are_independent(self):
        d = demo.Detector(min_samples=2)
        d.evaluate("A", 1.0)
        d.evaluate("A", 0.9)
        self.assertNotIn("B", d.windows)
        self.assertEqual((0.0, False), d.evaluate("B", -1.0))

    def test_severity_bands(self):
        self.assertEqual("LOW", demo.severity(2.5))
        self.assertEqual("MEDIUM", demo.severity(-3.0))
        self.assertEqual("HIGH", demo.severity(4.0))


class DatasetTest(unittest.TestCase):

    def test_interleave_round_robin(self):
        events = demo.interleave({"A": ["a1", "a2", "a3"], "B": ["b1"]}, "p")
        self.assertEqual(["a1", "b1", "a2", "a3"], [e.text for e in events])

    def test_bundled_dataset_is_valid(self):
        prices, phases = demo.load_dataset()
        self.assertTrue(any(e.expect_anomaly for e in phases["shock"]))
        warm = phases["warmup"]
        for symbol in prices:
            count = sum(1 for e in warm if e.symbol == symbol)
            if count:
                self.assertGreaterEqual(count, demo.MIN_SAMPLES, f"{symbol} needs >= {demo.MIN_SAMPLES} warm-up events")
        for e in phases["shock"]:
            self.assertGreaterEqual(sum(1 for w in warm if w.symbol == e.symbol), demo.MIN_SAMPLES)

    def test_predict_uses_dataset_order(self):
        _, phases = demo.load_dataset()
        events = demo.all_in_order(phases)
        # Fake scores: positive with a little variance *per symbol*, shock strongly negative.
        seen: dict[str, int] = {}
        scores = []
        for e in events:
            seen[e.symbol] = seen.get(e.symbol, 0) + 1
            scores.append((-1.0, "negative") if e.phase == "shock"
                          else (0.8 + 0.2 * (seen[e.symbol] % 2), "positive"))
        rows = demo.predict(phases, scores)
        flagged = [r[0] for r in rows if r[4]]
        self.assertEqual([e for e in events if e.expect_anomaly], flagged)


if __name__ == "__main__":
    unittest.main()
