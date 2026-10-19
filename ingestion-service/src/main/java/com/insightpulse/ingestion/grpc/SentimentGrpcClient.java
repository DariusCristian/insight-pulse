package com.insightpulse.ingestion.grpc;

import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.proto.SentimentRequest;
import com.insightpulse.proto.SentimentResponse;
import com.insightpulse.proto.SentimentServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SentimentGrpcClient {

    /** Per-call deadline mandated by sentiment.proto ("Clients SHOULD set a deadline of 5 000 ms"). */
    static final long DEADLINE_MS = 5_000;

    @GrpcClient("inference-service")
    private SentimentServiceGrpc.SentimentServiceBlockingStub blockingStub;

    /**
     * Sends a {@link MarketEvent} to the inference-service via gRPC and returns
     * a {@link SentimentAlert} constructed from the response.
     *
     * <p>A fresh {@value #DEADLINE_MS} ms deadline is applied to every call. gRPC deadlines
     * are absolute, so it must be set per call rather than once on the injected stub.
     * The blocking stub and its channel are thread-safe, so this method may be called
     * concurrently (e.g. from virtual threads).
     *
     * @param event the market event to analyse
     * @return the resulting sentiment alert (label normalised to upper case)
     * @throws StatusRuntimeException if the RPC fails, e.g. {@code DEADLINE_EXCEEDED} after 5 s
     *                                or {@code UNAVAILABLE} if the inference-service is down
     */
    public SentimentAlert analyzeSentiment(MarketEvent event) {
        SentimentRequest request = SentimentRequest.newBuilder()
                .setEventId(event.eventId())
                .setAssetSymbol(event.assetSymbol())
                .setText(event.rawText())
                .build();

        try {
            SentimentResponse response = blockingStub
                    .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                    .analyze(request);

            return new SentimentAlert(
                    response.getEventId(),
                    event.assetSymbol(),
                    // Float.toString avoids widening artefacts such as 0.800000011920929
                    Double.parseDouble(Float.toString(response.getScore())),
                    response.getLabel().toUpperCase(Locale.ROOT),
                    response.getProcessedAtMs());
        } catch (StatusRuntimeException e) {
            log.warn("Sentiment RPC failed for event {}: {}", event.eventId(), e.getStatus());
            throw e;
        }
    }
}
