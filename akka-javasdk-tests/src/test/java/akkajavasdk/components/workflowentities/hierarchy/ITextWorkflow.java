/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.hierarchy;

import akka.javasdk.workflow.Workflow;

public interface ITextWorkflow {

  Workflow.StepEffect dummyStepInInterface(String text);
}
