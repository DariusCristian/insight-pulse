#!/usr/bin/env python3
"""
Quick verification script to check if all components are properly structured.
This doesn't require running the full server with dependencies.
"""

import sys
import ast
from pathlib import Path

def verify_file_structure():
    """Verify that all required files exist and have basic structure."""
    base_path = Path(__file__).parent
    
    checks = {
        "Requirements file": base_path / "requirements.txt",
        "Proto stub - pb2": base_path / "inference_service/proto_generated/sentiment_pb2.py",
        "Proto stub - grpc": base_path / "inference_service/proto_generated/sentiment_pb2_grpc.py",
        "Proto __init__": base_path / "inference_service/proto_generated/__init__.py",
        "Servicer": base_path / "inference_service/grpc_server/servicer.py",
        "Server": base_path / "inference_service/grpc_server/server.py",
        "Model": base_path / "inference_service/model/sentiment_model.py",
        "Main app": base_path / "inference_service/main.py",
    }
    
    all_good = True
    for name, path in checks.items():
        if path.exists():
            print(f"✓ {name}: {path.name}")
        else:
            print(f"✗ {name}: NOT FOUND - {path}")
            all_good = False
    
    return all_good

def verify_servicer_async():
    """Check if the Analyze method in servicer.py is async."""
    servicer_path = Path(__file__).parent / "inference_service/grpc_server/servicer.py"
    
    with open(servicer_path) as f:
        tree = ast.parse(f.read())
    
    for node in ast.walk(tree):
        if isinstance(node, ast.AsyncFunctionDef) and node.name == "Analyze":
            print("✓ Analyze method is async")
            # Check for required parameters in the function body
            source = ast.get_source_segment(open(servicer_path).read(), node)
            if "event_id" in source and "processed_at_ms" in source:
                print("✓ Response includes event_id and processed_at_ms")
            return True
    
    print("✗ Analyze method is not async or not found")
    return False

def verify_requirements():
    """Check if requirements.txt has the specified versions."""
    req_path = Path(__file__).parent / "requirements.txt"
    
    with open(req_path) as f:
        content = f.read()
    
    checks = [
        ("grpcio==1.60.0", "grpcio version 1.60.0"),
        ("grpcio-tools==1.60.0", "grpcio-tools version 1.60.0"),
        ("transformers", "transformers package"),
        ("torch", "torch package"),
    ]
    
    all_good = True
    for requirement, description in checks:
        if requirement in content:
            print(f"✓ {description}")
        else:
            print(f"✗ {description} not found")
            all_good = False
    
    return all_good

def main():
    print("=" * 60)
    print("InsightPulse Inference Service - Structure Verification")
    print("=" * 60)
    print()
    
    print("1. File Structure Check:")
    print("-" * 40)
    structure_ok = verify_file_structure()
    print()
    
    print("2. Requirements Check:")
    print("-" * 40)
    requirements_ok = verify_requirements()
    print()
    
    print("3. Servicer Implementation Check:")
    print("-" * 40)
    servicer_ok = verify_servicer_async()
    print()
    
    print("=" * 60)
    if structure_ok and requirements_ok and servicer_ok:
        print("✓ All checks passed! Structure is correct.")
        print()
        print("To run the server (after installing dependencies):")
        print("  uvicorn inference_service.main:app --port 8000")
        print()
        print("The gRPC server will start on port 50051 automatically.")
        return 0
    else:
        print("✗ Some checks failed. Please review the errors above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
