/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.client.ComponentClient;

public class PromptTemplateClient {

  private final ComponentClient componentClient;

  public PromptTemplateClient(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public String getPromptTemplate(String templateId) {
    return componentClient.forEventSourcedEntity(templateId)
        .method(PromptTemplate::get)
        .invoke();
  }
}
