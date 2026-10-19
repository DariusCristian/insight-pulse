package com.insightpulse.ingestion.grpc;

import com.insightpulse.ingestion.model.MarketEvent;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.proto.SentimentRequest;
import com.insightpulse.proto.SentimentResponse;
import com.insightpulse.proto.SentimentServiceGrpc;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SentimentGrpcClientTest {

    private static final MarketEvent EVENT =
            new MarketEvent("evt-1", "AAPL", "NEWS", "Apple beats earnings", 190.5, 1_700_000_000_000L);

    private final AtomicReference<SentimentRequest> receivedRequest = new AtomicReference<>();
    private final AtomicReference<Deadline> receivedDeadline = new AtomicReference<>();
    private final AtomicReference<Status> failWith = new AtomicReference<>();

    private Server server;
    private ManagedChannel channel;
    private SentimentGrpcClient client;

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();

        SentimentServiceGrpc.SentimentServiceImplBase service = new SentimentServiceGrpc.SentimentServiceImplBase() {
            @Override
            public void analyze(SentimentRequest request, StreamObserver<SentimentResponse> observer) {
                receivedRequest.set(request);
                if (failWith.get() != null) {
                    observer.onError(failWith.get().asRuntimeException());
                    return;
                }
                observer.onNext(SentimentResponse.newBuilder()
                        .setEventId(request.getEventId())
                        .setScore(0.8f)
                        .setLabel("positive")
                        .setProcessedAtMs(1_700_000_000_123L)
                        .build());
                observer.onCompleted();
            }
        };

        ServerInterceptor deadlineCapture = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                receivedDeadline.set(Context.current().getDeadline());
                return next.startCall(call, headers);
            }
        };

        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(service, deadlineCapture))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        client = new SentimentGrpcClient();
        ReflectionTestUtils.setField(client, "blockingStub", SentimentServiceGrpc.newBlockingStub(channel));
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow();
        server.shutdownNow();
        channel.awaitTermination(1, TimeUnit.SECONDS);
        server.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void mapsEventToRequestAndResponseToAlert() {
        SentimentAlert alert = client.analyzeSentiment(EVENT);

        SentimentRequest sent = receivedRequest.get();
        assertThat(sent.getEventId()).isEqualTo("evt-1");
        assertThat(sent.getAssetSymbol()).isEqualTo("AAPL");
        assertThat(sent.getText()).isEqualTo("Apple beats earnings");

        assertThat(alert.eventId()).isEqualTo("evt-1");
        assertThat(alert.assetSymbol()).isEqualTo("AAPL");
        assertThat(alert.score()).isEqualTo(0.8); // exact: no float-widening artefacts
        assertThat(alert.label()).isEqualTo("POSITIVE");
        assertThat(alert.processedAtMs()).isEqualTo(1_700_000_000_123L);
    }

    @Test
    void appliesFreshFiveSecondDeadlinePerCall() throws Exception {
        client.analyzeSentiment(EVENT);
        long firstRemainingMs = receivedDeadline.get().timeRemaining(TimeUnit.MILLISECONDS);

        // The deadline is absolute; if it were set once on the stub, the second call would have less time.
        Thread.sleep(300);
        receivedDeadline.set(null);
        client.analyzeSentiment(EVENT);
        long secondRemainingMs = receivedDeadline.get().timeRemaining(TimeUnit.MILLISECONDS);

        assertThat(firstRemainingMs).isBetween(4_000L, SentimentGrpcClient.DEADLINE_MS);
        assertThat(secondRemainingMs).isBetween(4_000L, SentimentGrpcClient.DEADLINE_MS);
    }

    @Test
    void propagatesServerErrorStatus() {
        failWith.set(Status.INTERNAL.withDescription("Internal inference error."));

        assertThatThrownBy(() -> client.analyzeSentiment(EVENT))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL));
    }

    @Test
    void propagatesUnavailableWhenServerIsDown() throws Exception {
        server.shutdownNow();
        server.awaitTermination(1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> client.analyzeSentiment(EVENT))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }
}
