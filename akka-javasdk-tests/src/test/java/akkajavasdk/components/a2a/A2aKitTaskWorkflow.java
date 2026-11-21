/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.Done;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aTaskContext;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.workflow.Workflow;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A task workflow with the behaviours that the A2A compatibility kit selects by the prefix of the
 * message id (see {@code a2a-interop/adk_echo_server.py}), and an {@code ask} skill that asks for a
 * name and greets it. Without a known prefix, a message is echoed as an artifact. The text {@code
 * wait} keeps the task working until it is canceled.
 */
@Component(id = "a2a-kit-task-workflow", name = "Kit tasks", description = "Long tasks for tests")
@A2aSkill(id = "kit-tasks", name = "Kit tasks", description = "Behaviours of the A2A kit")
@A2aSkill(
    id = "ask",
    name = "Ask",
    description = "Asks for a name, then greets it",
    tags = {"input-required"})
public class A2aKitTaskWorkflow extends Workflow<A2aKitTaskWorkflow.State> {

  /**
   * @param messageId the id of the first message, which selects the behaviour
   * @param skillId the skill the first message selected, or null
   * @param text the text of the latest message
   */
  public record State(String messageId, String skillId, String text) {}

  private final A2aTaskContext task;

  public A2aKitTaskWorkflow(A2aTaskContext task) {
    this.task = task;
  }

  public Effect<Done> onMessage(A2aMessage message) {
    var text = text(message);
    if (currentState() == null) {
      var skillId =
          message.metadata() != null && message.metadata().get("skillId") instanceof String s
              ? s
              : null;
      return effects()
          .updateState(new State(message.messageId(), skillId, text))
          .transitionTo(A2aKitTaskWorkflow::start)
          .thenReply(Done.getInstance());
    }
    var state = currentState();
    return effects()
        .updateState(new State(state.messageId(), state.skillId(), text))
        .transitionTo(A2aKitTaskWorkflow::answer)
        .thenReply(Done.getInstance());
  }

  /** The timeout handler of a pause that ends the task. */
  public Effect<Done> pauseEnded() {
    return effects().transitionTo(A2aKitTaskWorkflow::finish).thenReply(Done.getInstance());
  }

  private StepEffect start() {
    var id = currentState().messageId();
    var text = currentState().text();
    if ("ask".equals(currentState().skillId())) {
      task.inputRequired("What is your name?");
      return stepEffects().thenPause();
    }
    if (id.startsWith("tck-stream-artifact-chunked")) {
      task.working("Working");
      task.artifact(artifact("chunked", A2aPart.text("chunk-1 "), A2aPart.text("chunk-2")));
      return complete();
    } else if (id.startsWith("test-resubscribe-message-id")) {
      task.working("Working");
      return stepEffects()
          .thenPause(
              pauseSetting(Duration.ofSeconds(4)).timeoutHandler(A2aKitTaskWorkflow::pauseEnded));
    } else if (id.startsWith("tck-stream-artifact-text")
        || id.startsWith("tck-stream-ordering-001")
        || id.startsWith("tck-stream-001")
        || id.startsWith("tck-stream-003")) {
      task.working("Working");
      task.artifact(artifact("text", A2aPart.text("Streamed text content")));
      return complete();
    } else if (id.startsWith("tck-stream-artifact-file")) {
      task.working("Working");
      task.artifact(fileArtifact());
      return complete();
    } else if (id.startsWith("tck-artifact-file-url")) {
      task.artifact(
          artifact(
              "file-url",
              A2aPart.url("https://example.com/output.txt", "text/plain", "output.txt")));
      return complete();
    } else if (id.startsWith("tck-artifact-file")) {
      task.artifact(fileArtifact());
      return complete();
    } else if (id.startsWith("tck-message-response")) {
      task.respondWithMessage(task.agentMessage("Direct message response"));
      return stepEffects().thenEnd();
    } else if (id.startsWith("tck-input-required")) {
      task.inputRequired("Please provide more input");
      return stepEffects().thenPause();
    } else if (id.startsWith("tck-complete-task")) {
      task.completed("Hello from TCK");
      return stepEffects().thenEnd();
    } else if (id.startsWith("tck-artifact-text")) {
      task.artifact(artifact("text", A2aPart.text("Generated text content")));
      return complete();
    } else if (id.startsWith("tck-artifact-data")) {
      task.artifact(artifact("data", A2aPart.data(Map.of("key", "value", "count", 42))));
      return complete();
    } else if (id.startsWith("tck-reject-task")) {
      task.failed("Rejected");
      return stepEffects().thenEnd();
    } else if (id.startsWith("tck-stream-002")) {
      return complete();
    } else if (text.equals("wait")) {
      task.working("Waiting until canceled");
      return stepEffects().thenPause();
    }
    task.artifact(artifact("echo", A2aPart.text("echo: " + text)));
    return complete();
  }

  // A later message of the task: the answer to the question of an input-required task
  private StepEffect answer() {
    task.completed("Hello, " + currentState().text());
    return stepEffects().thenEnd();
  }

  private StepEffect finish() {
    return complete();
  }

  private StepEffect complete() {
    task.completed(task.agentMessage("Done"));
    return stepEffects().thenEnd();
  }

  private static A2aArtifact fileArtifact() {
    return artifact(
        "file", A2aPart.raw("tck".getBytes(StandardCharsets.UTF_8), "text/plain", "output.txt"));
  }

  private static A2aArtifact artifact(String name, A2aPart... parts) {
    return new A2aArtifact(
        UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString(),
        name,
        null,
        List.of(parts),
        null,
        null);
  }

  private static String text(A2aMessage message) {
    return message.parts().stream()
        .filter(p -> p instanceof A2aPart.Text)
        .map(p -> ((A2aPart.Text) p).text())
        .collect(Collectors.joining("\n"));
  }
}
