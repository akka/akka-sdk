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
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "class-level-issuer")
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
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclPublicMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL), denyCode = 14)
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclOverrideDenyCodeMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(
      allow = @Acl.Matcher(service = "other-service"),
      deny = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclServiceMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclInheritedDenyCodeMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL))
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> aclDefaultDenyCodeMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer-123")
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> jwtIssuerMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, staticClaims = { @JWT.StaticClaim(claim = "sub", values = "my-subject-123")})
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> jwtStaticClaimValueMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, staticClaims = { @JWT.StaticClaim(claim = "sub", pattern = "my-subject-\\d+")})
  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> jwtStaticClaimPatternMethod(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Override
  public CompletionStage<TestGrpcServiceOuterClass.Out> jwtInherited(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

}
