/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akkajavasdk.components.a2a.A2aEchoAgent;
import akkajavasdk.components.a2a.TestA2aEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Keeps the test service running for the A2A interoperability checks in {@code a2a-interop}. Runs
 * only when {@code A2A_INTEROP_SECONDS} is set, and serves for that many seconds on the testkit
 * port. The first skill is the kit task workflow, because the compatibility kit selects no skill
 * and picks behaviours by the prefix of the message id.
 */
@EnabledIfEnvironmentVariable(named = "A2A_INTEROP_SECONDS", matches = "[0-9]+")
public class A2aInteropServiceTest extends TestKitSupport {

  static {
    TestA2aEndpoint.interop = true;
  }

  private final TestModelProvider echoModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    echoModel.fixedResponse(
        input -> {
          var text = input instanceof TestModelProvider.UserMessage user ? user.text() : "";
          return new AiResponse(text.isBlank() ? "Hello from the echo agent" : "echo: " + text);
        });
    return TestKit.Settings.DEFAULT.withModelProvider(A2aEchoAgent.class, echoModel);
  }

  @Test
  public void serveForInteropChecks() throws InterruptedException {
    Thread.sleep(Long.parseLong(System.getenv("A2A_INTEROP_SECONDS")) * 1000);
  }
}
