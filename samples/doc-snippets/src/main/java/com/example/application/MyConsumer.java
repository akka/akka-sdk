package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.Done.done;

@ComponentId("my-consumer")
public class MyConsumer extends Consumer {

  record Event() {
  }

  record SomeService() {
    public CompletionStage<Done> doSomething(Event event, String token) {
      return CompletableFuture.completedFuture(done());
    }
  }

  private SomeService someService;

  // tag::deterministic-hashing[]
  public Effect handle(Event event) {
    var entityId = messageContext().eventSubject().get();
    var sequenceNumber = messageContext().metadata().asCloudEvent().sequence().get();
    var token = UUID.nameUUIDFromBytes((entityId + sequenceNumber).getBytes()); // <1>
    return effects().asyncDone(
      someService.doSomething(event, token.toString()));
  }
  // end::deterministic-hashing[]
}
