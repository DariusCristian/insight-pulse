package com.insightpulse.ingestion.grpc;

import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.proto.SentimentServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SentimentGrpcClient {

    @GrpcClient("inference-service")
    private SentimentServiceGrpc.SentimentServiceBlockingStub blockingStub;

    /**
     * Sends a {@link MarketEvent} to the inference-service via gRPC and returns
     * a {@link SentimentAlert} constructed from the response.
     *
     * @param event the market event to analyse
     * @return the resulting sentiment alert
     */
    public SentimentAlert analyzeSentiment(MarketEvent event) {
        // TODO: build SentimentRequest from event, call blockingStub.analyze(),
        //       map SentimentResponse fields to SentimentAlert and return.
        throw new UnsupportedOperationException("analyzeSentiment not yet implemented");
    }
}
