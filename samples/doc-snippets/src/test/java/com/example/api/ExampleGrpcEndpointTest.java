package com.example.api;

import akka.NotUsed;
import akka.javasdk.testkit.TestKitSupport;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.example.proto.ExampleGrpcEndpointClient;
import com.example.proto.HelloReply;
import com.example.proto.HelloRequest;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

public class ExampleGrpcEndpointTest extends TestKitSupport {

  public void testSayHello() {
    var client = getGrpcEndpointClient(ExampleGrpcEndpointClient.class);

    var response = await(client.sayHello().invoke(HelloRequest.newBuilder()
        .setName("World")
        .build()));

    assertThat(response.getMessage()).isEqualTo("Hello World");
  }

  public void test() {
    var client = getGrpcEndpointClient(ExampleGrpcEndpointClient.class);

    Source<HelloReply, NotUsed> responseStream =
        client.itKeepsReplying(HelloRequest.newBuilder().setName("World").build());

    CompletionStage<List<HelloReply>> futureListOfFive =
        responseStream.take(5).runWith(Sink.seq(), testKit.getMaterializer());

    List<HelloReply> listOfFive = await(futureListOfFive);

    assertThat(listOfFive.size()).isEqualTo(5);
    listOfFive.stream().forEach((HelloReply helloReply) -> {
      assertThat(helloReply.getMessage()).isEqualTo("Hello World");
    });

  }

}
