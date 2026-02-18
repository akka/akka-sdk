/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/** Simple worker agent for testing delegation from SimpleOrchestratorAgent. */
@Component(id = "simple-worker")
public class SimpleWorkerAgent extends Agent {

  public Effect<String> doWork(String task) {
    return effects().systemMessage("You are a helpful worker agent.").userMessage(task).thenReply();
  }
}
