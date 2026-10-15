"""
inference_service/api/schemas.py
---------------------------------
Pydantic request / response models for the HTTP API.

These models mirror the protobuf contracts defined in proto/sentiment.proto
so that the REST and gRPC surfaces stay in sync.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class SentimentRequest(BaseModel):
    """Payload for a POST /analyze request.

    Mirrors ``SentimentRequest`` in proto/sentiment.proto.
    """

    text: str = Field(
        ...,
        min_length=1,
        description="The raw text to analyse.  Must be non-empty.",
        examples=["The earnings report exceeded all expectations."],
    )


class SentimentResponse(BaseModel):
    """Response body for a POST /analyze request.

    Mirrors ``SentimentResponse`` in proto/sentiment.proto.

    Score convention
    ----------------
    -1.0  → strongly negative
     0.0  → neutral / low-confidence
    +1.0  → strongly positive
    """

    score: float = Field(
        ...,
        ge=-1.0,
        le=1.0,
        description="Normalised sentiment score in the range [-1.0, 1.0].",
        examples=[0.87],
    )
    label: str = Field(
        ...,
        description='Human-readable label, e.g. "POSITIVE" or "NEGATIVE".',
        examples=["POSITIVE"],
    )


class HealthResponse(BaseModel):
    """Response body for GET /health."""

    status: str = Field(default="ok", description="Service health status string.")
