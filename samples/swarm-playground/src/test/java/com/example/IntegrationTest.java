package com.example;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

/**
 * This is a skeleton for implementing integration tests for an Akka application built with the Akka
 * SDK.
 *
 * <p>It interacts with the components of the application using a componentClient or httpClient
 * (already configured and provided automatically through injection).
 */
public class IntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    // Bootstrap will check if key exists when running integation tests.
    // We don't need a real one though.
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      ConfigFactory.parseString("akka.javasdk.agent.openai.api-key=fake-key")
    );
  }

  @Test
  public void test() throws Exception {
    // implement your integration tests here by calling your
    // components by using the `componentClient` or endpoints using `httpClient`
  }
}
