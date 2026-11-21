/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.workflow.Workflow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class A2aTaskWorkflowsTest {

  static class PublishedWorkflow extends Workflow<String> {}

  static class UnpublishedWorkflow extends Workflow<String> {}

  private static final A2aAgents ENDPOINT_PUBLISHES =
      A2aAgents.create().taskWorkflow(PublishedWorkflow.class);

  @Test
  void acceptsAWorkflowThatTheEndpointPublishes() {
    assertDoesNotThrow(
        () ->
            A2aTaskWorkflows.requirePublished(
                List.of(PublishedWorkflow.class), Optional.of(ENDPOINT_PUBLISHES)));
  }

  @Test
  void acceptsServicesWithoutAContextUser() {
    assertDoesNotThrow(() -> A2aTaskWorkflows.requirePublished(List.of(), Optional.empty()));
  }

  @Test
  void refusesAWorkflowThatTheEndpointDoesNotPublish() {
    var error =
        assertThrows(
            IllegalStateException.class,
            () ->
                A2aTaskWorkflows.requirePublished(
                    List.of(UnpublishedWorkflow.class), Optional.of(ENDPOINT_PUBLISHES)));
    assertTrue(
        error.getMessage().contains(UnpublishedWorkflow.class.getName()), error.getMessage());
    assertTrue(error.getMessage().contains("taskWorkflow"), error.getMessage());
  }

  @Test
  void refusesAWorkflowWhenTheServiceHasNoEndpoint() {
    var error =
        assertThrows(
            IllegalStateException.class,
            () ->
                A2aTaskWorkflows.requirePublished(
                    List.of(PublishedWorkflow.class), Optional.empty()));
    assertTrue(error.getMessage().contains("no @A2aEndpoint"), error.getMessage());
  }
}
