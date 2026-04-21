package com.example.callanotherservice;

import static org.assertj.core.api.Assertions.assertThat;

import akka.Done;
import akka.NotUsed;
import akka.javasdk.testkit.TestKitSupport;
import akka.stream.javadsl.Source;
import com.example.proto.CallExternalGrpcEndpointClient;
import com.example.proto.ExampleGrpcEndpointClient;
import com.example.proto.HelloReply;
import com.example.proto.HelloRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// tag::grpc-mock[]
public class CallExternalGrpcEndpointMockedTest extends TestKitSupport {

  @AfterEach
  public void resetMocks() {
    testKit.getMockedGrpcServices().reset();
  }

  @Test
  public void delegatesToMockedExternalGrpcService() {
    testKit
        .getMockedGrpcServices()
        .mockResponse( // <1>
            "hellogrpc.example.com",
            ExampleGrpcEndpointClient.class,
            new ExampleGrpcEndpointMock("Hello from the mock"));

    var client = getGrpcEndpointClient(CallExternalGrpcEndpointClient.class); // <2>
    var response =
        client.callExternalService(HelloRequest.newBuilder().setName("Alice").build());

    assertThat(response.getMessage()).isEqualTo("Hello from the mock");
  }

  /** Mock implementation of the generated Akka gRPC client interface. */ // <3>
  static final class ExampleGrpcEndpointMock extends ExampleGrpcEndpointClient {
    private final String reply;

    ExampleGrpcEndpointMock(String reply) {
      this.reply = reply;
    }

    @Override
    public HelloReply sayHello(HelloRequest in) {
      return HelloReply.newBuilder().setMessage(reply).build();
    }

    @Override
    public HelloReply itKeepsTalking(Source<HelloRequest, NotUsed> in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Done> close() {
      return CompletableFuture.completedFuture(Done.getInstance());
    }

    @Override
    public CompletionStage<Done> closed() {
      return new CompletableFuture<>();
    }
  }
}
// end::grpc-mock[]
