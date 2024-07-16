/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.actions.echo.Message;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.workflow.Workflow;

import java.time.Duration;

@ComponentId("transfer-workflow")
public class TransferWorkflow extends Workflow<TransferState> {

  private final String withdrawStepName = "withdraw";
  private final String depositStepName = "deposit";

  private ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    var withdraw =
        step(withdrawStepName)
            .asyncCall(Withdraw.class, cmd -> componentClient.forKeyValueEntity(cmd.from).method(WalletEntity::withdraw).invokeAsync(cmd.amount))
            .andThen(String.class, __ -> {
              var state = currentState().withLastStep("withdrawn").accepted();

              var depositInput = new Deposit(currentState().transfer.to, currentState().transfer.amount);

              return effects()
                  .updateState(state)
                  .transitionTo(depositStepName, depositInput);
            });

    var deposit =
        step(depositStepName)
            .asyncCall(Deposit.class, cmd -> componentClient.forKeyValueEntity(cmd.to).method(WalletEntity::deposit).invokeAsync(cmd.amount)
            ).andThen(String.class, __ -> {
              var state = currentState().withLastStep("deposited").finished();
              return effects().updateState(state).end();
            });

    return workflow()
        .timeout(Duration.ofSeconds(10))
        .addStep(withdraw)
        .addStep(deposit);
  }

  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount <= 0.0) {
      return effects().reply(new Message("Transfer amount should be greater than zero"));
    } else {
      if (currentState() == null) {
        return effects()
            .updateState(new TransferState(transfer, "started"))
            .transitionTo(withdrawStepName, new Withdraw(transfer.from, transfer.amount))
            .thenReply(new Message("transfer started"));
      } else {
        return effects().reply(new Message("transfer started already"));
      }
    }
  }
}
