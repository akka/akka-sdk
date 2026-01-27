/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.protobuf;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.protocol.SerializationTestProtos.OrderItem;
import akkajavasdk.protocol.SerializationTestProtos.OrderStatus;
import akkajavasdk.protocol.SerializationTestProtos.OrderWorkflowState;
import akkajavasdk.protocol.SerializationTestProtos.ProcessPaymentInput;
import akkajavasdk.protocol.SerializationTestProtos.ShipOrderInput;
import akkajavasdk.protocol.SerializationTestProtos.ValidateOrderInput;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A workflow that uses protobuf messages for state and step inputs. This demonstrates that protobuf
 * serialization works correctly with workflows.
 */
@Component(id = "protobuf-order-workflow")
public class ProtobufOrderWorkflow extends Workflow<OrderWorkflowState> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  // Command records (using Java records as commands)
  public record CreateOrderCommand(String customerId, List<OrderItemDto> items) {}

  public record OrderItemDto(String productId, String productName, int quantity, double price) {}

  public record CancelOrderCommand() {}

  // Command handlers

  public Effect<String> createOrder(CreateOrderCommand command) {
    if (currentState() != null && currentState().getStatus() != OrderStatus.ORDER_STATUS_UNKNOWN) {
      return effects().error("Order already exists");
    }

    // Convert DTOs to protobuf OrderItems
    var itemsBuilder = OrderWorkflowState.newBuilder();
    double total = 0;
    for (var dto : command.items()) {
      var item =
          OrderItem.newBuilder()
              .setProductId(dto.productId())
              .setProductName(dto.productName())
              .setQuantity(dto.quantity())
              .setPrice(dto.price())
              .build();
      itemsBuilder.addItems(item);
      total += dto.quantity() * dto.price();
    }

    var orderId = commandContext().workflowId();
    var newState =
        itemsBuilder
            .setOrderId(orderId)
            .setCustomerId(command.customerId())
            .setTotalAmount(total)
            .setStatus(OrderStatus.ORDER_STATUS_PENDING)
            .setLastStep("created")
            .build();

    // Create protobuf step input
    var validateInput =
        ValidateOrderInput.newBuilder()
            .setOrderId(orderId)
            .setCustomerId(command.customerId())
            .addAllItems(newState.getItemsList())
            .build();

    return effects()
        .updateState(newState)
        .transitionTo(ProtobufOrderWorkflow::validateOrder)
        .withInput(validateInput)
        .thenReply("Order created");
  }

  @StepName("validate-order")
  private StepEffect validateOrder(ValidateOrderInput input) {
    logger.info("Validating order {} for customer {}", input.getOrderId(), input.getCustomerId());

    // Simulate validation
    if (input.getItemsCount() == 0) {
      var state =
          currentState().toBuilder()
              .setStatus(OrderStatus.ORDER_STATUS_CANCELLED)
              .setLastStep("validate-order")
              .setErrorMessage("Order has no items")
              .build();
      return stepEffects().updateState(state).thenEnd();
    }

    var state =
        currentState().toBuilder()
            .setStatus(OrderStatus.ORDER_STATUS_VALIDATED)
            .setLastStep("validate-order")
            .build();

    // Create protobuf step input for payment
    var paymentInput =
        ProcessPaymentInput.newBuilder()
            .setOrderId(input.getOrderId())
            .setCustomerId(input.getCustomerId())
            .setAmount(currentState().getTotalAmount())
            .build();

    return stepEffects()
        .updateState(state)
        .thenTransitionTo(ProtobufOrderWorkflow::processPayment)
        .withInput(paymentInput);
  }

  @StepName("process-payment")
  private StepEffect processPayment(ProcessPaymentInput input) {
    logger.info("Processing payment of {} for order {}", input.getAmount(), input.getOrderId());

    // Simulate payment processing
    var state =
        currentState().toBuilder()
            .setStatus(OrderStatus.ORDER_STATUS_PROCESSING)
            .setLastStep("process-payment")
            .build();

    // Create protobuf step input for shipping
    var shipInput =
        ShipOrderInput.newBuilder()
            .setOrderId(input.getOrderId())
            .setCustomerId(input.getCustomerId())
            .setShippingAddress("123 Main St, City, Country")
            .build();

    return stepEffects()
        .updateState(state)
        .thenTransitionTo(ProtobufOrderWorkflow::shipOrder)
        .withInput(shipInput);
  }

  @StepName("ship-order")
  private StepEffect shipOrder(ShipOrderInput input) {
    logger.info("Shipping order {} to {}", input.getOrderId(), input.getShippingAddress());

    var state =
        currentState().toBuilder()
            .setStatus(OrderStatus.ORDER_STATUS_SHIPPED)
            .setLastStep("ship-order")
            .build();

    return stepEffects().updateState(state).thenTransitionTo(ProtobufOrderWorkflow::completeOrder);
  }

  private StepEffect completeOrder() {
    logger.info("Completing order {}", currentState().getOrderId());

    var state =
        currentState().toBuilder()
            .setStatus(OrderStatus.ORDER_STATUS_COMPLETED)
            .setLastStep("complete-order")
            .build();

    return stepEffects().updateState(state).thenEnd();
  }

  public Effect<String> cancelOrder(CancelOrderCommand command) {
    if (currentState() == null || currentState().getStatus() == OrderStatus.ORDER_STATUS_UNKNOWN) {
      return effects().error("Order does not exist");
    }
    if (currentState().getStatus() == OrderStatus.ORDER_STATUS_SHIPPED
        || currentState().getStatus() == OrderStatus.ORDER_STATUS_COMPLETED) {
      return effects().error("Cannot cancel order that has been shipped or completed");
    }

    var state =
        currentState().toBuilder()
            .setStatus(OrderStatus.ORDER_STATUS_CANCELLED)
            .setLastStep("cancelled")
            .build();

    return effects().updateState(state).end().thenReply("Order cancelled");
  }

  public ReadOnlyEffect<OrderWorkflowState> getOrder() {
    if (currentState() == null || currentState().getStatus() == OrderStatus.ORDER_STATUS_UNKNOWN) {
      return effects().error("Order does not exist");
    }
    return effects().reply(currentState());
  }

  public ReadOnlyEffect<String> getStatus() {
    if (currentState() == null || currentState().getStatus() == OrderStatus.ORDER_STATUS_UNKNOWN) {
      return effects().error("Order does not exist");
    }
    return effects().reply(currentState().getStatus().name());
  }

  public ReadOnlyEffect<String> getLastStep() {
    if (currentState() == null) {
      return effects().reply("none");
    }
    return effects().reply(currentState().getLastStep());
  }
}
