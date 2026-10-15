"""
inference_service/api/routes/health.py
----------------------------------------
Health-check endpoint.  Mounted at the application root under /health.
"""

from __future__ import annotations

from fastapi import APIRouter

from inference_service.api.schemas import HealthResponse

router = APIRouter(tags=["health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Service health check",
    description=(
        "Returns HTTP 200 with ``{\\\"status\\\": \\\"ok\\\"}`` when the service "
        "is running.  Can be extended to include readiness checks (model loaded, "
        "gRPC server up, etc.)."
    ),
)
async def health_check() -> HealthResponse:
    """Return a simple liveness response.

    Returns
    -------
    HealthResponse
        ``{"status": "ok"}`` — expand with richer checks as needed.
    """
    return HealthResponse(status="ok")
