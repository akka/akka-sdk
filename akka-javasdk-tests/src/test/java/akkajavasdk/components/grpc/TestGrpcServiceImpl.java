/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.grpc;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import akkajavasdk.protocol.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@GrpcEndpoint
public class TestGrpcServiceImpl implements TestGrpcService {

  private final GrpcClientProvider grpcClientProvider;

  public TestGrpcServiceImpl(GrpcClientProvider grpcClientProvider) {
    this.grpcClientProvider = grpcClientProvider;
  }

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> simple(TestGrpcServiceOuterClass.In in) {
    return CompletableFuture.completedFuture(
        TestGrpcServiceOuterClass.Out.newBuilder().setData(in.getData()).build()
    );
  }

  // FIXME what calls to other Akka services

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> selfDelegate(TestGrpcServiceOuterClass.In in) {
    // alias for external defined in application.conf
    var grpcServiceClient = grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "self.example.com");
    return grpcServiceClient.simple(in);
  }
}
