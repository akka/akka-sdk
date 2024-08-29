/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.DependencyProvider;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClient;
import akka.javasdk.timer.TimerScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * This class provided the necessary infrastructure to run Kalix integration test for projects built
 * with the Java SDK. Users should let their test classes extends this class.
 *
 * <p>This class wires-up a local Kalix application using the user's defined Kalix components.
 *
 * <p>Users can interact with their components via their public endpoint via an HTTP client or
 * internally through the {{componentClient}}.
 *
 * <p>On test teardown, the Kalix application and the Kalix Runtime will be stopped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AkkaSdkTestKitSupport extends AsyncCallsSupport {

  private Logger logger = LoggerFactory.getLogger(getClass());

  protected AkkaSdkTestKit akkaSdkTestKit;

  protected ComponentClient componentClient;

  protected TimerScheduler timerScheduler;

  protected Optional<DependencyProvider> dependencyProvider;

  protected Duration timeout = Duration.of(10, SECONDS);

  protected HttpClient httpClient;


  /**
   * Override this to use custom settings for an integration test
   */
  protected AkkaSdkTestKit.Settings kalixTestKitSettings() {
    return AkkaSdkTestKit.Settings.DEFAULT;
  }

  @BeforeAll
  public void beforeAll() {
    try {
      akkaSdkTestKit = (new AkkaSdkTestKit(kalixTestKitSettings())).start();
      componentClient = akkaSdkTestKit.getComponentClient();
      timerScheduler = akkaSdkTestKit.getTimerScheduler();
      dependencyProvider = akkaSdkTestKit.getDependencyContext();
      var baseUrl = "http://localhost:" + akkaSdkTestKit.getPort();
      httpClient = new HttpClient(akkaSdkTestKit.getActorSystem(), baseUrl);
    } catch (Exception ex) {
      logger.error("Failed to startup Kalix service", ex);
      throw ex;
    }
  }

  @AfterAll
  public void afterAll() {
    if (akkaSdkTestKit != null) {
      logger.info("Stopping Kalix TestKit...");
      akkaSdkTestKit.stop();
    }
  }

  public <T> T getDependency(Class<T> clazz) {
    return dependencyProvider.map(provider -> provider.getDependency(clazz))
      .orElseThrow(() -> new IllegalStateException("DependencyProvider not available, or not yet initialized."));
  }


}
