package com.example;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;

// tag::config[]
public class ConfigIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      """
      akka.javasdk.agent.openai.api-key = n/a
      """
    );
  }
}
// end::config[]
