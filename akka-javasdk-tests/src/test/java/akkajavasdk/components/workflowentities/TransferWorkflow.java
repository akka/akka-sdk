/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akkajavasdk.components.actions.echo.Message;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

import java.time.Duration;
import java.util.List;

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
              var state = currentState().withLastStep("withdrawn").asAccepted();

              var depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());

              return effects()
                  .updateState(state)
                  .transitionTo(depositStepName, depositInput);
            });

    var deposit =
        step(depositStepName)
            .asyncCall(Deposit.class, cmd -> componentClient.forKeyValueEntity(cmd.to).method(WalletEntity::deposit).invokeAsync(cmd.amount)
            ).andThen(String.class, __ -> {
              var state = currentState().withLastStep("deposited").asFinished();
              return effects().updateState(state).end();
            });

    return workflow()
        .timeout(Duration.ofSeconds(10))
        .addStep(withdraw)
        .addStep(deposit);
  }

  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount() <= 0.0) {
      return effects().reply(new Message("Transfer amount should be greater than zero"));
    } else {
      if (currentState() == null) {
        return effects()
            .updateState(new TransferState(transfer, "started"))
            .transitionTo(withdrawStepName, new Withdraw(transfer.from(), transfer.amount()))
            .thenReply(new Message("transfer started"));
      } else {
        return effects().reply(new Message("transfer started already"));
      }
    }
  }

  public Effect<Message> genericStringsCall(List<String> primitives) {
    return effects().reply(new Message("genericCall ok"));
  }

  public record SomeClass(String someValue) {}

  public Effect<Message> genericCall(List<SomeClass> objects) {
    return effects().reply(new Message("genericCall ok"));
  }
}
