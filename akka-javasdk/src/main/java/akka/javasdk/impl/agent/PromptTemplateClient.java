/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.client.ComponentClient;

/** INTERNAL API */
@InternalApi
public final class PromptTemplateClient {

  private final ComponentClient componentClient;

  public PromptTemplateClient(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public String getPromptTemplate(String templateId) {
    return componentClient.forEventSourcedEntity(templateId).method(PromptTemplate::get).invoke();
  }
}
