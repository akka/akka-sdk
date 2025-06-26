/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.grpc;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.annotations.JWT;
import akka.javasdk.grpc.GrpcClientProvider;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import akkajavasdk.protocol.TestJwtsGrpcService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET), denyCode = 5)
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "class-level-issuer")
@GrpcEndpoint
public class TestJwtsGrpcServiceImpl implements TestJwtsGrpcService {

  private final GrpcClientProvider grpcClientProvider;

  public TestJwtsGrpcServiceImpl(GrpcClientProvider grpcClientProvider) {
    this.grpcClientProvider = grpcClientProvider;
  }

  private TestGrpcServiceOuterClass.Out simple(TestGrpcServiceOuterClass.In in) {
    return TestGrpcServiceOuterClass.Out.newBuilder().setData(in.getData()).build();
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer-123")
  @Override
  public TestGrpcServiceOuterClass.Out jwtIssuer(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, staticClaims = { @JWT.StaticClaim(claim = "sub", values = "my-subject-123")})
  @Override
  public TestGrpcServiceOuterClass.Out jwtStaticClaimValue(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, staticClaims = { @JWT.StaticClaim(claim = "sub", pattern = "my-subject-\\d+")})
  public TestGrpcServiceOuterClass.Out jwtStaticClaimPattern(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Override
  public TestGrpcServiceOuterClass.Out jwtInherited(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

}
