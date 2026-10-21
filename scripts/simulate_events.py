#!/usr/bin/env python3
"""
simulate_events.py — stream realistic MarketEvent JSON into the `market-events` topic.

Each asset follows a "regime" (mostly bullish or mostly bearish headlines), so its
rolling sentiment window is stable. With probability --shock-prob an event carries a
headline of the opposite polarity, which is what the ingestion-service anomaly
detector should flag. Prices follow a small random walk per asset.

Events are piped into `kafka-console-producer` inside the Kafka container, keyed by
asset symbol so all events of one asset land on the same partition (in order).
Only the Python standard library and Docker are required.

Examples:
    python3 scripts/simulate_events.py                      # 100 events, 2/s
    python3 scripts/simulate_events.py --count 0 --rate 5   # run until Ctrl+C
    python3 scripts/simulate_events.py --dry-run --count 5  # print JSON, send nothing
    python3 scripts/simulate_events.py --malformed-every 20 # every 20th event is invalid (→ DLQ)
"""

from __future__ import annotations

import argparse
import json
import random
import shutil
import subprocess
import sys
import time
import uuid
import zlib
from dataclasses import dataclass
from typing import Iterator, TextIO

DEFAULT_SYMBOLS = ["AAPL", "MSFT", "NVDA", "TSLA", "BTC-USD"]
KEY_SEPARATOR = "|"

POSITIVE = [
    "{name} beats earnings expectations as revenue jumps {pct}%",
    "{name} shares surge after record quarterly profit",
    "Analysts upgrade {name} to buy, citing strong demand",
    "{name} raises full-year guidance on robust sales",
    "{name} announces ${amt}B share buyback, investors cheer",
    "{name} wins major contract, stock climbs {pct}%",
    "Strong demand lifts {name} margins to a record high",
    "{name} posts better-than-expected growth across all segments",
]
NEGATIVE = [
    "{name} misses estimates and cuts full-year guidance",
    "{name} shares plunge {pct}% amid fraud investigation",
    "Regulators open probe into {name}, stock tumbles",
    "{name} warns of weak demand, announces layoffs",
    "{name} hit by massive data breach, customers furious",
    "Analysts downgrade {name} to sell on collapsing margins",
    "{name} recalls products after serious safety failures",
    "{name} reports surprise loss as costs soar",
]
TRADE = [
    "Large block trade in {name}: {qty} shares crossed at ${price}",
    "Unusual options activity in {name} ahead of earnings",
]

NAMES = {
    "AAPL": "Apple", "MSFT": "Microsoft", "NVDA": "Nvidia", "TSLA": "Tesla",
    "BTC-USD": "Bitcoin", "AMZN": "Amazon", "GOOG": "Alphabet", "META": "Meta",
}
START_PRICES = {"AAPL": 190.0, "MSFT": 410.0, "NVDA": 120.0, "TSLA": 240.0, "BTC-USD": 64000.0}


@dataclass
class Asset:
    symbol: str
    name: str
    price: float
    bullish: bool


def stable_regime(symbol: str) -> bool:
    """Bullish/bearish regime fixed per symbol (~70% bullish), identical across runs.

    The anomaly detector keeps a rolling window per symbol across runs; if the
    regime changed between runs the window would mix both polarities, its spread
    would grow, and shocks would no longer stand out as anomalies.
    """
    return zlib.crc32(symbol.encode()) % 10 < 7


class EventGenerator:
    """Deterministic (given a seed) generator of MarketEvent dicts."""

    def __init__(self, symbols: list[str], shock_prob: float, trade_prob: float,
                 malformed_every: int, seed: int | None) -> None:
        self.rng = random.Random(seed)
        self.shock_prob = shock_prob
        self.trade_prob = trade_prob
        self.malformed_every = malformed_every
        self.assets = [
            Asset(s, NAMES.get(s, s), START_PRICES.get(s, 100.0), bullish=stable_regime(s))
            for s in symbols
        ]
        self.produced = 0

    def next_line(self) -> tuple[str, str, str]:
        """Return (key, json_value, kind) where kind is NEWS/SHOCK/TRADE/MALFORMED."""
        self.produced += 1
        asset = self.rng.choice(self.assets)
        if self.malformed_every and self.produced % self.malformed_every == 0:
            return asset.symbol, '{"eventId": "broken", "assetSymbol": ', "MALFORMED"

        # Random walk: ±1.5% per event, never below 1 cent.
        asset.price = max(0.01, asset.price * (1 + self.rng.uniform(-0.015, 0.015)))
        fmt = {
            "name": asset.name,
            "pct": self.rng.randint(3, 18),
            "amt": self.rng.randint(5, 90),
            "qty": f"{self.rng.randint(50, 900) * 1000:,}",
            "price": f"{asset.price:,.2f}",
        }

        if self.rng.random() < self.trade_prob:
            kind, event_type, text = "TRADE", "TRADE", self.rng.choice(TRADE).format(**fmt)
        else:
            shock = self.rng.random() < self.shock_prob
            positive = asset.bullish != shock
            kind = "SHOCK" if shock else "NEWS"
            event_type = "NEWS"
            text = self.rng.choice(POSITIVE if positive else NEGATIVE).format(**fmt)

        event = {
            # Always random: --seed reproduces content, but re-runs must not reuse ids
            # (the pipeline skips already-processed eventIds).
            "eventId": str(uuid.uuid4()),
            "assetSymbol": asset.symbol,
            "type": event_type,
            "rawText": text,
            "price": round(asset.price, 2),
            "timestampMs": int(time.time() * 1000),
        }
        return asset.symbol, json.dumps(event, separators=(",", ":")), kind

    def lines(self, count: int) -> Iterator[tuple[str, str, str]]:
        while count <= 0 or self.produced < count:
            yield self.next_line()


