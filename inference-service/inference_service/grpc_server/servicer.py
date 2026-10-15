"""
inference_service/grpc_server/servicer.py
------------------------------------------
Concrete gRPC servicer that implements SentimentService defined in
proto/sentiment.proto.

The generated base class (sentiment_pb2_grpc.SentimentServiceServicer) is
imported at runtime so that this module can be imported before proto stubs
are generated (useful during testing with mocks).
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

import grpc

if TYPE_CHECKING:
    # Only imported for type-checking; not available until proto stubs are generated.
    from inference_service.proto_generated import sentiment_pb2
    from inference_service.proto_generated import sentiment_pb2_grpc

from inference_service.model.sentiment_model import SentimentModel

logger = logging.getLogger(__name__)


class SentimentServicer:
    """Implements the SentimentService.Analyze RPC.

    This class extends the protobuf-generated base servicer at instantiation
    time (not at class definition) so that the module is importable before
    proto stubs exist.

    Parameters
    ----------
    model:
        A fully loaded :class:`~inference_service.model.SentimentModel`
        instance.  The servicer assumes :meth:`SentimentModel.load` has
        already been called.
    """

    def __init__(self, model: SentimentModel) -> None:
        self._model = model

        # Dynamically inherit from the generated base class so this file
        # remains importable before `generate_proto.sh` has been run.
        try:
            from inference_service.proto_generated import (  # noqa: PLC0415
                sentiment_pb2_grpc as _grpc_module,
            )
            # Patch the MRO at runtime
            self.__class__ = type(
                "SentimentServicer",
                (_grpc_module.SentimentServiceServicer, SentimentServicer),
                dict(SentimentServicer.__dict__),
            )
        except ImportError:
            logger.warning(
                "Proto stubs not found. Run scripts/generate_proto.sh first. "
                "Servicer will not register correctly with a real gRPC server."
            )

    # ------------------------------------------------------------------
    # RPC handlers
    # ------------------------------------------------------------------

    def Analyze(
        self,
        request: "sentiment_pb2.SentimentRequest",
        context: grpc.ServicerContext,
    ) -> "sentiment_pb2.SentimentResponse":
        """Handle a unary SentimentRequest → SentimentResponse call.

        Parameters
        ----------
        request:
            Protobuf ``SentimentRequest`` containing the ``text`` field.
        context:
            gRPC servicer context — used to set status codes on error.

        Returns
        -------
        SentimentResponse
            Protobuf response with ``score`` (float32, [-1.0, 1.0]) and
            ``label`` (str, e.g. "POSITIVE" / "NEGATIVE").
        """
        from inference_service.proto_generated import sentiment_pb2 as _pb2  # noqa: PLC0415

        try:
            score: float = self._model.predict(request.text)
            label: str = "POSITIVE" if score >= 0.0 else "NEGATIVE"
            return _pb2.SentimentResponse(score=score, label=label)
        except ValueError as exc:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details(str(exc))
            return _pb2.SentimentResponse()
        except Exception as exc:  # noqa: BLE001
            logger.exception("Unexpected error during Analyze RPC: %s", exc)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details("Internal inference error.")
            return _pb2.SentimentResponse()
