/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.grpc;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import akka.javasdk.grpc.GrpcRequestContext;
import akkajavasdk.protocol.*;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET), denyCode = 5)
@GrpcEndpoint
public class TestGrpcServiceImpl implements TestGrpcService {

  private final Logger logger = LoggerFactory.getLogger(TestGrpcServiceImpl.class);

  private final GrpcClientProvider grpcClientProvider;
  private final GrpcRequestContext requestContext;
  private boolean constructedOnVt = Thread.currentThread().isVirtual();

  public TestGrpcServiceImpl(
      GrpcClientProvider grpcClientProvider, GrpcRequestContext requestContext) {
    this.grpcClientProvider = grpcClientProvider;
    this.requestContext = requestContext;
  }

  private void logDetailsIfNotVt() {
    if (!Thread.currentThread().isVirtual()) {
      // try to gather more info
      logger.error(
          "Not on virtual thread, thread name ["
              + Thread.currentThread().getName()
              + "], thread state "
              + Thread.currentThread().getState()
              + " thread group ["
              + Thread.currentThread().getThreadGroup().getName()
              + "]",
          new RuntimeException("Error to get stacktrace"));
    }
  }

  @Override
  public TestGrpcServiceOuterClass.Out simple(TestGrpcServiceOuterClass.In in) {
    logDetailsIfNotVt();
    return TestGrpcServiceOuterClass.Out.newBuilder()
        .setData(in.getData())
        .setWasOnVirtualThread(Thread.currentThread().isVirtual() && constructedOnVt)
        .build();
  }

  @Override
  public TestGrpcServiceOuterClass.Out readMetadata(TestGrpcServiceOuterClass.In in) {
    logDetailsIfNotVt();
    return TestGrpcServiceOuterClass.Out.newBuilder()
        .setData(requestContext.metadata().getText(in.getData()).orElse(""))
        .build();
  }

  @Override
  public TestGrpcServiceOuterClass.Out delegateToAkkaService(TestGrpcServiceOuterClass.In in) {
    logDetailsIfNotVt();
    // alias for external defined in application.conf - but note that it is only allowed for
    // dev/test
    var grpcServiceClient =
        grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "other-service");
    return grpcServiceClient.simple(in);
  }

  @Override
  public TestGrpcServiceOuterClass.Out delegateToExternal(TestGrpcServiceOuterClass.In in) {
    logDetailsIfNotVt();
    // alias for external defined in application.conf
    var grpcServiceClient =
        grpcClientProvider.grpcClientFor(TestGrpcServiceClient.class, "some.example.com");
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
