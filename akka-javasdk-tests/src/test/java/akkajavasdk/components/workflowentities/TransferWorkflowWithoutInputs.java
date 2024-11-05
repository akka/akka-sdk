/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akkajavasdk.components.actions.echo.Message;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

@ComponentId("transfer-workflow-without-inputs")
public class TransferWorkflowWithoutInputs extends Workflow<TransferState> {

  private final String withdrawStepName = "withdraw";
  private final String withdrawAsyncStepName = "withdraw-async";
  private final String depositStepName = "deposit";
  private final String depositAsyncStepName = "deposit-async";

  private ComponentClient componentClient;

  public TransferWorkflowWithoutInputs(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    var withdraw =
      step(withdrawStepName)
        .asyncCall(() -> {
          var transfer = currentState().transfer;
          return componentClient.forKeyValueEntity(transfer.from).method(WalletEntity::withdraw).invokeAsync(transfer.amount);
        })
        .andThen(String.class, response -> {
          var state = currentState().withLastStep("withdrawn").accepted();
          return effects()
            .updateState(state)
            .transitionTo(depositStepName);
        });

    var withdrawAsync =
      step(withdrawAsyncStepName)
        .asyncCall(() -> {
          var transfer = currentState().transfer;
          return componentClient.forKeyValueEntity(transfer.from).method(WalletEntity::withdraw).invokeAsync(transfer.amount);
        })
        .andThen(String.class, response -> {
          var state = currentState().withLastStep("withdrawn").accepted();
          return effects()
            .updateState(state)
            .transitionTo(depositAsyncStepName);
        });


    var deposit =
      step(depositStepName)
        .asyncCall(() -> {
          var transfer = currentState().transfer;
          return componentClient.forKeyValueEntity(transfer.to).method(WalletEntity::deposit).invokeAsync(transfer.amount);
        })
        .andThen(String.class, __ -> {
          var state = currentState().withLastStep("deposited").finished();
          return effects().updateState(state).end();
        });

    var depositAsync =
      step(depositAsyncStepName)
        .asyncCall(() -> {
          var transfer = currentState().transfer;
          return componentClient.forKeyValueEntity(transfer.to).method(WalletEntity::deposit).invokeAsync(transfer.amount);
        })
        .andThen(String.class, __ -> {
          var state = currentState().withLastStep("deposited").finished();
          return effects().updateState(state).end();
        });

    return workflow()
      .addStep(withdraw)
      .addStep(deposit)
      .addStep(withdrawAsync)
      .addStep(depositAsync);
  }

  public Effect<Message> startTransfer(Transfer transfer) {
    return start(transfer, withdrawStepName);
  }

  public Effect<Message> startTransferAsync(Transfer transfer) {
    return start(transfer, withdrawAsyncStepName);
  }

  private Effect<Message> start(Transfer transfer, String withdrawStepName) {
    if (transfer.amount <= 0.0) {
      return effects().error("Transfer amount should be greater than zero");
    } else {
      if (currentState() == null) {
        return effects()
          .updateState(new TransferState(transfer, "started"))
          .transitionTo(withdrawStepName)
          .thenReply(new Message("transfer started"));
      } else {
        return effects().reply(new Message("transfer started already"));
      }
    }
  }
}
