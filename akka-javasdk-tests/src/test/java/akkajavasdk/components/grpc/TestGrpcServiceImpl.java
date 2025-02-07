/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.grpc;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.annotations.JWT;
import akka.javasdk.grpc.GrpcClientProvider;
import akkajavasdk.protocol.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET), denyCode = 5)
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

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> delegateToAkkaService(TestGrpcServiceOuterClass.In in) {
    // alias for external defined in application.conf - but note that it is only allowed for dev/test
    var grpcServiceClient = grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "other-service");
    return grpcServiceClient.simple(in);
  }

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> delegateToExternal(TestGrpcServiceOuterClass.In in) {
    // alias for external defined in application.conf
    var grpcServiceClient = grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "some.example.com");
    return grpcServiceClient.simple(in);
  }

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclPublic(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL), denyCode = 14)
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclOverrideDenyCode(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(
      allow = @Acl.Matcher(service = "other-service"),
      deny = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclService(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclInheritedDenyCode(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL))
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclDefaultDenyCode(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

}
