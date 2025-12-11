/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.grpc.GrpcClientProvider;
import akkajavasdk.components.keyvalueentities.user.ProdCounterEntity;
import akkajavasdk.protocol.TestGrpcServiceClient;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@akka.javasdk.annotations.Setup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Bootstrap implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);
  private final TestGrpcServiceClient eagerlyCreatedClient;

  public Bootstrap(GrpcClientProvider clientProvider) {
    // indirect test coverage of lazy resolution of other grpc services in dev mode
    // (no concrete test case, but would fail all tests if it did not work)
    eagerlyCreatedClient =
        clientProvider.grpcClientFor(TestGrpcServiceClient.class, "some-other-service");
  }

  @Override
  public void onStartup() {
    logger.info("Starting Application");
  }

  @Override
  public Set<Class<?>> disabledComponents() {
    return Set.of(ProdCounterEntity.class);
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {

      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == TestGrpcServiceClient.class) {
          // not normal usage, here for test coverage of lazily resolving grpc clients
          return (T) eagerlyCreatedClient;
        } else {
          throw new IllegalArgumentException("Unknown dependency type " + clazz.getName());
        }
      }
    };
  }
}
