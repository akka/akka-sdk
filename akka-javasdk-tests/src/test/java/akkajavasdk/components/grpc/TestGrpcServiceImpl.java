/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.grpc;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import akka.javasdk.grpc.GrpcRequestContext;
import akkajavasdk.protocol.*;
import io.grpc.Status;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET), denyCode = 5)
@GrpcEndpoint
public class TestGrpcServiceImpl implements TestGrpcService {

  private final GrpcClientProvider grpcClientProvider;
  private final GrpcRequestContext requestContext;

  public TestGrpcServiceImpl(GrpcClientProvider grpcClientProvider, GrpcRequestContext requestContext) {
    this.grpcClientProvider = grpcClientProvider;
    this.requestContext = requestContext;
  }

  @Override
  public TestGrpcServiceOuterClass.Out simple(TestGrpcServiceOuterClass.In in) {
    return TestGrpcServiceOuterClass.Out.newBuilder()
        .setData(in.getData())
        .setWasOnVirtualThread(Thread.currentThread().isVirtual())
        .build();
  }

  @Override
  public TestGrpcServiceOuterClass.Out readMetadata(TestGrpcServiceOuterClass.In in) {
    return TestGrpcServiceOuterClass.Out.newBuilder().setData(
            requestContext.metadata().getText(in.getData()).orElse("")).build();
  }


  @Override
  public TestGrpcServiceOuterClass.Out delegateToAkkaService(TestGrpcServiceOuterClass.In in) {
    // alias for external defined in application.conf - but note that it is only allowed for dev/test
    var grpcServiceClient = grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "other-service");
    return grpcServiceClient.simple(in);
  }

  @Override
  public TestGrpcServiceOuterClass.Out delegateToExternal(TestGrpcServiceOuterClass.In in) {
    // alias for external defined in application.conf
    var grpcServiceClient = grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "some.example.com");
    return grpcServiceClient.simple(in);
  }

  @Override
  public TestGrpcServiceOuterClass.Out customStatus(TestGrpcServiceOuterClass.In in) {
    if (in.getData().equals("error")) {
      throw Status.INVALID_ARGUMENT.augmentDescription("Invalid data").asRuntimeException();
    } else if (in.getData().equals("illegal")) {
      throw new IllegalArgumentException("Invalid data");
    } else if (in.getData().equals("error-dev-details")) {
      throw new RuntimeException("All the details in dev mode");
    }

    return simple(in);
  }

  @Override
  public TestGrpcServiceOuterClass.Out aclPublic(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL), denyCode = 14)
  @Override
  public TestGrpcServiceOuterClass.Out aclOverrideDenyCode(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(
      allow = @Acl.Matcher(service = "other-service"),
      deny = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @Override
  public TestGrpcServiceOuterClass.Out aclService(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Override
  public TestGrpcServiceOuterClass.Out aclInheritedDenyCode(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL))
  @Override
  public TestGrpcServiceOuterClass.Out aclDefaultDenyCode(TestGrpcServiceOuterClass.In in) {
    return simple(in);
  }

}
