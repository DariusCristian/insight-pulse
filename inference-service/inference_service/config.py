"""
inference_service/config.py
----------------------------
Centralised settings for the inference service.
Values are read from environment variables (or a .env file at startup).
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application-wide configuration loaded from the environment."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # ------------------------------------------------------------------ server
    http_port: int = Field(default=8000, description="Port for the uvicorn HTTP server")
    grpc_port: int = Field(default=50051, description="Port for the gRPC server")

    # ------------------------------------------------------------------ model
    model_name: str = Field(
        default="distilbert-base-uncased-finetuned-sst-2-english",
        description="HuggingFace model identifier used for sentiment analysis",
    )

    # ---------------------------------------------------------------- logging
    log_level: str = Field(
        default="info",
        description="Logging level: debug | info | warning | error | critical",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return a cached singleton Settings instance."""
    return Settings()
