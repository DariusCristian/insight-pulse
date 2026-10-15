"""
inference_service/grpc_server/server.py
----------------------------------------
Factory and lifecycle helpers for the async gRPC server.

Usage (inside an async context, e.g. FastAPI lifespan):

    from inference_service.grpc_server.server import create_grpc_server
    from inference_service.model.sentiment_model import SentimentModel

    model = SentimentModel(settings.model_name)
    model.load()

    server = await create_grpc_server(model, port=settings.grpc_port)
    await server.start()
    ...
    await server.stop(grace=5)
"""

from __future__ import annotations

import logging

import grpc
import grpc.aio

from inference_service.model.sentiment_model import SentimentModel

logger = logging.getLogger(__name__)

# Number of threads in the gRPC thread-pool for unary blocking calls.
_GRPC_WORKERS = 4


async def create_grpc_server(
    model: SentimentModel,
    port: int = 50051,
) -> grpc.aio.Server:
    """Construct and bind an async gRPC server exposing SentimentService.

    Parameters
    ----------
    model:
        A fully loaded :class:`~inference_service.model.SentimentModel`.
        :meth:`SentimentModel.load` must have been called before passing the
        instance here.
    port:
        TCP port the gRPC server will listen on.  Defaults to ``50051``.

    Returns
    -------
    grpc.aio.Server
        A configured (but not yet started) async gRPC server.
        Call ``await server.start()`` to begin accepting connections.

    Raises
    ------
    ImportError
        If proto stubs have not been generated yet (run
        ``scripts/generate_proto.sh``).
    """
    from inference_service.proto_generated import (  # noqa: PLC0415
        sentiment_pb2_grpc,
    )
    from inference_service.grpc_server.servicer import SentimentServicer  # noqa: PLC0415

    server = grpc.aio.server(
        options=[
            ("grpc.max_send_message_length", 4 * 1024 * 1024),    # 4 MB
            ("grpc.max_receive_message_length", 4 * 1024 * 1024),  # 4 MB
        ]
    )

    servicer = SentimentServicer(model=model)
    sentiment_pb2_grpc.add_SentimentServiceServicer_to_server(servicer, server)

    listen_addr = f"[::]:{port}"
    server.add_insecure_port(listen_addr)
    logger.info("gRPC server bound to %s", listen_addr)

    return server
