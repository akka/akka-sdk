package com.example.application;

import akka.javasdk.client.ComponentClient;
import com.example.domain.Counter;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionStage;

@Service
public class EmailComposer {

  private final ComponentClient componentClient;

  //injecting Akka Platform dependency as any other dependency
  public EmailComposer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public CompletionStage<String> composeEmail(String counterId) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(Counter::get)
      .invokeAsync()
      .thenApply(currentValue -> "Counter [" + counterId + "] value is: " + currentValue);
  }
}
