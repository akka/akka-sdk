package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.api.OrderRequest;
import com.example.domain.Order;
import com.example.domain.OrderStatus;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.application.OrderEntity.Result.Ok.ok;



// tag::order[]
@ComponentId("order")
public class OrderEntity extends KeyValueEntity<Order> {
   //...
  // end::order[]
  private static final Logger logger = LoggerFactory.getLogger(OrderEntity.class);

  private final String entityId;

  public OrderEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Result.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = Result.NotFound.class, name = "notFound"),
    @JsonSubTypes.Type(value = Result.Invalid.class, name = "invalid")
  })
  
  public sealed interface Result {

    record Ok() implements Result {
      public static Ok ok = new Ok();
    }

    record NotFound(String message) implements Result {
      public static NotFound of(String message) {
        return new NotFound(message);
      }
    }

    record Invalid(String message) implements Result {
      public static Invalid of(String message) {
        return new Invalid(message);
      }
    }
  }

  @Override
  public Order emptyState() {
    return new Order(entityId, false, false, "", 0);
  }

  // tag::place-order[]
  public Effect<Order> placeOrder(OrderRequest orderRequest) { // <1>
    var orderId = commandContext().entityId();
    // end::place-order[]
    logger.info("Placing orderId={} request={}", orderId, orderRequest);
    // tag::place-order[]
    boolean placed = true;
    boolean confirmed = false;
    var newOrder = new Order(
      orderId,
      confirmed,
      placed, // <2>
      orderRequest.item(),
      orderRequest.quantity());

    return effects()
      .updateState(newOrder)
      .thenReply(newOrder);
  }
  // end::place-order[]

  public Effect<Result> confirm() {
    var orderId = commandContext().entityId();
    
    logger.info("Confirming orderId={}", orderId);
    
    if (currentState().placed()) { // <3>
      return effects()
        .updateState(currentState().confirm())
        .thenReply(ok);
    } else {
      return effects().reply(Result.NotFound.of("No order found for " + orderId)); // <4>
    }
  }


  // tag::cancel-order[]
  public Effect<Result> cancel() {
    var orderId = commandContext().entityId();
    // end::cancel-order[]
    logger.info("Cancelling orderId={} currentState={}", orderId, currentState());

    // tag::cancel-order[]
    if (!currentState().placed()) {
      return effects().reply(Result.NotFound.of("No order found for " + orderId)); // <1>
    } else if (currentState().confirmed()) {
      return effects().reply(Result.Invalid.of("Cannot cancel an already confirmed order")); // <2>
    } else {
      return effects().updateState(emptyState()).thenReply(ok); // <3>
    }
  }
  // end::cancel-order[]

  public ReadOnlyEffect<OrderStatus> status() {
    var id = currentState().id();
    if (currentState().placed()) {
      var orderStatus = new OrderStatus(id, currentState().item(), currentState().quantity(), currentState().confirmed());
      return effects().reply(orderStatus);
    } else {
      return effects().error("No order found for '" + id + "'");
    }
  }

// tag::order[]
}
// end::order[]