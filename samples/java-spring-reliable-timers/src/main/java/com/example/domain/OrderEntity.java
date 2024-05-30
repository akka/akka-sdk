package com.example.domain;

import kalix.javasdk.StatusCode.ErrorCode;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.valueentity.ValueEntityContext;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::order[]
@TypeId("order")
public class OrderEntity extends ValueEntity<Order> {

  private static final Logger logger = LoggerFactory.getLogger(OrderEntity.class);

  private final String entityId;

  public OrderEntity(ValueEntityContext context) {
    this.entityId = context.entityId();
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

  public Effect<String> confirm() {
    var orderId = commandContext().entityId();
    logger.info("Confirming orderId={}", orderId);
    if (currentState().placed()) { // <3>
      return effects()
          .updateState(currentState().confirm())
          .thenReply("Ok");
    } else {
      return effects().error(
          "No order found for '" + orderId + "'",
          ErrorCode.NOT_FOUND); // <4>
    }
  }

  public Effect<String> cancel() {
    var orderId = commandContext().entityId();
    logger.info("Cancelling orderId={} currentState={}", orderId, currentState());
    if (!currentState().placed()) {
      return effects().error(
          "No order found for " + orderId,
          ErrorCode.NOT_FOUND); // <5>
    } else if (currentState().confirmed()) {
      return effects().error(
          "Cannot cancel an already confirmed order",
          ErrorCode.BAD_REQUEST); // <6>
    } else {
      return effects().updateState(emptyState())
          .thenReply("Ok"); // <7>
    }
  }
  // end::order[]

  public Effect<OrderStatus> status() {
    var id = currentState().id();
    if (currentState().placed()) {
      var orderStatus = new OrderStatus(id, currentState().item(), currentState().quantity(), currentState().confirmed());
      return effects().reply(orderStatus);
    } else {
      return effects().error(
          "No order found for '" + id + "'",
          ErrorCode.NOT_FOUND);
    }
  }
// tag::order[]
}
// end::order[]
