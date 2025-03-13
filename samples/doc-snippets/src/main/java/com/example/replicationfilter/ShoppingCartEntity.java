/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */
package com.example.replicationfilter;

// tag::replication-filter[]
import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.EnableReplicationFilter;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.ReplicationFilter;

@ComponentId("shopping-cart")
@EnableReplicationFilter // <1>
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> {

  public Effect<Done> replicateTo(String region) {
    return effects()
      .updateReplicationFilter(ReplicationFilter.includeRegion(region)) // <2>
      .thenReply(__ -> Done.getInstance());
  }

  // end::replication-filter[]
  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return currentState();
  }
  // tag::replication-filter[]
}

// end::replication-filter[]

record ShoppingCart(String id) {}

interface ShoppingCartEvent {}
