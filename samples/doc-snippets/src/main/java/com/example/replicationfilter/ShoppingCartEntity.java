/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */
package com.example.replicationfilter;

// tag::replication-filter[]
import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.EnableReplicationFilter;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.ReplicationFilter;

@Component(id = "shopping-cart")
@EnableReplicationFilter // <1>
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> {

  // end::replication-filter[]
  // tag::replication-filter-event[]
  public Effect<Done> createCart(String userId) {
    var selfRegion = commandContext().selfRegion();
    return effects()
      .persist(new CartCreated(commandContext().entityId(), userId))
      .updateReplicationFilter(ReplicationFilter.includeRegion(selfRegion))
      .thenReply(__ -> Done.getInstance());
  }

  // end::replication-filter-event[]
  // tag::replication-filter[]

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

record CartCreated(String cartId, String userId) implements ShoppingCartEvent {}
