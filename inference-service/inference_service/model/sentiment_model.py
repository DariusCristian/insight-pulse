"""
inference_service/model/sentiment_model.py
-------------------------------------------
Wraps a HuggingFace sentiment-analysis pipeline and normalises the raw
model output to a continuous score in the range [-1.0, 1.0].

Score convention
----------------
  -1.0  → strongly negative
   0.0  → neutral / uncertain
  +1.0  → strongly positive

The underlying model (distilbert-base-uncased-finetuned-sst-2-english) outputs
a POSITIVE / NEGATIVE label together with a confidence in [0, 1].  We map that
to the normalised range as follows:

    score = confidence        if label == "POSITIVE"
    score = -confidence       if label == "NEGATIVE"

This preserves magnitude (how confident the model is) and encodes polarity
in the sign.
"""

from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


class SentimentModel:
    """Thin wrapper around a HuggingFace sentiment-analysis pipeline.

    Lifecycle
    ---------
    1. Instantiate with the desired HuggingFace model identifier.
    2. Call :meth:`load` once (e.g. during application startup) to download
       weights and initialise the pipeline.
    3. Call :meth:`predict` for each inference request.

    Thread safety
    -------------
    The underlying HuggingFace pipeline is **not** thread-safe for concurrent
    writes.  In production, either serialise calls with a lock or use a
    separate model instance per worker.
    """

    def __init__(self, model_name: str) -> None:
        """
        Parameters
        ----------
        model_name:
            HuggingFace Hub model identifier, e.g.
            ``"distilbert-base-uncased-finetuned-sst-2-english"``.
        """
        self._model_name: str = model_name
        self._pipeline: Any | None = None  # transformers.Pipeline, typed as Any to avoid import at module level

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def load(self) -> None:
        """Download (if necessary) and initialise the inference pipeline.

        This method is intentionally synchronous — HuggingFace pipeline
        construction is CPU-bound and should be called once at startup,
        outside of any async event loop.

        Raises
        ------
        RuntimeError
            If the model cannot be loaded (e.g. invalid identifier or no
            network access and cache miss).
        """
        from transformers import pipeline  # type: ignore[import-untyped]

        logger.info("Loading sentiment model: %s", self._model_name)
        self._pipeline = pipeline(
            task="sentiment-analysis",
            model=self._model_name,
            # Use CPU by default; override with device=0 for GPU
            device=-1,
        )
        logger.info("Model loaded successfully.")

    # ------------------------------------------------------------------
    # Inference
    # ------------------------------------------------------------------

    def predict(self, text: str) -> float:
        """Run sentiment inference on *text* and return a normalised score.

        Parameters
        ----------
        text:
            Non-empty input string to analyse.

        Returns
        -------
        float
            A value in [-1.0, 1.0] where:
            - negative values indicate negative sentiment,
            - positive values indicate positive sentiment,
            - values near 0 indicate low-confidence or borderline predictions.

        Raises
        ------
        RuntimeError
            If :meth:`load` has not been called prior to :meth:`predict`.
        ValueError
            If *text* is empty or whitespace-only.
        """
        if self._pipeline is None:
            raise RuntimeError(
                "SentimentModel.load() must be called before predict()."
            )
        if not text or not text.strip():
            raise ValueError("Input text must be non-empty.")

        result: list[dict[str, Any]] = self._pipeline(text)
        label: str = result[0]["label"]          # "POSITIVE" or "NEGATIVE"
        confidence: float = float(result[0]["score"])  # in [0, 1]

        # Normalise to [-1.0, 1.0]
        score: float = confidence if label == "POSITIVE" else -confidence
        return score
