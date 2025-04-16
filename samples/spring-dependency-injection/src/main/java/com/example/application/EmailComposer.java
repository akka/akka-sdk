package com.example.application;

import akka.javasdk.client.ComponentClient;
import com.example.domain.Counter;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionStage;

@Service
public class EmailComposer {

  private final ComponentClient componentClient;

  //injecting Akka dependency as any other dependency
  public EmailComposer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public String composeEmail(String counterId) {
    var currentValue = componentClient.forEventSourcedEntity(counterId)
      .method(Counter::get)
      .invoke();

    return "Counter [" + counterId + "] value is: " + currentValue;
  }
}
