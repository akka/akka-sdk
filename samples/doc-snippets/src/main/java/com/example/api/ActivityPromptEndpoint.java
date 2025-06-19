package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

// tag::prompt-template-endpoint[]
@HttpEndpoint("/activity-prompts")
public class ActivityPromptEndpoint {

  private final ComponentClient componentClient;

  public ActivityPromptEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Put
  public HttpResponse update(String prompt) {
    componentClient
      .forEventSourcedEntity("activity-agent-prompt") // <1>
      .method(PromptTemplate::update) // <2>
      .invoke(prompt);

    return HttpResponses.ok();
  }

  @Get
  public String get() {
    return componentClient
      .forEventSourcedEntity("activity-agent-prompt") // <1>
      .method(PromptTemplate::get) // <3>
      .invoke();
  }
}
// end::prompt-template-endpoint[]
