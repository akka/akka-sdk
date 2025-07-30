/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.hierarchy;

import akka.javasdk.workflow.Workflow;

public abstract class AbstractTextKvWorkflow extends Workflow<AbstractTextKvWorkflow.State> {

  public record State(String value) {}
}
