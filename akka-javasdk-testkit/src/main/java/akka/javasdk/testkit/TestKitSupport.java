/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.grpc.javadsl.AkkaGrpcClient;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClient;
import akka.javasdk.timer.TimerScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provided the necessary infrastructure to run integration test for projects built
 * with the Java SDK. Users should let their test classes extends this class.
 *
 * <p>This class wires-up a local service using the user's defined components.
 *
 * <p>Users can interact with their components via their public endpoint via an HTTP client or
 * internally through the {{componentClient}}.
 *
 * <p>On test teardown, the service and the runtime will be stopped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestKitSupport extends AsyncCallsSupport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  protected TestKit testKit;

  protected ComponentClient componentClient;

  protected TimerScheduler timerScheduler;

  /**
   * A http client for interacting with the service under test, the client will not be authenticated
   * and will appear to the service as a request with the internet principal.
   */
  protected HttpClient httpClient;

  /**
   * Override this to use custom settings for an integration test
   */
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT;
  }

  @BeforeAll
  public void beforeAll() {
    try {
      testKit = (new TestKit(testKitSettings())).start();
      componentClient = testKit.getComponentClient();
      timerScheduler = testKit.getTimerScheduler();
      httpClient = testKit.getSelfHttpClient();
    } catch (Exception ex) {
      logger.error("Failed to startup service", ex);
      throw ex;
    }
  }

  @AfterAll
  public void afterAll() {
    if (testKit != null) {
      logger.info("Stopping TestKit...");
      testKit.stop();
    }
  }


  /**
   * Lookup a specific object as provided by the service dependency provider
   */
  public <T> T getDependency(Class<T> clazz) {
    return testKit.getDependencyProvider().map(provider -> provider.getDependency(clazz))
      .orElseThrow(() -> new IllegalStateException("DependencyProvider not available, or not yet initialized."));
  }

  public <T extends AkkaGrpcClient> T getGrpcEndpointClient(Class<T> grpcClientClass) {
    return testKit.getGrpcEndpointClient(grpcClientClass);
  }


}
