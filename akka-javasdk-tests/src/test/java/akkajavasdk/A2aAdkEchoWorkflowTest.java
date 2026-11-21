/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.a2a.RemoteTaskFollowingWorkflow;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A workflow follows a task of the a2a-python echo server in {@code a2a-interop}, once by push
 * notifications and once by polling. It runs only when the two servers are started and their card
 * URLs are given:
 *
 * <pre>
 * .venv/Scripts/python adk_echo_server.py --port 9101 --push
 * .venv/Scripts/python adk_echo_server.py --port 9102
 * A2A_ADK_ECHO_PUSH_CARD=http://127.0.0.1:9101/.well-known/agent-card.json \
 * A2A_ADK_ECHO_POLL_CARD=http://127.0.0.1:9102/.well-known/agent-card.json \
 * sbt "akka-javasdk-tests/testOnly akkajavasdk.A2aAdkEchoWorkflowTest"
 * </pre>
 *
 * <p>The echo server pushes to the service under test, so the service runs on a fixed port that the
 * push public URL names. The push agent polls only once an hour, so the task completes in time only
 * by push.
 */
@EnabledIfEnvironmentVariable(named = "A2A_ADK_ECHO_PUSH_CARD", matches = ".+")
public class A2aAdkEchoWorkflowTest extends TestKitSupport {

  private static final int PORT = 19397;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
        """
        akka.javasdk.testkit.http-port = %d
        akka.javasdk.a2a.push-receiver.public-url = "http://127.0.0.1:%d"
        akka.javasdk.a2a.remote-agents.echo-push {
          card-url = "%s"
          poll-interval = 1h
        }
        akka.javasdk.a2a.remote-agents.echo-poll {
          card-url = "%s"
          poll-interval = 500ms
        }
        """
            .formatted(
                PORT,
                PORT,
                System.getenv("A2A_ADK_ECHO_PUSH_CARD"),
                System.getenv("A2A_ADK_ECHO_POLL_CARD")));
  }

  @Test
  public void shouldFollowTheEchoServerByPush() {
    var state = run("echo-push", "hello push");

    assertThat(state.outcome()).isEqualTo("completed");
    assertThat(state.updates())
        .contains("artifact slow echo: hello push")
        .endsWith("status TASK_STATE_COMPLETED");
  }

  @Test
  public void shouldFollowTheEchoServerByPolling() {
    var state = run("echo-poll", "hello poll");

    assertThat(state.outcome()).isEqualTo("completed");
    assertThat(state.updates())
        .contains("artifact slow echo: hello poll")
        .endsWith("status TASK_STATE_COMPLETED");
  }

  private RemoteTaskFollowingWorkflow.State run(String agent, String text) {
    var workflowId = UUID.randomUUID().toString();
    componentClient
        .forWorkflow(workflowId)
        .method(RemoteTaskFollowingWorkflow::start)
        .invoke(new RemoteTaskFollowingWorkflow.Start(agent, "slow", text, 60_000));
    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .until(() -> get(workflowId).outcome() != null);
    var state = get(workflowId);
    System.out.println("Workflow " + agent + ": " + state);
    return state;
  }

  private RemoteTaskFollowingWorkflow.State get(String workflowId) {
    return componentClient
        .forWorkflow(workflowId)
        .method(RemoteTaskFollowingWorkflow::get)
        .invoke();
  }
}
