/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testkit;

import akka.actor.ExtendedActorSystem;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.http.HttpClient;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.impl.WebClientProviderImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * This class provided the necessary infrastructure to run Kalix integration test for projects built
 * with the Java SDK. Users should let their test classes extends this class.
 *
 * <p>This class wires-up a local Kalix application using the user's defined Kalix components.
 *
 * <p>Users can interact with their components public endpoints using the {@link
 * org.springframework.web.reactive.function.client.WebClient} that is available through {{{webClient}}} or
 * internally through the {{componentClient}}.
 *
 * <p>On test teardown, the Kalix application and the Kalix Runtime will be stopped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class KalixIntegrationTestKitSupport extends AsyncCallsSupport {

  private Logger logger = LoggerFactory.getLogger(getClass());

  protected KalixTestKit kalixTestKit;

  protected ComponentClient componentClient;

  protected WebClient webClient;

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
      webClient = new WebClientProviderImpl((ExtendedActorSystem)kalixTestKit.getActorSystem())
              .localWebClient();

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



}
