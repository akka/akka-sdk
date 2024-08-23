/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testkit;

import akka.platform.javasdk.DependencyProvider;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.http.HttpClient;
import akka.platform.javasdk.testkit.KalixTestKit;
import akka.platform.javasdk.timer.TimerScheduler;
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
public abstract class KalixIntegrationTestKitSupport extends AsyncCallsSupport {

  private Logger logger = LoggerFactory.getLogger(getClass());

  protected KalixTestKit kalixTestKit;

  protected ComponentClient componentClient;

  protected TimerScheduler timerScheduler;

  protected Optional<DependencyProvider> dependencyProvider;

  protected Duration timeout = Duration.of(10, SECONDS);

  protected HttpClient httpClient;


  /**
   * Override this to use custom settings for an integration test
   */
  protected KalixTestKit.Settings kalixTestKitSettings() {
    return KalixTestKit.Settings.DEFAULT;
  }

  @BeforeAll
  public void beforeAll() {
    try {
      kalixTestKit = (new KalixTestKit(kalixTestKitSettings())).start();
      componentClient = kalixTestKit.getComponentClient();
      timerScheduler = kalixTestKit.getTimerScheduler();
      dependencyProvider = kalixTestKit.getDependencyContext();
      var baseUrl = "http://localhost:" + kalixTestKit.getPort();
      httpClient = new HttpClient(kalixTestKit.getActorSystem(), baseUrl);
    } catch (Exception ex) {
      logger.error("Failed to startup Kalix service", ex);
      throw ex;
    }
  }

  @AfterAll
  public void afterAll() {
    if (kalixTestKit != null) {
      logger.info("Stopping Kalix TestKit...");
      kalixTestKit.stop();
    }
  }

  public <T> T getDependency(Class<T> clazz) {
    return dependencyProvider.map(provider -> provider.getDependency(clazz))
      .orElseThrow(() -> new IllegalStateException("DependencyProvider not available, or not yet initialized."));
  }


}
