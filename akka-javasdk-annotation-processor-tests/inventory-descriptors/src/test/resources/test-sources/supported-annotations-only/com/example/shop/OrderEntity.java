package com.example.shop;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = Ids.ORDERS, name = "", description = "")
public class OrderEntity extends EventSourcedEntity<String, OrderEntity.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  public Effect<Done> create(String name) {
    return effects().persist(new Event.Created(name)).thenReply(__ -> Done.getInstance());
  }

  @Override
  public String applyEvent(Event event) {
    return "";
  }
}
