package com.example.multiregion;

// tag::consumer[]
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "multi-region-order-index")
@Consume.FromEventSourcedEntity(OrderEntity.class)
public class RegionalOrderIndex extends Consumer {

  private final RegionalIndex index;

  public RegionalOrderIndex(RegionalIndex index) {
    this.index = index;
  }

  public Effect onOrderPlaced(OrderPlaced event) {
    return effects().asyncDone(index.upsert(messageContext().selfRegion(), event)); // <1>
  }
}
// end::consumer[]
