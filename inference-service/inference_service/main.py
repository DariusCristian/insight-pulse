"""
inference_service/main.py
--------------------------
FastAPI application factory and entry-point.

Startup sequence (managed by the async lifespan context manager):
  1. Load application settings from the environment.
  2. Initialise and warm up the SentimentModel (CPU-bound, runs in a thread).
  3. Create and start the async gRPC server on GRPC_PORT.

Shutdown sequence:
  4. Gracefully stop the gRPC server (5-second grace period).

Run locally:
    uvicorn inference_service.main:app --reload --port 8000
"""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI

from inference_service.config import get_settings
from inference_service.model.sentiment_model import SentimentModel
from inference_service.api.routes import health

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Module-level singletons (populated during lifespan startup)
# ---------------------------------------------------------------------------
_sentiment_model: SentimentModel | None = None


def get_model() -> SentimentModel:
    """FastAPI dependency — returns the loaded model singleton.

    Raises
    ------
    RuntimeError
        If called before the application lifespan has completed startup.
    """
    if _sentiment_model is None:
        raise RuntimeError("SentimentModel is not initialised yet.")
    return _sentiment_model


# ---------------------------------------------------------------------------
# Lifespan
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Async context manager that owns the full service lifecycle.

    Startup
    -------
    * Loads the HuggingFace model in a thread-pool executor so the event loop
      is not blocked during weight download / tokenizer initialisation.
    * Creates and starts the gRPC server.

    Shutdown
    --------
    * Sends a graceful stop signal to the gRPC server and awaits termination.
    """
    global _sentiment_model  # noqa: PLW0603

    settings = get_settings()

    logging.basicConfig(level=settings.log_level.upper())
    logger.info("Starting inference-service (HTTP :%d, gRPC :%d)", settings.http_port, settings.grpc_port)

    # ---------------------------------------------------------------- startup
    # 1. Load model in a thread so the event loop stays responsive.
    _sentiment_model = SentimentModel(model_name=settings.model_name)
    loop = asyncio.get_running_loop()
    await loop.run_in_executor(None, _sentiment_model.load)
    logger.info("SentimentModel ready.")

    # 2. Start gRPC server.
    from inference_service.grpc_server.server import create_grpc_server  # noqa: PLC0415

    grpc_server = await create_grpc_server(
        model=_sentiment_model,
        port=settings.grpc_port,
    )
    await grpc_server.start()
    logger.info("gRPC server started on port %d.", settings.grpc_port)

    # Hand control back to FastAPI — service is live.
    yield

    # --------------------------------------------------------------- shutdown
    logger.info("Shutting down gRPC server (grace=5s)…")
    await grpc_server.stop(grace=5)
    logger.info("gRPC server stopped.")


# ---------------------------------------------------------------------------
# Application factory
# ---------------------------------------------------------------------------


def create_app() -> FastAPI:
    """Construct and return the FastAPI application.

    Routers are registered here; middleware and exception handlers should also
    be added in this function to keep ``main.py`` as the single configuration
    point.
    """
    settings = get_settings()

    application = FastAPI(
        title="InsightPulse Inference Service",
        description=(
            "Real-time sentiment inference over financial event streams. "
            "Exposes both a REST API (this spec) and a gRPC endpoint on "
            f"port {settings.grpc_port}."
        ),
        version="0.1.0",
        lifespan=lifespan,
    )

    # Routers
    application.include_router(health.router)

    return application


# ---------------------------------------------------------------------------
# Module-level app instance (used by uvicorn)
# ---------------------------------------------------------------------------
app: FastAPI = create_app()
