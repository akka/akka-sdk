package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;

import java.util.UUID;

@ComponentId("my-consumer")
public class MyConsumer extends Consumer {

  record Event() {
  }

  record SomeService() {
    public void doSomething(Event event, String token) {
    }
  }

  private SomeService someService;

  // tag::deterministic-hashing[]
  public Effect handle(Event event) {
    var entityId = messageContext().eventSubject().get();
    var sequenceNumber = messageContext().metadata().asCloudEvent().sequence().get();
    var token = UUID.nameUUIDFromBytes((entityId + sequenceNumber).getBytes()); // <1>
    someService.doSomething(event, token.toString());
    return effects().done();
  }
  // end::deterministic-hashing[]
}
