#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# generate_proto.sh
#
# Generates Python gRPC stubs from proto/sentiment.proto into
# inference_service/proto_generated/.
#
# Usage:
#   cd inference-service
#   bash scripts/generate_proto.sh
#
# Prerequisites:
#   pip install grpcio-tools
# -----------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

PROTO_SRC="${PROJECT_ROOT}/proto"
OUT_DIR="${PROJECT_ROOT}/inference_service/proto_generated"

echo "==> Generating protobuf stubs"
echo "    Source : ${PROTO_SRC}"
echo "    Output : ${OUT_DIR}"

# Ensure the output directory exists
mkdir -p "${OUT_DIR}"

# Run grpc_tools.protoc
python -m grpc_tools.protoc \
  --proto_path="${PROTO_SRC}" \
  --python_out="${OUT_DIR}" \
  --grpc_python_out="${OUT_DIR}" \
  "${PROTO_SRC}/sentiment.proto"

# Fix relative imports produced by protoc so they work as a package
# (protoc generates `import sentiment_pb2` but we need `from . import sentiment_pb2`)
GRPC_FILE="${OUT_DIR}/sentiment_pb2_grpc.py"
if [[ -f "${GRPC_FILE}" ]]; then
  sed -i.bak 's/^import sentiment_pb2/from . import sentiment_pb2/' "${GRPC_FILE}"
  rm -f "${GRPC_FILE}.bak"
  echo "    Fixed  : relative import in sentiment_pb2_grpc.py"
fi

# Ensure the output directory is a Python package
touch "${OUT_DIR}/__init__.py"

echo "==> Done. Generated files:"
ls -1 "${OUT_DIR}"
