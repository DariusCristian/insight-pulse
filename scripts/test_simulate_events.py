"""Tests for simulate_events.py — run with: python3 -m unittest discover scripts"""

import io
import json
import re
import sys
import unittest
import unittest.mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import simulate_events as sim  # noqa: E402

REQUIRED = {"eventId", "assetSymbol", "type", "rawText", "price", "timestampMs"}


class EventGeneratorTest(unittest.TestCase):

    def gen(self, **kw):
        opts = dict(symbols=["AAPL", "TSLA"], shock_prob=0.1, trade_prob=0.1, malformed_every=0, seed=42)
        opts.update(kw)
        return sim.EventGenerator(**opts)

    def test_events_match_market_event_contract(self):
        for key, value, kind in self.gen().lines(200):
            event = json.loads(value)
            self.assertEqual(REQUIRED, set(event))
            self.assertEqual(key, event["assetSymbol"])
            self.assertIn(event["type"], {"NEWS", "TRADE"})
            self.assertTrue(event["rawText"].strip())
            self.assertGreater(event["price"], 0)
            self.assertNotIn("{", event["rawText"], "unformatted template placeholder")

    def test_event_ids_are_unique(self):
        ids = [json.loads(v)["eventId"] for _, v, _ in self.gen().lines(500)]
        self.assertEqual(len(ids), len(set(ids)))

    def test_same_seed_gives_same_texts(self):
        texts = lambda: [json.loads(v)["rawText"] for _, v, _ in self.gen().lines(50)]
        self.assertEqual(texts(), texts())

    def test_same_seed_still_gives_new_event_ids(self):
        ids = lambda: {json.loads(v)["eventId"] for _, v, _ in self.gen().lines(50)}
        self.assertFalse(ids() & ids())

    def test_shock_headlines_go_against_the_regime(self):
        gen = self.gen(symbols=["AAPL"], shock_prob=1.0, trade_prob=0.0)
        expected = sim.NEGATIVE if gen.assets[0].bullish else sim.POSITIVE
        # Turn each template into a regex: placeholders match any text.
        patterns = [re.compile(re.sub(r"\\\{\w+\\\}", ".+", re.escape(t)) + "$") for t in expected]
        for _, value, kind in gen.lines(20):
            self.assertEqual("SHOCK", kind)
            text = json.loads(value)["rawText"]
            self.assertTrue(any(p.match(text) for p in patterns), text)

    def test_malformed_every_n_is_invalid_json(self):
        kinds = [k for _, _, k in self.gen(malformed_every=5).lines(20)]
        self.assertEqual(4, kinds.count("MALFORMED"))
        _, value, _ = self.gen(malformed_every=1).next_line()
        with self.assertRaises(json.JSONDecodeError):
            json.loads(value)


class CliTest(unittest.TestCase):

    def test_dry_run_prints_one_json_per_line(self):
        out = io.StringIO()
        args = sim.parse_args(["--dry-run", "--count", "7", "--rate", "0", "--seed", "1"])
        stats = sim.run(args, out)
        lines = out.getvalue().splitlines()
        self.assertEqual(7, len(lines))
        self.assertEqual(7, sum(stats.values()))
        for line in lines:
            self.assertEqual(REQUIRED, set(json.loads(line)))

    def test_rejects_invalid_probabilities(self):
        with self.assertRaises(SystemExit), unittest.mock.patch("sys.stderr", io.StringIO()):
            sim.parse_args(["--shock-prob", "2"])


if __name__ == "__main__":
    unittest.main()
