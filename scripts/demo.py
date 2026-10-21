#!/usr/bin/env python3
"""
demo.py — repeatable presentation demo for InsightPulse.

Plays a curated, fictional headline story (scripts/demo_headlines.json) through
the real pipeline: Kafka -> ingestion -> gRPC sentiment -> anomaly detection ->
PostgreSQL -> dashboard.

Commands:
    check    Score every headline with the running model and replay the scores
             through a copy of the anomaly detector. Shows exactly which events
             will be flagged. Run this before every presentation.
    prepare  Delete previous demo rows, restart ingestion-service (clears the
             in-memory anomaly windows), then send the warm-up headlines.
             Reload the dashboard afterwards for a clean screen.
    run      Stream the story; pauses before the shock event until you press Enter.

Typical presentation:
    python3 scripts/demo.py check      # once, beforehand
    python3 scripts/demo.py prepare    # right before presenting, then reload http://localhost:3000
    python3 scripts/demo.py run        # live

Only the Python standard library and Docker are required.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from simulate_events import KEY_SEPARATOR, open_producer  # noqa: E402

DATASET = Path(__file__).with_name("demo_headlines.json")
EVENT_ID_PREFIX = "demo-"
KAFKA = "insightpulse-kafka"
POSTGRES = "insightpulse-postgres"
INGESTION = "insightpulse-ingestion"
INFERENCE = "insightpulse-inference"

# Must match ingestion-service application.yml (app.anomaly.*).
THRESHOLD = 2.5
WINDOW_SIZE = 100
MIN_SAMPLES = 10


# ─── Dataset ─────────────────────────────────────────────────────────────────

@dataclass
class DemoEvent:
    phase: str
    symbol: str
    text: str
    price_move: float = 0.0
    expect_anomaly: bool = False


def interleave(by_symbol: dict[str, list[str]], phase: str) -> list[DemoEvent]:
    """Round-robin across symbols so the stream looks like a mixed market feed."""
    queues = {s: list(texts) for s, texts in by_symbol.items()}
    out: list[DemoEvent] = []
    while any(queues.values()):
        for symbol, texts in queues.items():
            if texts:
                out.append(DemoEvent(phase, symbol, texts.pop(0)))
    return out


def load_dataset(path: Path = DATASET) -> tuple[dict[str, float], dict[str, list[DemoEvent]]]:
    data = json.loads(path.read_text())
    phases = {
        "warmup": interleave(data["warmup"], "warmup"),
        "stream": interleave(data["stream"], "stream"),
        "shock": [
            DemoEvent("shock", e["symbol"], e["text"], e.get("price_move", 0.0), e.get("expect_anomaly", False))
            for e in data["shock"]
        ],
        "after": interleave(data.get("after", {}), "after"),
    }
    prices = data["base_prices"]
    for events in phases.values():
        for e in events:
            if e.symbol not in prices:
                raise ValueError(f"no base price for symbol {e.symbol}")
            if not e.text.strip():
                raise ValueError(f"empty headline for {e.symbol}")
    return prices, phases


def all_in_order(phases: dict[str, list[DemoEvent]]) -> list[DemoEvent]:
    return phases["warmup"] + phases["stream"] + phases["shock"] + phases["after"]


# ─── Anomaly detector replica (mirrors AnomalyDetector.java) ─────────────────

@dataclass
class Detector:
    threshold: float = THRESHOLD
    window_size: int = WINDOW_SIZE
    min_samples: int = MIN_SAMPLES
    windows: dict[str, list[float]] = field(default_factory=dict)

    def evaluate(self, symbol: str, score: float) -> tuple[float, bool]:
        """Z-score against the window *before* adding the score, then add it."""
        window = self.windows.setdefault(symbol, [])
        z, anomaly = 0.0, False
        n = len(window)
        if n >= self.min_samples:
            mean = sum(window) / n
            std = math.sqrt(sum((v - mean) ** 2 for v in window) / (n - 1))
            if std > 0:
                z = (score - mean) / std
                anomaly = abs(z) >= self.threshold
        window.append(score)
        if len(window) > self.window_size:
            window.pop(0)
        return z, anomaly


def severity(z: float) -> str:
    m = abs(z)
    return "HIGH" if m >= 4 else "MEDIUM" if m >= 3 else "LOW"


# ─── Docker helpers ──────────────────────────────────────────────────────────

def docker(*args: str, input_text: str | None = None, timeout: int = 60) -> str:
    result = subprocess.run(["docker", *args], input=input_text, capture_output=True, text=True, timeout=timeout)
    if result.returncode != 0:
        sys.exit(f"error: docker {' '.join(args[:3])} failed:\n{result.stderr.strip()}")
    return result.stdout


def psql(sql: str) -> str:
    return docker("exec", POSTGRES, "psql", "-U", "insightpulse", "-d", "insightpulse", "-At", "-c", sql).strip()


SCORE_SCRIPT = """
import sys, json, grpc
from inference_service.proto_generated import sentiment_pb2 as pb, sentiment_pb2_grpc as g
stub = g.SentimentServiceStub(grpc.insecure_channel("localhost:50051"))
for line in sys.stdin:
    r = stub.Analyze(pb.SentimentRequest(text=json.loads(line), event_id="demo-check", asset_symbol="CHECK"), timeout=15)
    print(json.dumps([round(r.score, 4), r.label]), flush=True)
