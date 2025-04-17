package com.example.api;

import akka.NotUsed;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.example.proto.ExampleGrpcEndpoint;
import com.example.proto.HelloReply;
import com.example.proto.HelloRequest;

import java.util.concurrent.TimeUnit;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@GrpcEndpoint
public class ExampleGrpcEndpointImpl implements ExampleGrpcEndpoint {

  private final Materializer materializer;

  public ExampleGrpcEndpointImpl(Materializer materializer) {
    this.materializer = materializer;
  }

  @Override
  public HelloReply sayHello(HelloRequest in) {
    return HelloReply.newBuilder().setMessage("Hello " + in.getName()).build();
  }

  @Override
  public HelloReply itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    try {
      return in.runWith(Sink.head(), materializer).thenApply(firstStreamedHello ->
          HelloReply.newBuilder().setMessage("Hello " + firstStreamedHello.getName()).build()
      ).toCompletableFuture().get(3, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
    return Source.repeat(HelloReply.newBuilder().setMessage("Hello " + in.getName()).build());
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    return in.map(streamedHello -> HelloReply.newBuilder().setMessage("Hello " + streamedHello.getName()).build());
  }
}
