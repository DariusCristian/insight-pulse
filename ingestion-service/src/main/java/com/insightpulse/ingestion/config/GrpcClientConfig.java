package com.insightpulse.ingestion.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    // gRPC client channel is configured via application.yml under grpc.client.inference-service.
    // The net.devh grpc-spring-boot-starter auto-wires @GrpcClient-annotated stubs.
    // Add custom interceptors or channel credentials here as needed.
}
