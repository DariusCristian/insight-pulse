# ---------------------------------------------------------------------------
# inference-service Dockerfile
# ---------------------------------------------------------------------------
# Base: python:3.11-slim (Debian Bookworm)
# Build strategy: install dependencies first for layer-cache efficiency,
#                 then copy application source.
# ---------------------------------------------------------------------------

FROM python:3.11-slim AS base

# Prevent Python from writing .pyc files and buffer stdout/stderr.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# ---------------------------------------------------------------------------
# System dependencies (minimal set for torch CPU + grpcio compilation)
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Python dependencies
# ---------------------------------------------------------------------------
# Copy only the dependency manifests first so Docker can cache this layer
# independently of source changes.
COPY pyproject.toml requirements.txt ./

# Install CPU-only torch from the PyTorch index to avoid pulling the large
# CUDA variant; fall back to requirements.txt for everything else.
RUN pip install --index-url https://download.pytorch.org/whl/cpu torch==2.3.0 && \
    pip install -r requirements.txt

# ---------------------------------------------------------------------------
# Proto stubs (must be generated before copying source that imports them)
# ---------------------------------------------------------------------------
COPY proto/ ./proto/
COPY scripts/ ./scripts/

# Install grpcio-tools (already in requirements.txt) and generate stubs.
RUN bash scripts/generate_proto.sh

# ---------------------------------------------------------------------------
# Application source
# ---------------------------------------------------------------------------
COPY inference_service/ ./inference_service/

# ---------------------------------------------------------------------------
# Runtime configuration
# ---------------------------------------------------------------------------
# Expose HTTP and gRPC ports.
EXPOSE 8000 50051

# Default env (can be overridden at runtime or via docker-compose).
ENV HTTP_PORT=8000 \
    GRPC_PORT=50051 \
    MODEL_NAME=distilbert-base-uncased-finetuned-sst-2-english \
    LOG_LEVEL=info

# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
CMD ["uvicorn", "inference_service.main:app", \
     "--host", "0.0.0.0", \
     "--port", "8000", \
     "--log-level", "info"]