"""


def score_texts(texts: list[str]) -> list[tuple[float, str]]:
    """Score headlines with the running model via gRPC inside the inference container."""
    out = docker("exec", "-i", INFERENCE, "python", "-c", SCORE_SCRIPT,
                 input_text="".join(json.dumps(t) + "\n" for t in texts), timeout=300)
    return [tuple(json.loads(line)) for line in out.splitlines()]


# ─── Commands ────────────────────────────────────────────────────────────────

def predict(phases: dict[str, list[DemoEvent]], scores: list[tuple[float, str]]) -> list[tuple[DemoEvent, float, str, float, bool]]:
    detector = Detector()
    rows = []
    for event, (score, label) in zip(all_in_order(phases), scores):
        z, anomaly = detector.evaluate(event.symbol, score)
        rows.append((event, score, label, z, anomaly))
    return rows


def cmd_check(args: argparse.Namespace) -> int:
    _, phases = load_dataset(args.dataset)
    events = all_in_order(phases)
    print(f"Scoring {len(events)} headlines with the running model...", file=sys.stderr)
    rows = predict(phases, score_texts([e.text for e in events]))

    problems = 0
    for event, score, label, z, anomaly in rows:
        if event.phase == "warmup" and not args.verbose:
            continue
        mark = ""
        if anomaly and not event.expect_anomaly:
            mark, problems = "  <-- UNEXPECTED anomaly", problems + 1
        elif event.expect_anomaly and not anomaly:
            mark, problems = "  <-- expected anomaly NOT triggered", problems + 1
        flag = f"ANOMALY {severity(z):<6}" if anomaly else " " * 14
        print(f"{event.phase:<6} {event.symbol:<5} {score:+.2f} {label:<8} z={z:+7.2f} {flag} {event.text[:60]}{mark}")

    warm = [r for r in rows if r[0].phase == "warmup"]
    print(f"\nwarm-up: {len(warm)} events, negative-scored: {sum(1 for r in warm if r[1] < 0)}")
    if problems:
        print(f"FAIL: {problems} problem(s). Edit {args.dataset.name} and re-run check.")
        return 1
    print("OK: anomalies trigger exactly on the shock event(s).")
    return 0


def send(events: list[DemoEvent], prices: dict[str, float], rate: float, rng: random.Random, log: bool) -> None:
    producer = open_producer(KAFKA, "market-events")
    try:
        for e in events:
            drift = e.price_move if e.price_move else rng.uniform(-0.004, 0.006)
            prices[e.symbol] = round(prices[e.symbol] * (1 + drift), 2)
            payload = {
                "eventId": f"{EVENT_ID_PREFIX}{uuid.uuid4()}",
                "assetSymbol": e.symbol,
                "type": "NEWS",
                "rawText": e.text,
                "price": prices[e.symbol],
                "timestampMs": int(time.time() * 1000),
            }
            producer.stdin.write(f"{e.symbol}{KEY_SEPARATOR}{json.dumps(payload)}\n")
            producer.stdin.flush()
            if log:
                print(f"  {e.symbol:<5} {e.text}")
            if rate > 0:
                time.sleep(1.0 / rate)
    finally:
        producer.stdin.close()
        producer.wait(timeout=30)


def wait_for_rows(expected: int, timeout: float = 90) -> int:
    deadline = time.time() + timeout
    count = 0
    while time.time() < deadline:
        count = int(psql(f"select count(*) from sentiment_alerts where event_id like '{EVENT_ID_PREFIX}%'"))
        if count >= expected:
            break
        time.sleep(2)
    return count


def cmd_prepare(args: argparse.Namespace) -> int:
    prices, phases = load_dataset(args.dataset)
    deleted = psql(
        f"with a as (delete from sentiment_alerts where event_id like '{EVENT_ID_PREFIX}%' returning 1), "
        f"e as (delete from market_events where event_id like '{EVENT_ID_PREFIX}%' returning 1) "
        f"select (select count(*) from a) || ' alerts, ' || (select count(*) from e) || ' events'"
    )
    print(f"Deleted previous demo rows: {deleted}")

    print("Restarting ingestion-service (clears anomaly windows)...")
    docker("restart", INGESTION, timeout=120)
    deadline = time.time() + 120
    while docker("inspect", "-f", "{{.State.Health.Status}}", INGESTION).strip() != "healthy":
        if time.time() > deadline:
            sys.exit("error: ingestion-service did not become healthy")
        time.sleep(3)
    time.sleep(5)  # let the Kafka consumer join its group

    warmup = phases["warmup"]
    print(f"Sending {len(warmup)} warm-up headlines...")
    send(warmup, dict(prices), rate=25, rng=random.Random(1), log=False)
    count = wait_for_rows(len(warmup))
    if count < len(warmup):
        sys.exit(f"error: only {count}/{len(warmup)} warm-up events were processed")
    print(f"Ready: {count} warm-up events processed. Reload http://localhost:3000, then run: python3 scripts/demo.py run")
    return 0


def cmd_run(args: argparse.Namespace) -> int:
    prices, phases = load_dataset(args.dataset)
    rng = random.Random(2)
    send_prices = dict(prices)

    print(f"Streaming {len(phases['stream'])} market headlines...")
    send(phases["stream"], send_prices, args.rate, rng, log=True)

    if not args.no_pause:
        input("\n>>> Press Enter to trigger the market shock... ")
    print("Shock:")
    send(phases["shock"], send_prices, args.rate, rng, log=True)

    if phases["after"]:
        time.sleep(2)
        print("Market continues...")
        send(phases["after"], send_prices, args.rate, rng, log=True)
    print("Demo finished.")
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--dataset", type=Path, default=DATASET)
    sub = p.add_subparsers(dest="command", required=True)
    c = sub.add_parser("check", help="predict sentiment and anomalies for every headline")
    c.add_argument("--verbose", action="store_true", help="also list warm-up events")
    sub.add_parser("prepare", help="reset demo data, clear anomaly windows, send warm-up")
    r = sub.add_parser("run", help="stream the story (pauses before the shock)")
    r.add_argument("--rate", type=float, default=1.5, help="events per second (default 1.5)")
    r.add_argument("--no-pause", action="store_true", help="do not wait for Enter before the shock")
    args = p.parse_args(argv)
    return {"check": cmd_check, "prepare": cmd_prepare, "run": cmd_run}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