def open_producer(container: str, topic: str) -> subprocess.Popen:
    if shutil.which("docker") is None:
        sys.exit("error: docker not found on PATH (use --dry-run to only print events)")
    cmd = [
        "docker", "exec", "-i", container, "kafka-console-producer",
        "--bootstrap-server", "localhost:9092", "--topic", topic,
        "--property", "parse.key=true", "--property", f"key.separator={KEY_SEPARATOR}",
    ]
    return subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, bufsize=1)


def run(args: argparse.Namespace, out: TextIO) -> dict[str, int]:
    gen = EventGenerator(args.symbols, args.shock_prob, args.trade_prob, args.malformed_every, args.seed)
    producer = None if args.dry_run else open_producer(args.container, args.topic)
    sink = out if producer is None else producer.stdin
    stats = {"NEWS": 0, "SHOCK": 0, "TRADE": 0, "MALFORMED": 0}
    interval = 1.0 / args.rate if args.rate > 0 else 0.0

    try:
        for key, value, kind in gen.lines(args.count):
            if producer is not None and producer.poll() is not None:
                sys.exit(f"error: kafka producer exited with code {producer.returncode}")
            sink.write(value + "\n" if producer is None else f"{key}{KEY_SEPARATOR}{value}\n")
            stats[kind] += 1
            if producer is not None and not args.quiet:
                print(f"[{gen.produced:>5}] {kind:<9} {key:<8} {value[:110]}", file=sys.stderr)
            if interval:
                time.sleep(interval)
    except (KeyboardInterrupt, BrokenPipeError):
        pass
    finally:
        if producer is not None:
            try:
                producer.stdin.close()
            except BrokenPipeError:
                pass
            producer.wait(timeout=30)
    return stats


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--count", type=int, default=100, help="events to send; 0 = until Ctrl+C (default 100)")
    p.add_argument("--rate", type=float, default=2.0, help="events per second; 0 = as fast as possible (default 2)")
    p.add_argument("--symbols", type=lambda s: [x.strip() for x in s.split(",") if x.strip()],
                   default=DEFAULT_SYMBOLS, help="comma-separated symbols (default %(default)s)")
    p.add_argument("--shock-prob", type=float, default=0.08,
                   help="probability a news event goes against the asset's regime (default 0.08)")
    p.add_argument("--trade-prob", type=float, default=0.1, help="probability of a TRADE event (default 0.1)")
    p.add_argument("--malformed-every", type=int, default=0,
                   help="send an invalid record every N events to exercise the DLQ (default off)")
    p.add_argument("--seed", type=int, default=None, help="random seed for reproducible runs")
    p.add_argument("--topic", default="market-events")
    p.add_argument("--container", default="insightpulse-kafka")
    p.add_argument("--dry-run", action="store_true", help="print events to stdout instead of sending")
    p.add_argument("--quiet", action="store_true", help="do not log each sent event")
    args = p.parse_args(argv)
    if not args.symbols:
        p.error("--symbols must not be empty")
    if not 0 <= args.shock_prob <= 1 or not 0 <= args.trade_prob <= 1:
        p.error("--shock-prob and --trade-prob must be between 0 and 1")
    if args.rate < 0 or args.malformed_every < 0:
        p.error("--rate and --malformed-every must be >= 0")
    return args


def main() -> None:
    args = parse_args()
    stats = run(args, sys.stdout)
    total = sum(stats.values())
    if not args.dry_run:
        print(f"sent {total} events to {args.topic}: " + ", ".join(f"{k}={v}" for k, v in stats.items()),
              file=sys.stderr)


if __name__ == "__main__":
    main()
