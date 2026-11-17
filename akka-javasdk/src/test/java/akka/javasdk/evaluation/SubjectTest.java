/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class SubjectTest {

  @Test
  public void interactionAccessorsOnTheSubjectReadTheInteraction() {
    Subject subject = new Subject.Interaction("interaction-1", "my-agent", Optional.empty());

    assertEquals("interaction-1", subject.interactionId());
    assertEquals("my-agent", subject.agentComponentId());
  }

  @Test
  public void interactionAccessorsFailForAWorkflowRun() {
    Subject subject = new Subject.Workflow("workflow-1", "order-workflow");

    assertThrows(IllegalStateException.class, subject::interactionId);
    assertThrows(IllegalStateException.class, subject::agentComponentId);
  }
}
