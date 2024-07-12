package com.example.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.javasdk.keyvalueentity.KeyValueEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::order[]
@TypeId("order")
public class OrderEntity extends KeyValueEntity<Order> {

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

    public record Ok() implements Result {
      public static Ok instance = new Ok();

    }

    public record NotFound(String message) implements Result {
      public static NotFound of(String message) {
        return new NotFound(message);
      }
    }

    public record Invalid(String message) implements Result {
      public static Invalid of(String message) {
        return new Invalid(message);
      }
    }
  }

  @Override
  public Order emptyState() {
    return new Order(entityId, false, false, "", 0);
  }

  public Effect<Order> placeOrder(OrderRequest orderRequest) { // <1>
    var orderId = commandContext().entityId();
    logger.info("Placing orderId={} request={}", orderId, orderRequest);
    var newOrder = new Order(
      orderId,
      false,
      true, // <2>
      orderRequest.item(),
      orderRequest.quantity());
    return effects()
      .updateState(newOrder)
      .thenReply(newOrder);
  }

  public Effect<Result> confirm() {
    var orderId = commandContext().entityId();
    logger.info("Confirming orderId={}", orderId);
    if (currentState().placed()) { // <3>
      return effects()
        .updateState(currentState().confirm())
        .thenReply(Result.Ok.instance);
    } else {
      return effects().error(
        "No order found for '" + orderId + "'"); // <4>
    }
  }

  public Effect<Result> cancel() {
    var orderId = commandContext().entityId();
    logger.info("Cancelling orderId={} currentState={}", orderId, currentState());
    if (!currentState().placed()) {
      return effects().reply(Result.NotFound.of("No order found for " + orderId)); // <5>
    } else if (currentState().confirmed()) {
      return effects().reply(Result.Invalid.of("Cannot cancel an already confirmed order")); // <6>
    } else {
      return effects().updateState(emptyState())
        .thenReply(Result.Ok.instance); // <7>
    }
  }
  // end::order[]

  public Effect<OrderStatus> status() {
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
