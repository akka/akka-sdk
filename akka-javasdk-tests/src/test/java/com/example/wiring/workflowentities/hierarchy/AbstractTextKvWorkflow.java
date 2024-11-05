/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities.hierarchy;

import akka.javasdk.workflow.Workflow;

public abstract class AbstractTextKvWorkflow extends Workflow<AbstractTextKvWorkflow.State> {

  public record State(String value) {}


}