/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.grpc;

import akka.javasdk.annotations.GrpcEndpoint;
import akkajavasdk.protocol.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@GrpcEndpoint
public class TestGrpcServiceImpl implements TestGrpcService {
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> simple(TestGrpcServiceOuterClass.In in) {
    return CompletableFuture.completedFuture(
        TestGrpcServiceOuterClass.Out.newBuilder().setData(in.getData()).build()
    );
  }
}
