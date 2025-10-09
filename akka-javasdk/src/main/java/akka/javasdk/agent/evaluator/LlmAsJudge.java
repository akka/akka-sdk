/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.evaluator;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.client.ComponentClient;
import com.typesafe.config.Config;

/**
 * Base class for LLM as judge evaluator agents. It is intentionally not public because it's just
 * some convenience to keep the concrete implementations DRY.
 */
abstract class LlmAsJudge extends Agent {

  protected final String componentId;
  protected final ComponentClient componentClient;
  protected final Config config;

  LlmAsJudge(String componentId, ComponentClient componentClient, Config config) {
    this.componentId = componentId;
    this.componentClient = componentClient;
    this.config = config;
  }

  protected ModelProvider modelProvider() {
    return ModelProvider.fromConfig(
        config.getString("akka.javasdk.agent.evaluators." + componentId + ".model-provider"));
  }

  protected String prompt(String promptId, String defaultPrompt) {
    return componentClient
        .forEventSourcedEntity(promptId)
        .method(PromptTemplate::getOptional)
        .invoke()
        .orElse(defaultPrompt);
  }
}
