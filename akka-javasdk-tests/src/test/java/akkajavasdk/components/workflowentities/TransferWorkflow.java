/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

@ComponentId("transfer-workflow")
public class TransferWorkflow extends Workflow<TransferState> {

  private final String withdrawStepName = "withdraw";
  private final String depositStepName = "deposit";

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    var withdraw =
        step(withdrawStepName)
          .call(Withdraw.class, cmd -> componentClient.forKeyValueEntity(cmd.from).method(WalletEntity::withdraw).invoke(cmd.amount))
          .andThen(() -> {
              var state = currentState().withLastStep("withdrawn").asAccepted();

              var depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());

              return effects()
                  .updateState(state)
                  .transitionTo(depositStepName, depositInput);
            });

    var deposit =
        step(depositStepName)
          .call(Deposit.class, cmd -> componentClient.forKeyValueEntity(cmd.to).method(WalletEntity::deposit).invoke(cmd.amount))
          .andThen(() -> {
              var state = currentState().withLastStep("deposited").asFinished();
              return effects().updateState(state).end();
            });

    // this last step is mainly to ensure that Runnables are properly supported
    var logAndStop =
        step("logAndStop")
            .call(() -> logger.info("Workflow finished"))
            .andThen(() ->
              effects()
                .updateState(currentState().withLastStep("logAndStop"))
                .end());

    return workflow()
        .timeout(Duration.ofSeconds(10))
        .addStep(withdraw)
        .addStep(deposit)
        .addStep(logAndStop);
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

  public Effect<Boolean> commandHandlerIsOnVirtualThread() {
    return effects().reply(Thread.currentThread().isVirtual());
  }

  public Effect<Message> genericStringsCall(List<String> primitives) {
    return effects().reply(new Message("genericCall ok"));
  }

  public record SomeClass(String someValue) {}

  public Effect<Message> genericCall(List<SomeClass> objects) {
    return effects().reply(new Message("genericCall ok"));
  }
}
