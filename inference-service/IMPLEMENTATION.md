# gRPC Sentiment Service Implementation Summary

## Overview
This document summarizes the async gRPC server implementation for sentiment analysis in the InsightPulse inference-service.

## Components Implemented

### 1. Dependencies (requirements.txt)
- **grpcio==1.60.0** - Core gRPC library for Python
- **grpcio-tools==1.60.0** - Tools for generating protobuf stubs
- **transformers** - HuggingFace library for sentiment model
- **torch** - PyTorch for model inference

### 2. Protocol Buffer Stubs
Generated Python stubs from `proto/sentiment.proto`:

**inference_service/proto_generated/sentiment_pb2.py**
- Protobuf message definitions for `SentimentRequest` and `SentimentResponse`
- Fields:
  - Request: `text`, `event_id`, `asset_symbol`
  - Response: `event_id`, `score`, `label`, `processed_at_ms`

**inference_service/proto_generated/sentiment_pb2_grpc.py**
- gRPC service stub for `SentimentService`
- Unary RPC method: `Analyze(SentimentRequest) -> SentimentResponse`

### 3. Service Implementation

**inference_service/grpc_server/servicer.py**
Implements the `SentimentService.Analyze` RPC method with:

#### Key Features:
1. **Async Method Signature**
   - Uses `async def Analyze()` for non-blocking execution
   - Compatible with `grpc.aio` async server

2. **Request Processing**
   - Accepts text input from `SentimentRequest`
   - Passes to HuggingFace sentiment model

3. **Response Mapping**
   - **event_id**: Echoed from request for correlation
   - **score**: Normalized sentiment score in range [-1.0, 1.0]
     - Clamped using `max(-1.0, min(1.0, raw_score))`
   - **label**: Mapped to human-readable strings
     - `"positive"` when score > 0.05
     - `"negative"` when score < -0.05
     - `"neutral"` when -0.05 ≤ score ≤ 0.05
   - **processed_at_ms**: Current Unix epoch in milliseconds
     - Generated using `int(time.time() * 1000)`

4. **Error Handling**
   - `ValueError`: Invalid input → `INVALID_ARGUMENT` status
   - Generic exceptions → `INTERNAL` status with logging
   - Always returns response with event_id for correlation

### 4. Server Configuration

**inference_service/grpc_server/server.py**
- Creates async gRPC server on port 50051 (configurable via `GRPC_PORT` env var)
- Registers `SentimentServicer` with the server
- Configures message size limits (4 MB)

**inference_service/main.py**
- FastAPI application with async lifespan management
- Startup sequence:
  1. Loads `distilbert-base-uncased-finetuned-sst-2-english` model
  2. Starts gRPC server on port 50051
  3. Starts HTTP server on port 8000
- Shutdown: Graceful gRPC stop with 5-second grace period

### 5. Model Integration

**inference_service/model/sentiment_model.py**
- Wraps HuggingFace sentiment analysis pipeline
- Model: `distilbert-base-uncased-finetuned-sst-2-english`
- Returns scores in [-1.0, 1.0] range:
  - Positive values = positive sentiment
  - Negative values = negative sentiment
  - Values near 0 = neutral/low confidence

## Running the Service

### Prerequisites
```bash
cd inference-service
pip install -r requirements.txt
```

### Start the Service
```bash
uvicorn inference_service.main:app --port 8000
```

This starts:
- HTTP API on port 8000
- gRPC server on port 50051

### Environment Variables
Configure in `.env` file:
- `HTTP_PORT`: FastAPI server port (default: 8000)
- `GRPC_PORT`: gRPC server port (default: 50051)
- `MODEL_NAME`: HuggingFace model identifier
- `LOG_LEVEL`: Logging verbosity (default: info)

## Testing the gRPC Service

### Using grpcurl (if available)
```bash
grpcurl -plaintext -d '{
  "text": "Apple stock soars to new highs",
  "event_id": "test-123",
  "asset_symbol": "AAPL"
}' localhost:50051 sentiment.SentimentService/Analyze
```

Expected response:
```json
{
  "event_id": "test-123",
  "score": 0.95,
  "label": "positive",
  "processed_at_ms": 1729095221029
}
```

## Architecture Notes

1. **Async Pattern**: The service uses `grpc.aio` for async gRPC support, allowing non-blocking concurrent request handling.

2. **Model Loading**: The HuggingFace model is loaded synchronously during startup in a thread executor to avoid blocking the async event loop.

3. **Thread Safety**: The model is loaded once at startup and shared across all requests. The HuggingFace pipeline handles concurrent inference safely.

4. **Neutral Threshold**: A small threshold (±0.05) around zero is used to classify neutral sentiment, providing more nuanced results than a strict positive/negative binary.

5. **Score Clamping**: Raw model scores are strictly clamped to [-1.0, 1.0] to ensure contract compliance.

## Integration with Ingestion Service

The Java ingestion-service connects to this gRPC endpoint to:
1. Send market event text for sentiment analysis
2. Receive scored sentiment with millisecond-precision timestamps
3. Use event_id for correlation between request and response
4. Handle timeouts (5-second deadline) and failures gracefully

## Files Modified

1. `/inference-service/requirements.txt` - Updated gRPC versions
2. `/inference-service/inference_service/proto_generated/sentiment_pb2.py` - Generated protobuf messages
3. `/inference-service/inference_service/proto_generated/sentiment_pb2_grpc.py` - Generated gRPC service
4. `/inference-service/inference_service/proto_generated/__init__.py` - Package exports
5. `/inference-service/inference_service/grpc_server/servicer.py` - Async Analyze implementation

## Verification

Run the verification script to check the implementation:
```bash
python3 verify_structure.py
```

All components are properly structured and ready for deployment.
